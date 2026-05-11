# ThruSpark Architecture

System design overview for code reviewers and contributors. Read this once, then the source.

The app is intentionally simple: a single module, a small set of clearly-bounded packages, no DI framework, no event bus, no service locator. Standard Android Jetpack patterns throughout (Compose, ViewModel, DataStore, WorkManager). State flows in one direction; UI observes via `StateFlow`.

---

## 1. Module + package layout

```
app/
├── src/main/
│   ├── AndroidManifest.xml             # all permissions + service registrations
│   ├── assets/profiles/                # bundled preset (Minimum Power) + seeds
│   └── java/ca/thebikemechanic/thruspark/
│       ├── ThruSparkApp.kt             # Application class — initializes ShizukuManager
│       ├── alarm/                      # AlarmManager-based wake-up alarms
│       ├── auth/                       # Supabase email/password auth + token refresh
│       ├── capability/                 # Two-tier capability providers (Standard, Shizuku)
│       ├── data/                       # DataStore wrappers + profile JSON I/O
│       ├── engine/                     # ProfileEngine — orchestrates activation/deactivation
│       ├── model/                      # Pure data classes (ThruSparkProfile, AlarmEntry)
│       ├── notification/               # NotificationListenerService — DND filtering
│       ├── receiver/                   # BootReceiver — restore active profile on reboot
│       ├── session/                    # WorkManager session timer + foreground lifecycle service
│       ├── shizuku/                    # ShizukuManager — binder + shell command exec
│       ├── tile/                       # Quick Settings tile service
│       ├── ui/                         # Compose UI: screens, ViewModels, nav
│       └── util/                       # SystemExemptions + AppCategories + NotificationFilterState
```

Each package has one or two responsibilities and minimal coupling to others. `ui/` depends on `model/`, `data/`, `auth/`, `engine/`, `shizuku/`, `util/` — never the reverse.

---

## 2. Data model

### 2.1 ThruSparkProfile

The central data class (`model/ThruSparkProfile.kt`). Represents a complete power configuration:

```
ThruSparkProfile
├── name, version
├── display: { brightness, darkMode, grayscale, refreshHz, timeoutSec }
├── radios: { airplane, wifi, cellular, forceLte, gps, bluetooth, nfc }
├── notifications: { dndEnabled, allowlistContacts, allowlistApps }
├── background: { restrictAllExcept, freezeNonEssential }
├── essentialApps: { categories, explicitPackages }
├── session: { durationHours, autoDeactivateAt }
└── alarm: AlarmSettings (legacy, embedded; standalone alarms are separate)
```

Schema is JSON-serializable via `kotlinx.serialization`. Bundled assets and user-saved custom profiles share the same schema. Seeded profiles (City Life / Sport / Expedition) are copied from `assets/profiles/seeds/` into `CustomProfileStore` on first launch — they're treated as user profiles, fully editable and deletable.

### 2.2 The "template + customs" model (v0.3)

There is exactly one bundled preset (read-only "template"): **Minimum Power** in `assets/profiles/minimum_power.json`. It represents the absolute floor — most aggressive battery saver.

Users start by either:
- **Tapping a seeded profile** (City Life / Sport / Expedition) and editing it, or
- **Tapping ⋮ → Duplicate** on the template to clone it as their own custom profile

There is no "preset library" anymore (v0.1 had four trip-specific presets; we deleted them). The clearer mental model: ThruSpark gives you the floor, you opt in to what your trip needs.

---

## 3. Activation pipeline

```
User taps GO / tile / preset card
        │
        ▼
MainViewModel.activateProfile(name)        ──┐
ProfileEngine.activateDefault(context)     ──┤  Both routes converge on:
TileService.onClick → activateDefault      ──┘
        │
        ▼
ProfileEngine.activateProfile(context, name)
        │
        ▼
CapabilityProviderResolver.pick(context)
        │  ├── ShizukuState.Granted   → ShizukuCapabilityProvider
        │  └── otherwise              → StandardCapabilityProvider (Tier 1 only)
        ▼
provider.apply(profile)
        │
        ├─ Tier 1: brightness, dark mode, DND, screen timeout
        ├─ Tier 2 (Shizuku only): airplane mode, refresh rate cap,
        │   grayscale, radios via svc commands
        └─ App pausing (always-on in v0.3+):
              ├─ Build exemption set (SystemExemptions + profile.explicitPackages
              │                       + AppCategories.installedPackages(categories))
              ├─ toPause = all launchable - exempt
              ├─ For each batch of 25:
              │      am force-stop $pkg
              │      cmd appops set $pkg RUN_IN_BACKGROUND deny
              │      cmd package suspend --dialogMessage '...' $pkg
              └─ Persist toPause to AppRestrictionsStore (for reverse on deactivate)
        │
        ▼
Side effects:
  ├─ ProfileStateStore.setActive(name)
  ├─ ProfileLifecycleService.start(context, name)  # foreground notification + onTaskRemoved
  ├─ NotificationFilterState.setAllowlist(...)     # in-memory cache for the listener
  ├─ SessionScheduler.scheduleEnd(hours?)          # WorkManager timer
  └─ AlarmScheduler.schedule(time, label)          # legacy profile-embedded alarm
```

