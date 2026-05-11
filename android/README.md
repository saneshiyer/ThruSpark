# ThruSpark

A free Android battery-saver utility for outdoor enthusiasts whose best privacy strategy is staying off the network in the first place.

> Bikepacking. Thru-hiking. Expedition kayaking. Long-haul flights. Battery anxiety in the backcountry.

ThruSpark gives you one-tap access to system-level power controls — the same kind of permissions normally reserved for manufacturer (OEM) apps — through a small, well-defined integration with the open-source [Shizuku](https://github.com/RikkaApps/Shizuku) project.

---

## What it does

| Feature | What it actually does |
|---|---|
| **Profiles** | Save bundles of power settings — brightness, dark mode, screen timeout, Do Not Disturb, radio toggles (with Shizuku), per-profile app allowlists. One tap activates a profile, another deactivates it. |
| **App pausing** | When a profile activates, every app NOT on the profile's allowlist gets `am force-stop`'d, RUN_IN_BACKGROUND-denied, and `cmd package suspend`'d. Tapping a paused app's icon shows Android's "App is paused" dialog. Deactivating restores them. |
| **Quick Settings tile** | Activate / deactivate from the pull-down shade. Defaults to the last-used profile. |
| **Multiple alarms** | Wake-up alarms with day-of-week repeat. Firing an alarm deactivates the active profile so your phone is ready when you are. |
| **Notification filtering** | While a Do-Not-Disturb profile is active, notifications from non-allowlisted apps are quietly cancelled. Foreground-service notifications are deliberately preserved. |
| **Optional account** | Email + password sign-in via Supabase. Lets you sync profiles between devices in a future version. Account is fully optional — every feature works without one. |

---

## Why Shizuku

Modern Android doesn't let regular apps toggle airplane mode, cap refresh rate, suspend other apps, or change carrier-network preferences. Those permissions are reserved for system / OEM apps.

Shizuku is a free, open-source companion app that bridges this gap safely. The user installs it themselves (Play Store or GitHub), starts its service via Wireless debugging, and grants ThruSpark access through Shizuku's own permission UI.

ThruSpark uses Shizuku for a small, auditable set of shell commands — see [`SECURITY.md`](SECURITY.md) §3.5.

Without Shizuku, ThruSpark falls back to "Tier 1" capabilities: brightness, dark mode, screen timeout, and Do Not Disturb. Useful but limited.

---

## Trust model

ThruSpark is built for users who would rather trust a small open-source project than a large one with telemetry. Concrete commitments:

- **No analytics, no telemetry, no tracking.** Zero. Verifiable in-app via Settings → Network activity (the audit log shows every network request the app has ever made).
- **All data on-device by default.** Custom profiles, alarms, and settings live in app-private storage. Email is sent only when you explicitly sign in.
- **No persistent auth tokens.** Sensitive operations (account deletion) re-prompt for password; we don't cache JWTs.
- **In-app permission explainer.** Settings → Privacy & transparency → Permissions used. Every permission, plain English, which feature uses it.
- **In-app account deletion + data export.** GDPR Article 17 + Article 20 compliance, accessible without emailing us.

Full security audit: [`../SECURITY.md`](../SECURITY.md).
Architecture overview: [`ARCHITECTURE.md`](ARCHITECTURE.md).
Pre-store-submission compliance work: [`../Compliance-Handoff.md`](../Compliance-Handoff.md).

---

## Building from source

Requires Android Studio (any recent stable channel) and the Android SDK.

```bash
git clone https://github.com/REPLACE_WITH_REAL_OWNER/ExpeditionMode.git
cd ExpeditionMode
cp local.properties.example local.properties
# Edit local.properties to add your SUPABASE_URL and SUPABASE_ANON_KEY
# (or leave them blank to build without auth — every other feature works)
```

Open the project in Android Studio, wait for Gradle sync, hit Run. First sync downloads dependencies (~3 minutes); subsequent builds are quick.

For sideload + on-device testing instructions, see [`Build-And-Sideload-v01.md`](Build-And-Sideload-v01.md).

### Build configuration

- AGP 9.1.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01
- `compileSdk = 36`, `minSdk = 30` (Android 11), `targetSdk = 36`
- Single module (`:app`) — the project is intentionally not over-engineered
- Gradle version catalog at `gradle/libs.versions.toml`

---

## Verifying a build

Settings → "Verify this build" opens the GitHub releases page for that version. Each release publishes the SHA256 of the AAB / APK so you can confirm the binary you installed matches the source you can read.

(Pending: open-source publication. Until then, the verify link will 404 — see `project_thruspark_v05_open_source.md` in the development memory.)

---

## License

Pending decision (see `project_thruspark_v05_open_source.md`). When published as open source, the license will be MIT or Apache 2.0.

---

## Status

Pre-launch. The app builds, runs on Android 11+, and the headline features (profiles, app pausing, alarms, notification filtering) are all implemented and tested on a real device. Next milestones:

- Resolve the open-source publication question
- Complete the v0.4 compliance pre-flight (privacy policy live, Edge Function deployed, Play Console submission)
- v0.6 work: comprehensive code commenting pass, EncryptedSharedPreferences for the signed-in email, Shizuku UserService binding (replacing the current reflection bypass)

iOS app is in active development under [`../ios/`](../ios/) — see the top-level [`README.md`](../README.md) for cross-platform context.

---

## Contact

- General feedback: `hello@thebikemechanic.ca`
- Security: `security@thebikemechanic.ca` (see [`SECURITY.md`](SECURITY.md))
- Privacy / data subject requests: `privacy@thebikemechanic.ca`
