# ThruSpark Security

This document describes ThruSpark's threat model, attack surface, audit findings, and the mitigations in place. It is written to give security-conscious users, reviewers, and potential contributors / investors a clear honest picture of what the app does and doesn't protect against.

Last reviewed: 2026-04-28 against the v0.5 codebase.

If you discover a vulnerability, please email `security@thebikemechanic.ca` rather than opening a public issue. We respond within 7 business days for high-severity reports, 30 days otherwise.

---

## 1. Executive summary

ThruSpark is a battery-saver utility for Android that uses Shizuku (a separate, free, open-source companion app) to apply elevated power-management actions: airplane mode, refresh-rate caps, app pausing via the OS-level `cmd package suspend` mechanism. The app stores per-profile settings, optional standalone alarms, and an optional Supabase-backed account on the user's device.

The biggest security-relevant decisions:

- **No data collected by us.** Email + profiles stay on-device unless you explicitly sign in (and even then only the email is sent). No analytics, no telemetry. Verifiable in-app via Settings → Network activity (the audit log).
- **No persistent auth tokens.** Supabase JWTs are not stored locally. The signed-in state is just an email string; protected operations (account deletion) re-prompt for password to obtain a fresh token.
- **Shizuku is the trust anchor.** Tier 2 capabilities depend on the user manually granting Shizuku, which is an open-source project we don't control. ThruSpark uses Shizuku for a small, well-defined set of shell commands documented in `app/src/main/java/.../capability/AppPauser.kt`.
- **Pausing is reversible.** Every package we suspend or restrict is tracked in `AppRestrictionsStore`; deactivating the profile reverses everything. An emergency "Unpause everything" button exists in Settings as a last-resort recovery.
- **System-critical packages are always exempt.** A hard-coded list (`util/SystemExemptions.kt`) plus runtime detection (current launcher / IME / dialer / accessibility services) prevents the user from breaking their phone by forgetting to add critical packages to a profile's allowlist.

No issues found at Critical severity. Three findings at High severity are documented below — two are accuracy issues in user-facing copy that depend on user decisions; one is a defense-in-depth opportunity (encrypted-at-rest local storage) we plan to address in v0.6.

---

## 2. Threat model

We consider three attacker categories.

**Local attacker with physical access to the unlocked device.** Out of scope. Any app on an unlocked phone can be inspected, modified, or replaced. ThruSpark is not designed to defend against this — Android itself doesn't.

**Local attacker with physical access to the locked device.** Mitigated by Android's full-disk encryption (FDE / file-based encryption since Android 10). All ThruSpark data lives in the app's private storage, which is encrypted at rest as part of the OS's default FDE — readable only after the user unlocks. ThruSpark adds no app-layer encryption on top (see Finding H-3 below for the tradeoff).

**Remote attacker on the network.** Two relevant scenarios:
- *Passive observer (eavesdropping)*: HTTPS is enforced for all Supabase calls (`baseUrl.startsWith("https://")` check at `auth/SupabaseAuth.kt:35`). OkHttp's default certificate validation applies; no pinning (see Finding M-2).
- *Active man-in-the-middle*: Without certificate pinning, a successful TLS-MITM (e.g. malicious root CA, compromised network) could intercept sign-in credentials. This is mitigated by user behavior (don't sign in on networks you don't trust) more than by the app.

**Compromised companion app (Shizuku).** Shizuku is a separate process the user installs themselves. We trust Shizuku's signature check during binder pairing — Shizuku verifies its own signature, so a tampered-with Shizuku build would fail to bind. We do not implement an additional trust check on our side.

**Malicious or curious app on the same device.** Other apps cannot read ThruSpark's private storage (Android sandbox enforces this). They cannot bind to our `BIND_QUICK_SETTINGS_TILE` or `BIND_NOTIFICATION_LISTENER_SERVICE` services because those permissions are signature-level (system-only). They cannot intercept our pending intents because we use `FLAG_IMMUTABLE` on every PendingIntent we create.

**The ThruSpark developers.** Listed for completeness. The user must trust that the Play Store / GitHub binary they install matches the source code they can audit. Verification path: open-source publication + reproducible builds. Open-source decision is pending (see `project_thruspark_v05_open_source.md` memory note); reproducible builds are not currently a goal but feasible.

---

