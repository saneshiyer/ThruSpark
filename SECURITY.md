# ThruSpark Security

This document describes ThruSpark's threat model, attack surface, and the mitigations in place. It is written to give security-conscious users and reviewers a clear, honest picture of what the app does and doesn't protect against.

**Last audit:** 2026-05-25 against the v0.1.0 codebase (post-Supabase rip-out).

If you discover a vulnerability, please email `security@thebikemechanic.ca` rather than opening a public issue.

---

## 1. Executive summary

ThruSpark is an alpha-stage battery-saver utility for Android that uses Shizuku (a separate, free, open-source companion app) to apply elevated power-management actions: airplane mode, refresh-rate caps, app pausing via the OS-level `cmd package suspend` mechanism. The app stores per-profile settings and standalone alarms on the user's device.

The biggest security-relevant decisions:

- **No network calls. None.** The `INTERNET` permission is not declared in the manifest — the app literally cannot reach the network. No analytics, no telemetry, no remote auth. Verifiable by inspecting `AndroidManifest.xml` or the device's app info page.
- **No cloud backup.** `android:allowBackup="false"` in the manifest. Your profiles, alarms, and preferences never leave the device, not even to your own Google Drive.
- **Shizuku is the trust anchor.** Tier 2 capabilities depend on the user manually granting Shizuku, which is an open-source project we don't control. ThruSpark uses Shizuku for a small, well-defined set of shell commands documented in §3.5.
- **Pausing is reversible.** Every package we suspend or restrict is tracked in `AppRestrictionsStore`; deactivating the profile reverses everything. An emergency "Unpause everything" button exists in Settings as a last-resort recovery.
- **System-critical packages are always exempt.** A hard-coded list (`util/SystemExemptions.kt`) plus runtime detection (current launcher / IME / dialer / accessibility services / device admins) prevents the user from breaking their phone by forgetting to add critical packages to a profile's allowlist.

**Audit result (2026-05-25):** 0 Critical, 0 High, 0 Medium open. Two Medium findings (`allowBackup="true"` and verbose logcat output) were fixed in v0.1.0 — see §4.

---

## 2. Threat model

We consider three attacker categories.

**Local attacker with physical access to the unlocked device.** Out of scope. Any app on an unlocked phone can be inspected, modified, or replaced. ThruSpark is not designed to defend against this — Android itself doesn't.

**Local attacker with physical access to the locked device.** Mitigated by Android's full-disk encryption (FDE / file-based encryption since Android 10). All ThruSpark data lives in the app's private storage, which is encrypted at rest as part of the OS's default FDE — readable only after the user unlocks.

**Remote attacker on the network.** Not applicable. ThruSpark makes no network calls and declares no network permissions. There is no traffic to intercept, no credentials to phish, no server to compromise.

**Compromised companion app (Shizuku).** Shizuku is a separate process the user installs themselves. We trust Shizuku's signature check during binder pairing — Shizuku verifies its own signature, so a tampered-with Shizuku build would fail to bind. We do not implement an additional trust check on our side.

**Malicious or curious app on the same device.** Other apps cannot read ThruSpark's private storage (Android sandbox enforces this). They cannot bind to our `BIND_QUICK_SETTINGS_TILE` or `BIND_NOTIFICATION_LISTENER_SERVICE` services because those permissions are signature-level (system-only). They cannot intercept our pending intents because we use `FLAG_IMMUTABLE` on every PendingIntent we create. They cannot read logcat in API 26+ without `READ_LOGS` (a signature-level permission held only by system apps and ADB shell).

---

## 3. Attack surface

### 3.1 Network

None. `AndroidManifest.xml` does not declare `INTERNET` or `ACCESS_NETWORK_STATE`. ThruSpark cannot open a socket. The app does not embed any HTTP client or networking library on the classpath.

The only network-adjacent action in the app is the Shizuku setup video link in onboarding, which calls `startActivity(ACTION_VIEW)` and hands the URL off to the system browser — that traffic comes from the browser, not from ThruSpark.

### 3.2 Inter-process communication (IPC)

| Component | Exported | Permission gate | Notes |
|---|---|---|---|
| `MainActivity` | true | none | Required for launcher; `MAIN`/`LAUNCHER` filter only |
| `tile.ThruSparkTileService` | true | `BIND_QUICK_SETTINGS_TILE` (signature-level) | Only system can bind |
| `notification.ThruSparkNotificationListener` | true | `BIND_NOTIFICATION_LISTENER_SERVICE` (signature-level) | Only system can bind via user grant |
| `receiver.BootReceiver` | true | `RECEIVE_BOOT_COMPLETED` (system-only sender) | Intent-filtered to `BOOT_COMPLETED`; defensive `intent.action` check despite the filter |
| `alarm.AlarmReceiver` | false | — | Internal only |
| `alarm.AlarmActivity` | false | — | Internal only; `showWhenLocked=true` + `turnScreenOn=true` justified for alarm UX |
| `alarm.AlarmForegroundService` | false | — | Internal; `foregroundServiceType=specialUse` |
| `session.ProfileLifecycleService` | false | — | Internal; `foregroundServiceType=specialUse` |
| `rikka.shizuku.ShizukuProvider` | true | `INTERACT_ACROSS_USERS_FULL` (signature-level) | Required by Shizuku SDK; only system can call |

