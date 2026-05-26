# ThruSpark Security

This document describes ThruSpark's threat model, attack surface, and the mitigations in place. It is written to give security-conscious users and reviewers a clear, honest picture of what the app does and doesn't protect against.

If you discover a vulnerability, please email `security@thebikemechanic.ca` rather than opening a public issue.

---

## 1. Executive summary

ThruSpark is a battery-saver utility for Android that uses Shizuku (a separate, free, open-source companion app) to apply elevated power-management actions: airplane mode, refresh-rate caps, app pausing via the OS-level `cmd package suspend` mechanism. The app stores per-profile settings and standalone alarms on the user's device.

The biggest security-relevant decisions:

- **No network calls. None.** The `INTERNET` permission is not declared in the manifest — the app literally cannot reach the network. No analytics, no telemetry, no remote auth. Verifiable by inspecting `AndroidManifest.xml` or the device's app info page.
- **Shizuku is the trust anchor.** Tier 2 capabilities depend on the user manually granting Shizuku, which is an open-source project we don't control. ThruSpark uses Shizuku for a small, well-defined set of shell commands documented in `app/src/main/java/.../capability/AppPauser.kt`.
- **Pausing is reversible.** Every package we suspend or restrict is tracked in `AppRestrictionsStore`; deactivating the profile reverses everything. An emergency "Unpause everything" button exists in Settings as a last-resort recovery.
- **System-critical packages are always exempt.** A hard-coded list (`util/SystemExemptions.kt`) plus runtime detection (current launcher / IME / dialer / accessibility services) prevents the user from breaking their phone by forgetting to add critical packages to a profile's allowlist.

---

## 2. Threat model

We consider three attacker categories.

**Local attacker with physical access to the unlocked device.** Out of scope. Any app on an unlocked phone can be inspected, modified, or replaced. ThruSpark is not designed to defend against this — Android itself doesn't.

**Local attacker with physical access to the locked device.** Mitigated by Android's full-disk encryption (FDE / file-based encryption since Android 10). All ThruSpark data lives in the app's private storage, which is encrypted at rest as part of the OS's default FDE — readable only after the user unlocks.

**Remote attacker on the network.** Not applicable. ThruSpark makes no network calls and declares no network permissions. There is no traffic to intercept, no credentials to phish, no server to compromise.

**Compromised companion app (Shizuku).** Shizuku is a separate process the user installs themselves. We trust Shizuku's signature check during binder pairing — Shizuku verifies its own signature, so a tampered-with Shizuku build would fail to bind. We do not implement an additional trust check on our side.

**Malicious or curious app on the same device.** Other apps cannot read ThruSpark's private storage (Android sandbox enforces this). They cannot bind to our `BIND_QUICK_SETTINGS_TILE` or `BIND_NOTIFICATION_LISTENER_SERVICE` services because those permissions are signature-level (system-only). They cannot intercept our pending intents because we use `FLAG_IMMUTABLE` on every PendingIntent we create.

---

## 3. Attack surface

### 3.1 Network

None. `AndroidManifest.xml` does not declare `INTERNET` or `ACCESS_NETWORK_STATE`. ThruSpark cannot open a socket. The app does not embed any HTTP client or networking library.

The only network-adjacent action in the app is the Shizuku setup video link in onboarding, which calls `startActivity(ACTION_VIEW)` and hands the URL off to the system browser — that traffic comes from the browser, not from ThruSpark.

### 3.2 Inter-process communication (IPC)

- **Quick Settings tile** (`tile/ThruSparkTileService`): exposed=true, but `permission="android.permission.BIND_QUICK_SETTINGS_TILE"` (signature-level — only system can bind).
- **Notification listener** (`notification/ThruSparkNotificationListener`): exposed=true, `permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` (signature-level — only system can bind via user grant).
- **Boot receiver** (`receiver/BootReceiver`): exposed=true, intent-filtered to `ACTION_BOOT_COMPLETED` only. Defensive `intent.action != Intent.ACTION_BOOT_COMPLETED` check despite the filter.
- **Alarm receiver, alarm activity, alarm service** (`alarm/*`): all exported=false (internal only).
- **Profile lifecycle service** (`session/ProfileLifecycleService`): exported=false, foregroundServiceType=specialUse.
- **Shizuku provider** (`rikka.shizuku.ShizukuProvider`): exported=true, `permission="INTERACT_ACROSS_USERS_FULL"` (system-only). Required by the Shizuku SDK.
- **Main activity** (`ui/MainActivity`): exported=true (must be, to launch from launcher), `MAIN`/`LAUNCHER` intent filter only.

### 3.3 Local storage