## 3. Attack surface

### 3.1 Network

- Outbound HTTPS to the configured Supabase project URL (`local.properties` → `BuildConfig.SUPABASE_URL`)
- Outbound HTTPS to YouTube when the user taps the setup video link
- Outbound HTTPS to Play Store / GitHub when opening Shizuku install links (delegated to system browser, not the app)
- No inbound network. ThruSpark exposes no listening sockets.

### 3.2 Inter-process communication (IPC)

- **Quick Settings tile** (`tile/ThruSparkTileService`): exposed=true, but `permission="android.permission.BIND_QUICK_SETTINGS_TILE"` (signature-level — only system can bind).
- **Notification listener** (`notification/ThruSparkNotificationListener`): exposed=true, `permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` (signature-level — only system can bind via user grant).
- **Boot receiver** (`receiver/BootReceiver`): exposed=true, intent-filtered to `ACTION_BOOT_COMPLETED` only. Defensive `intent.action != Intent.ACTION_BOOT_COMPLETED` check despite the filter.
- **Alarm receiver, alarm activity, alarm service** (`alarm/*`): all exported=false (internal only).
- **Profile lifecycle service** (`session/ProfileLifecycleService`): exported=false, foregroundServiceType=specialUse.
- **Shizuku provider** (`rikka.shizuku.ShizukuProvider`): exported=true, `permission="INTERACT_ACROSS_USERS_FULL"` (system-only). Required by the Shizuku SDK.
- **Main activity** (`ui/MainActivity`): exported=true (must be, to launch from launcher), `MAIN`/`LAUNCHER` intent filter only.

### 3.3 Local storage

- DataStore for profile state, user prefs, alarm entries, network activity log, app restrictions (see `data/*Store.kt`).
- App-private file storage for custom profile JSON (`data/CustomProfileStore.kt` — files in `${context.filesDir}/custom_profiles/`).
- No external storage usage.
- No SQLite database.
- No SharedPreferences except a single legacy `thruspark_prefs` for the onboarding-done flag.

### 3.4 Permissions held

Full plain-English explanation in-app at Settings → Privacy & transparency → Permissions used. Cross-references `AndroidManifest.xml`. The most sensitive permission is `BIND_NOTIFICATION_LISTENER_SERVICE`, justified for in-app notification filtering with the explicit guarantee that notification content is never persisted or transmitted off-device.

### 3.5 Shizuku shell command surface

`capability/AppPauser.kt` and `capability/ShizukuCapabilityProvider.kt` are the only places that build shell commands sent through Shizuku. The commands are templated with package names obtained from `PackageManager.queryIntentActivities()` — Android's package-name validation restricts these to a safe charset (`[a-zA-Z0-9._]`), so command injection via package name is not possible.

The exact command set is auditable in those two files. Examples:
- `am force-stop <pkg>`
- `cmd appops set <pkg> RUN_IN_BACKGROUND deny`
- `cmd package suspend --dialogMessage '...' <pkg>`
- `settings put global airplane_mode_on 1`
- `svc wifi disable`

---

## 4. Findings

Severity scale: **Critical** (immediate fix), **High** (fix in next release), **Medium** (fix when convenient, document for now), **Low** (acceptable tradeoff, document), **Informational** (no action needed, noted for transparency).

### Critical

None.

### High

**H-1. WalkthroughScreen slide 2 claims ThruSpark is open-source; this is currently false.**
File: `ui/WalkthroughScreen.kt`, slide 2 body.
The text reads: "Both ThruSpark and Shizuku are open-source and available on the Google Play Store." Shizuku is open-source. ThruSpark is not (yet) — the source is in a private GitHub folder and has not been published. Telling users a verifiable lie about the app's licensing is a meaningful trust violation.

*Fix*: either publish the source (decision pending — see `project_thruspark_v05_open_source.md`) and the claim becomes true, or change the slide to say "ThruSpark is closed-source; Shizuku is open-source" and accept the smaller trust surface. **Resolve before any store submission.**

**H-2. WalkthroughScreen slide 3 claims data is "encrypted on your device only."**
File: `ui/WalkthroughScreen.kt`, slide 3 body.
Technically correct (Android FDE encrypts all app private storage at rest), but oversells what ThruSpark itself does. The app adds no app-layer encryption on top of FDE; on a rooted device or a forensic extraction with the device unlocked, custom profile contents and the signed-in email are recoverable.