PendingIntents across the codebase (`alarm/AlarmScheduler.kt`, `alarm/AlarmForegroundService.kt`, `session/ProfileLifecycleService.kt`) all use `FLAG_IMMUTABLE`. No mutable PendingIntents in the build.

### 3.3 Local storage

- DataStore for profile state, user prefs, alarm entries, app restrictions (`data/*Store.kt`). All in app-private storage.
- App-private file storage for custom profile JSON (`data/CustomProfileStore.kt` — files in `${context.filesDir}/custom_profiles/`).
- No external storage usage.
- No SQLite database.
- No SharedPreferences except a single legacy `thruspark_prefs` holding the boolean onboarding-done flag.
- `android:allowBackup="false"` — nothing is cloud-backed up.

### 3.4 Permissions held

Full plain-English explanation in-app at Settings → Privacy & transparency → Permissions used. The full declared set:

| Permission | Why we need it |
|---|---|
| `WRITE_SETTINGS` | Brightness control |
| `ACCESS_NOTIFICATION_POLICY` | Do Not Disturb |
| `FOREGROUND_SERVICE` | Persistent active-profile notification |
| `FOREGROUND_SERVICE_SPECIAL_USE` | FGS type declaration (Android 14+) |
| `RECEIVE_BOOT_COMPLETED` | Restore active profile on reboot |
| `POST_NOTIFICATIONS` | Alarm + active-profile notifications |
| `SCHEDULE_EXACT_ALARM` | Alarm scheduling |
| `WAKE_LOCK` | Wake the device for an alarm |
| `USE_FULL_SCREEN_INTENT` | Show AlarmActivity over the lockscreen |
| `<queries>` block | List launchable apps for the per-profile picker (Android 11+ pattern; avoids `QUERY_ALL_PACKAGES`) |