- DataStore for profile state, user prefs, alarm entries, app restrictions (see `data/*Store.kt`).
- App-private file storage for custom profile JSON (`data/CustomProfileStore.kt` — files in `${context.filesDir}/custom_profiles/`).
- No external storage usage.
- No SQLite database.
- No SharedPreferences except a single legacy `thruspark_prefs` for the onboarding-done flag.

### 3.4 Permissions held

Full plain-English explanation in-app at Settings → Privacy & transparency → Permissions used. Cross-references `AndroidManifest.xml`. The most sensitive permission is `BIND_NOTIFICATION_LISTENER_SERVICE`, justified for in-app notification filtering with the explicit guarantee that notification content is never persisted (and, with no `INTERNET` permission, cannot be transmitted off-device).

### 3.5 Shizuku shell command surface

`capability/AppPauser.kt` and `capability/ShizukuCapabilityProvider.kt` are the only places that build shell commands sent through Shizuku. The commands are templated with package names obtained from `PackageManager.queryIntentActivities()` — Android's package-name validation restricts these to a safe charset (`[a-zA-Z0-9._]`), so command injection via package name is not possible.

The exact command set is auditable in those two files. Examples:
- `am force-stop <pkg>`
- `cmd appops set <pkg> RUN_IN_BACKGROUND deny`
- `cmd package suspend --dialogMessage '...' <pkg>`
- `settings put global airplane_mode_on 1`
- `svc wifi disable`

---

## 4. Known limitations

These are tradeoffs we explicitly accept rather than bugs to fix.

**Local storage relies on Android FDE; no app-layer encryption.** Custom profiles and alarm settings sit in DataStore and JSON files under app-private storage. On a rooted device or a forensic extraction with the device unlocked, those contents are recoverable. Defensible because (a) none of it is PII — there are no user accounts, no email, no identifiers — and (b) any attacker with that level of access can also read every other app's data.

**Shizuku.newProcess() called via reflection.** Shizuku 13 marked `newProcess()` as `@RestrictTo(LIBRARY_GROUP)`; we bypass this with reflection. The official path is to bind a custom `UserService` and route shell calls through it. Reflection is fragile — a future Shizuku version may rename, sign-restrict, or remove the method. Failure mode is exceptions at runtime, surfaced via the `ShellResult.Error` return type and caller-side failure messaging (acceptable, not silent).

**Device admin packages are not auto-exempted from app pausing.** If the user has a device-admin app installed (corporate MDM, enterprise security app, antivirus with admin role) and forgets to add it to a profile's allowlist, the pause flow will suspend it — potentially breaking the device's compliance posture or triggering a security alert from the admin app. Workaround: add admin apps to the allowlist manually.

**Tile UI refresh delay (300ms) may show stale state during long activations.** A profile activation involving Shizuku app-pause can take 1–3s. The tile refreshes its on/off badge after 300ms, before activation completes. Cosmetic only; `ProfileStateStore` is updated correctly.

**ProfileLifecycleService deactivate timeout (8s) — apps may stay paused on Shizuku binder hang.** If Shizuku is uninstalled or its binder dies between profile activation and the user swiping the app away, deactivation may not complete within the timeout window; suspended packages stay suspended. Mitigation in place: Settings → Background pausing → "Unpause everything now" runs the full restore independently.

---

## 5. In-place mitigations summary

| Layer | Mitigation | File reference |
|---|---|---|
| Network | No `INTERNET` permission declared | `AndroidManifest.xml` |
| IPC | Signature-permission gating on tile + notification listener | `AndroidManifest.xml` |
| IPC | `FLAG_IMMUTABLE` on every PendingIntent | `alarm/AlarmScheduler.kt`, `session/ProfileLifecycleService.kt` |
| Storage | App-private storage only | All `data/*Store.kt` |
| Permissions | `<queries>` + launcher filter (no `QUERY_ALL_PACKAGES`) | `AndroidManifest.xml` |
| Pause flow | Hard-coded system exemption set | `util/SystemExemptions.kt` |
| Pause flow | Runtime exemption resolver (launcher / IME / dialer / a11y) | `util/SystemExemptions.kt` |
| Pause flow | Restored package list tracked for reverse | `data/AppRestrictionsStore.kt` |
| Pause flow | Emergency "Unpause everything" recovery | `ui/SettingsScreen.kt` (Background pausing card) |
| Pause flow | Foreground service auto-deactivate on swipe-away | `session/ProfileLifecycleService.kt` |
| Data portability | In-app data export (JSON) | `data/DataExporter.kt`, Settings → Data |

---

## 6. Reporting vulnerabilities

`security@thebikemechanic.ca`. PGP key available on request.

Coordinated disclosure preferred — please give 90 days to ship a fix before public disclosure.