Deactivation reverses everything in roughly the reverse order. The `ProfileLifecycleService.onTaskRemoved` hook auto-deactivates if the user swipes ThruSpark away from recents.

---

## 4. Capability resolution (Tier 1 vs Tier 2)

`capability/CapabilityProviderResolver.kt` picks the right `CapabilityProvider` at every activation:

- `StandardCapabilityProvider` — always available, requires `WRITE_SETTINGS` + Notification Policy access. Handles brightness, dark mode, DND, screen timeout. Everything else is reported as "skipped" with a rationale.
- `ShizukuCapabilityProvider` — used when Shizuku is granted. Wraps `StandardCapabilityProvider` (composition, not inheritance) and adds Tier 2 capabilities through Shizuku shell commands.

The resolution happens per-call so a user who grants Shizuku mid-session gets the upgraded provider on their next activation without restart.

---

## 5. App pausing implementation detail

The "kill apps so they don't drain battery in the background" feature is the hardest part of the codebase to get right and the most security-relevant. See `capability/AppPauser.kt`.

Two-level safeguards prevent the user from soft-bricking their device:

**Level 1: hard-coded mandatory exemption list** (`util/SystemExemptions.MANDATORY`).
- ThruSpark itself, Shizuku
- `android`, `com.android.systemui`
- Settings + permission infrastructure providers
- Play Services + Services Framework + IMS
- WebView providers (Google + AOSP — apps using WebView crash if these are suspended)
- Telephony stack + dialer + emergency
- Common launchers across major OEMs (Pixel, Samsung, MIUI, EMUI, OnePlus, Motorola)
- Common keyboards (Gboard, Samsung Honeyboard, SwiftKey)
- Bluetooth / NFC / printspooler stacks

**Level 2: runtime-resolved exemptions** (`SystemExemptions.resolveAll()`).
- Current launcher (resolved via `Intent.ACTION_MAIN + CATEGORY_HOME`)
- Current IME (read from `Settings.Secure.DEFAULT_INPUT_METHOD`)
- Current dialer (resolved via `Intent.ACTION_DIAL`)
- All currently-enabled accessibility services

**Plus per-profile**: `profile.essential_apps.explicit_packages` (user picker selection) + `profile.essential_apps.categories` (resolved through `AppCategories.installedPackages()`).

The set of "to pause" apps is `(all launchable user apps) - (mandatory + runtime + per-profile)`. If everything works, ThruSpark + the user's launcher + their phone app + the apps they explicitly allowed stay alive; everything else is OS-level suspended.

Per-package state tracking: every package we suspend goes into `data/AppRestrictionsStore`. Deactivation reads that list and reverses every action. An emergency "Unpause everything now" button in Settings runs the same reverse-all flow if state ever gets stuck.

---

## 6. Auth (Supabase)

`auth/SupabaseAuth.kt` is a thin OkHttp wrapper around Supabase's REST API. We deliberately avoid the Supabase Kotlin SDK to keep the dependency tree small (the SDK pulls in Ktor + 8 other modules).

Persistence model: only the signed-in email is stored locally (`UserPrefsStore.signedInEmail`). JWTs are NOT persisted. Sensitive operations re-authenticate to obtain a fresh token (sensitive-action re-auth pattern, matches Google / Apple / GitHub).

Auth is fully optional — the app's first-launch flow includes a "Skip" path. Everything except cloud sync (planned for a later version) works without an account.

Account deletion calls a user-deployed Supabase Edge Function (`/functions/v1/delete-account`); see `Compliance-Handoff.md` for the deploy script.

---

## 7. Navigation

Single Compose-Nav graph in `ui/AppNav.kt`. Three bottom-nav destinations (Modes / Alarms / Settings) plus secondary destinations (Builder / Auth / Shizuku setup / App picker / Alarm edit / Permissions / Network activity).

The first-launch flow lives outside `AppNav` in `MainActivity`'s gating composable:

```
auth phase done?    → no → AuthFlow (Welcome → Sign in / Sign up / Skip)
walkthrough done?   → no → WalkthroughScreen (3 trust-focused slides)
permissions done?   → no → OnboardingScreen (system permission grants)
shizuku setup done? → no → ShizukuOnboardingScreen (5-step guided walkthrough)
                          → AppNav (bottom-nav destinations)
```

Each gate writes a `*_done` flag in `UserPrefsStore` so returning users skip ahead. Each gate is independently dismissable — skipping Shizuku doesn't block app usage; skipping auth doesn't block anything except cloud sync.

---

## 8. Persistence layer

Five DataStore-backed stores + one filesystem-backed store. All in `data/`:

| Store | What it holds | Backed by |
|---|---|---|
| `ProfileStateStore` | Active profile name, activated-at timestamp, isActive flag | DataStore |
| `UserPrefsStore` | Onboarding flags, signed-in email, last-used profile | DataStore |
| `CustomProfileStore` | User-created profile JSON files | Filesystem (`filesDir/custom_profiles/*.json`) |
| `AlarmEntryStore` | List of standalone alarms | DataStore (single JSON value) |
| `NetworkActivityStore` | Last 50 network requests for the audit log | DataStore (single JSON value) |
| `AppRestrictionsStore` | Packages currently suspended by the active profile | DataStore (CSV string) |

`AppDataReset.kt` provides two reset levels: "restart onboarding" (clears just the flow flags) and "clear all data" (full nuclear).

---

## 9. Background work

| Component | Trigger | Purpose |
|---|---|---|
| `BootReceiver` | `BOOT_COMPLETED` | Restores the previously-active profile on device restart |
| `SessionScheduler` (WorkManager) | Profile activation with `duration_hours` | Auto-deactivates the profile when the session expires |
| `AlarmScheduler` (AlarmManager) | User creates / enables an alarm | Wakes the device + shows AlarmActivity at the scheduled time |
| `ProfileLifecycleService` (foreground) | Profile activation | Persistent status notification + auto-deactivate on swipe-away |
| `ThruSparkTileService` | User taps the QS tile | One-tap activate / deactivate using the last-used profile |
| `ThruSparkNotificationListener` | A notification is posted | Cancels notifications from non-allowlisted apps while a DND profile is active |

---

## 10. Threading

Coroutines throughout. Three scopes in active use:
- `viewModelScope` (Compose ViewModels) — UI-driven work
- `lifecycleScope` (MainActivity) — pref writes triggered by gating
- `ProfileEngine`'s internal `CoroutineScope(Dispatchers.IO)` — engine work triggered from non-coroutine contexts (TileService, BootReceiver)

`runBlocking` is used in three deliberate places, each documented:
- `ProfileStateStore.isActive(context)` — TileService doesn't have a coroutine scope, blocks briefly to read DataStore
- `ProfileLifecycleService.runDeactivate()` — onTaskRemoved must complete before service teardown; wrapped in `withTimeoutOrNull(8s)`
- (no others)

---

## 11. Testing strategy

Honest current state: no automated tests. Validation has been on-device manual testing on a real Android phone (Pixel-class device).

Planned for v0.6+:
- Unit tests for `AppPauser` exemption resolution (deterministic, no Android dependency)
- Unit tests for `AlarmScheduler.nextOccurrenceMillis` (date math, deterministic)
- Instrumented tests for the navigation graph + first-launch gating flow

The codebase is structured to make tests cheap to add — pure-Kotlin packages (`util/`, `model/`, parts of `data/`) are easily JVM-testable; Android-coupled packages (`alarm/`, `tile/`, `receiver/`) need Robolectric or instrumented tests.

---

## 12. What's NOT in the architecture

For the curious, things you might expect but won't find:

- **No DI framework.** Hilt / Koin would be overkill at this scale. Constructors take what they need; ViewModels use `AndroidViewModel(application)` for context access.
- **No event bus.** `StateFlow` carries all state changes that cross the ViewModel ↔ UI boundary. Cross-component signaling (e.g. ProfileEngine → NotificationFilterState) uses singleton state holders explicitly.
- **No persistence framework / Room.** DataStore + JSON files are sufficient for the data shapes we have.
- **No analytics SDK.** Deliberately. See `SECURITY.md`.
- **No crash reporter.** Could add opt-in Sentry / Crashlytics in a later version; would be off by default and require user consent.
- **No feature flags.** Single-channel app, ship to all users at the same time.
- **No multi-module build.** One `:app` module. If iOS happens, the shared schema would justify a `:shared` Kotlin Multiplatform module — not before.