*Fix*: either soften the wording to "stored privately on your device" (accurate, less impressive), or actually add app-layer encryption for the few sensitive fields (`UserPrefsStore.signedInEmail`) using `EncryptedSharedPreferences` from `androidx.security.crypto`. The latter is ~50 lines and worth doing before launch.

**H-3. Local storage relies entirely on Android FDE; no app-layer encryption.**
Files: `data/UserPrefsStore.kt`, `data/CustomProfileStore.kt`, `data/AlarmEntryStore.kt`, `data/NetworkActivityStore.kt`, `data/ProfileStateStore.kt`, `data/AppRestrictionsStore.kt`.
Companion to H-2. While Android FDE is sound for most threat models, defense-in-depth via `EncryptedSharedPreferences` for the signed-in email (the only PII we hold) would meaningfully reduce attack surface against forensic extraction.

*Fix*: migrate `UserPrefsStore.KEY_SIGNED_IN_EMAIL` to EncryptedSharedPreferences in v0.6. Custom profiles, alarms, and the activity log can stay in DataStore — they're not PII.

### Medium

**M-1. Shizuku.newProcess() called via reflection.**
File: `shizuku/ShizukuManager.kt:91-100`.
Shizuku 13 marked `newProcess()` as `@RestrictTo(LIBRARY_GROUP)`; we bypass this with reflection. The official path is to bind a custom `UserService` and route shell calls through it. Reflection is fragile — a future Shizuku version may rename, sign-restrict, or remove the method. Failure mode is exceptions at runtime, surfaced via the `ShellResult.Error` return type and caller-side failure messaging (acceptable, not silent).

*Fix*: implement the proper UserService binding pattern in v0.6. ~150 lines including the AIDL interface.

**M-2. No certificate pinning on Supabase calls.**
File: `auth/SupabaseAuth.kt`.
OkHttp uses the system trust store. A successful TLS MITM (compromised CA, malicious root certificate the user has installed) could intercept sign-in credentials. Pinning would prevent this but at high maintenance cost (Supabase / Cloudflare certificates rotate; a stale pin bricks the app).

*Fix*: accept the tradeoff. Pin only if a concrete threat model emerges. Document in user-facing privacy info that we use standard system trust.

**M-3. Device admin packages are not auto-exempted from app pausing.**
File: `util/SystemExemptions.kt:50` (commented "Device admins are NOT included here").
If the user has a device-admin app installed (corporate MDM, enterprise security app, antivirus with admin role) and forgets to add it to a profile's allowlist, our pause flow will suspend it — potentially breaking the device's compliance posture or triggering a security alert from the admin app.

*Fix*: enumerate active admins via Shizuku (`dpm list-active-admins`) at apply time, add them to the runtime exemption set in v0.6.

**M-4. Password held as a plaintext String during account deletion confirmation.**
File: `ui/SettingsScreen.kt`, `DeleteAccountDialog`.
Compose's `mutableStateOf("")` for the password field stores the value as a `String`, which lives on the JVM heap and could appear in heap dumps or memory inspections. Best practice is `CharArray` zeroed after use, but Compose's `OutlinedTextField` doesn't natively support `CharArray`.

*Fix*: low priority. Real attack would require local code execution, at which point the keyboard input is also trivially capturable. Defer indefinitely unless a concrete threat emerges.

### Low

**L-1. Tile UI refresh delay (300ms) may show stale state during long activations.**
File: `tile/ThruSparkTileService.kt:36`.
A profile activation involving Shizuku app-pause can take 1–3s. The tile refreshes its on/off badge after 300ms, before activation completes. Cosmetic only; ProfileStateStore is updated correctly.

*Fix*: wire the tile to observe `ProfileStateStore.isActiveFlow` instead of polling. ~20 lines. Deferred per user preference (asked, declined).

**L-2. ProfileLifecycleService deactivate timeout (8s) — apps may stay paused on Shizuku binder hang.**
File: `session/ProfileLifecycleService.kt:102`.
If Shizuku is uninstalled or its binder dies between profile activation and the user swiping the app away, deactivation may not complete within the timeout window; suspended packages stay suspended.