**Explicitly absent**: `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `READ_PHONE_STATE`, `ACCESS_*_LOCATION`, `CAMERA`, `RECORD_AUDIO`, `READ_CONTACTS`, every other sensitive permission you might worry about.

### 3.5 Shizuku shell command surface

`capability/AppPauser.kt` and `capability/ShizukuCapabilityProvider.kt` are the only places that build shell commands sent through Shizuku. The commands are templated with package names obtained from `PackageManager.queryIntentActivities()` — Android's package-name validation restricts these to a safe charset (`[a-zA-Z0-9._]`), so command injection via package name is structurally impossible.

The exact command set is auditable in those two files. Examples:
- `am force-stop <pkg>`
- `cmd appops set <pkg> RUN_IN_BACKGROUND deny`
- `cmd package suspend --dialogMessage '...' <pkg>` (the dialog message is a hardcoded constant)
- `cmd package unsuspend <pkg>`
- `cmd appops set <pkg> RUN_IN_BACKGROUND default`
- `settings put global airplane_mode_on 1`
- `svc wifi disable`
- `settings put system peak_refresh_rate <hz>`
- `dumpsys device_policy` (read-only enumeration of active device admins)

**No user-controlled string** ever reaches a shell context. Profile names, dialog message text, and other user-editable fields are never interpolated into commands.

### 3.6 Notification listener access

`ThruSparkNotificationListener` has access to every notification on the device — a sensitive privilege. We commit:

- Notification content is **never** persisted to disk.
- Notification content is **never** written to logs at any log level.
- Notification content is **never** copied into Intent extras that leave the app process.
- The only action taken on a posted notification is `cancelNotification(sbn.key)` (cancel) or no-op (allow through).

The filter consults an in-memory allowlist (`util/NotificationFilterState`) populated by the active profile and clears it on deactivation.

---

## 4. Audit findings (2026-05-25)

Audit pass against the v0.1.0 codebase. 15 findings total; **0 Critical, 0 High, 2 Medium (both fixed), 4 Low (acceptable / deferred), 9 Informational (no action needed)**.

### Medium severity — fixed in v0.1.0

**M-1. `android:allowBackup="true"` exposed profiles + preferences to Google Drive backup.** *(Fixed.)* Now `android:allowBackup="false"`. ThruSpark data does not leave the device, including via auto-backup. If you reset / migrate phones, you re-set up ThruSpark on the new device.

**M-2. Verbose shell-command logging at DEBUG level included full command strings (with package names of paused apps).** *(Fixed.)* Logcat lines from `ShizukuManager` and `SystemExemptions` no longer include user app package names or device-admin package names. Full commands are only logged in debug builds (`BuildConfig.DEBUG`), where the developer is the only audience.

### Low severity — acceptable / deferred

**L-1. No app-layer encryption on DataStore.** ThruSpark relies on Android FDE for at-rest protection. There's no PII to protect (no email, no accounts, no identifiers), so the marginal value of EncryptedSharedPreferences is low. Defer until a feature lands that stores something sensitive.

**L-2. Shizuku `newProcess()` called via reflection.** Shizuku 13 marked `newProcess()` as `@RestrictTo(LIBRARY_GROUP)`; we bypass with reflection. Failure mode is graceful (`ShellResult.Error` returned, no silent crash); the official path is a UserService binding, which is a 150-line refactor planned for a later release.

**L-3. Tile UI refresh delay (300ms) may show stale state during long activations.** Cosmetic; `ProfileStateStore` is updated correctly.

**L-4. Deactivate timeout (8s) — apps may stay paused on Shizuku binder hang.** Mitigation in place: Settings → Background pausing → "Unpause everything now" runs the full restore independently of any active profile state.

### Informational

- PendingIntents use `FLAG_IMMUTABLE` consistently (verified across `alarm/AlarmScheduler.kt`, `alarm/AlarmForegroundService.kt`, `session/ProfileLifecycleService.kt`).
- BootReceiver explicitly verifies `intent.action == ACTION_BOOT_COMPLETED` despite the manifest filter (belt-and-suspenders).
- No `QUERY_ALL_PACKAGES` requested; `<queries>` block with launcher filter scopes the visibility.
- All exported components are gated by signature-level system permissions.
- No hardcoded secrets, API keys, or tokens anywhere in the source tree.
- All shell command construction is type-safe — no string-formatted user input reaches a shell context.
- `assembleDebug` is used for the released APK (debuggable, no minification) — appropriate for an alpha portfolio release, **not** suitable for production. See §5.
- Notification listener content is never persisted or transmitted.
- DataStore + filesystem profile JSON are in app-private storage; Android sandbox enforces isolation from other apps.

---

## 5. Known limitations

Things to be aware of, not things to fix.

**Debug-signed APK distribution.** The release-attached APK is built with `assembleDebug` and signed with Android's auto-generated debug keystore. This is appropriate for alpha-stage portfolio distribution; it is **not** suitable for Play Store publication. A production release would build with `assembleRelease` against a stable production keystore, enable ProGuard/R8, and gate the verbose logging behind a feature flag rather than `BuildConfig.DEBUG`.

**Local storage relies on Android FDE.** No app-layer encryption. On a rooted device or a forensic extraction with the device unlocked, custom profile contents are recoverable. Defensible because none of the stored data is PII — no email, no identifiers, just brightness percentages, package-name allowlists, and alarm schedules.

**Emergency restore reads from the local store, not the system.** If the DataStore is wiped externally while a profile is active, the recorded list of suspended packages is lost and "Unpause everything" finds nothing to restore. Real-world unlikely (the store survives reboots; uninstall fully removes it). Future versions could query system state directly via `dumpsys package`.

**Shizuku binder fragility.** If Shizuku is uninstalled, its service dies, or its API changes, ThruSpark gracefully falls back to Tier 1 (brightness / dark mode / DND only). No data loss, but the user loses access to airplane mode / refresh-rate caps / app suspension until Shizuku is restored.

---

## 6. In-place mitigations summary

| Layer | Mitigation | File reference |
|---|---|---|
| Network | No `INTERNET` permission declared | `AndroidManifest.xml` |
| Backup | `android:allowBackup="false"` | `AndroidManifest.xml` |
| IPC | Signature-permission gating on tile + notification listener | `AndroidManifest.xml` |
| IPC | `FLAG_IMMUTABLE` on every PendingIntent | `alarm/AlarmScheduler.kt`, `session/ProfileLifecycleService.kt` |
| Storage | App-private storage only | All `data/*Store.kt` |
| Permissions | `<queries>` + launcher filter (no `QUERY_ALL_PACKAGES`) | `AndroidManifest.xml` |
| Logging | Sensitive package names stripped from non-debug log lines | `shizuku/ShizukuManager.kt`, `util/SystemExemptions.kt` |
| Pause flow | Hard-coded system exemption set | `util/SystemExemptions.kt` |
| Pause flow | Runtime exemption resolver (launcher / IME / dialer / a11y / device admins) | `util/SystemExemptions.kt` |
| Pause flow | Restored package list tracked for reverse | `data/AppRestrictionsStore.kt` |
| Pause flow | Emergency "Unpause everything" recovery | `ui/SettingsScreen.kt` (Background pausing card) |
| Pause flow | Foreground service auto-deactivate on swipe-away | `session/ProfileLifecycleService.kt` |
| Data portability | In-app data export (JSON) | `data/DataExporter.kt`, Settings → Data |

---

## 7. Reporting vulnerabilities

`security@thebikemechanic.ca`. PGP key available on request.

Coordinated disclosure preferred — please give 90 days to ship a fix before public disclosure.