*Mitigation in place*: Settings → Background pausing → "Unpause everything now" runs the full restore independently. Documented in-app.

**L-3. Edge Function endpoint hardcoded to `/functions/v1/delete-account`.**
File: `auth/SupabaseAuth.kt:54`.
If the user names their Edge Function differently, account deletion fails with a clear error. Documented in `Compliance-Handoff.md`.

*Fix*: make the function name a `BuildConfig` field if multi-environment support becomes a requirement. Not worth doing for v0.5.

### Informational

**I-1. PendingIntents use `FLAG_IMMUTABLE` consistently.**
Files: `alarm/AlarmScheduler.kt`, `session/ProfileLifecycleService.kt`. Required by Android 12+; protects against intent hijacking.

**I-2. BootReceiver explicitly verifies `intent.action`.**
File: `receiver/BootReceiver.kt:23`. Defensive even though the manifest's intent-filter already restricts to `ACTION_BOOT_COMPLETED`.

**I-3. No `QUERY_ALL_PACKAGES` permission requested.**
File: `AndroidManifest.xml`. Uses the `<queries>` block with a launcher-intent filter to enumerate user-launchable apps only. Avoids the Play Store policy review for `QUERY_ALL_PACKAGES`.

**I-4. URL parameters stripped before logging.**
File: `auth/NetworkActivityLogger.kt:32`. The activity log shows host + path only, never query strings — defensive against future code that might put credentials in URL params.

**I-5. NotificationListenerService never persists or transmits notification content.**
File: `notification/ThruSparkNotificationListener.kt`. The override only consults the in-memory `NotificationFilterState` allowlist and either calls `cancelNotification(sbn.key)` or no-ops. No write to disk, no network call.

**I-6. Sensitive-action re-auth pattern for account deletion.**
File: `auth/AuthRepository.kt`, `ui/SettingsScreen.kt` (DeleteAccountDialog). User must enter their password before deletion; matches Google / Apple / GitHub patterns.

**I-7. Shell command construction safety.**
Package names used in shell commands are obtained from `PackageManager` and validated by Android to `[a-zA-Z0-9._]`. No user-controlled string ever reaches a shell context.

---

## 5. In-place mitigations summary

| Layer | Mitigation | File reference |
|---|---|---|
| Network | HTTPS-only enforcement | `auth/SupabaseAuth.kt:35` |
| Network | URL params stripped from logs | `auth/NetworkActivityLogger.kt:32` |
| Network | User-auditable activity log | `ui/NetworkActivityScreen.kt` |
| Auth | No persistent JWT | `auth/AuthRepository.kt` (sign-in stores email only) |
| Auth | Sensitive-action re-auth | `auth/AuthRepository.kt:48` |
| IPC | Signature-permission gating on tile + notification listener | `AndroidManifest.xml` |
| IPC | `FLAG_IMMUTABLE` on every PendingIntent | `alarm/AlarmScheduler.kt`, `session/ProfileLifecycleService.kt` |
| Storage | App-private storage only | All `data/*Store.kt` |
| Permissions | `<queries>` + launcher filter (no `QUERY_ALL_PACKAGES`) | `AndroidManifest.xml` |
| Pause flow | Hard-coded system exemption set | `util/SystemExemptions.kt` |
| Pause flow | Runtime exemption resolver (launcher / IME / dialer / a11y) | `util/SystemExemptions.kt:32-72` |
| Pause flow | Restored package list tracked for reverse | `data/AppRestrictionsStore.kt` |
| Pause flow | Emergency "Unpause everything" recovery | `ui/SettingsScreen.kt` (Background pausing card) |
| Pause flow | Foreground service auto-deactivate on swipe-away | `session/ProfileLifecycleService.kt:74` |
| GDPR | In-app account deletion | `auth/AuthRepository.kt:48`, `ui/SettingsScreen.kt` |
| GDPR | In-app data export | `data/DataExporter.kt`, Settings → Account |

---

## 6. Reporting vulnerabilities

`security@thebikemechanic.ca`. PGP key available on request.

We aim to acknowledge reports within 7 business days for high-severity issues, 30 days otherwise. Coordinated disclosure preferred — please give us 90 days to ship a fix before public disclosure.

We don't currently run a paid bug-bounty program. Significant reports will be acknowledged in release notes (with the reporter's permission).
