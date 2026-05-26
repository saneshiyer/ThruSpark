# ThruSpark — Android

A free Android battery-saver utility for outdoor enthusiasts whose best privacy strategy is staying off the network in the first place.

> Bikepacking. Thru-hiking. Expedition kayaking. Long-haul flights. Battery anxiety in the backcountry.

ThruSpark gives you one-tap access to system-level power controls — the same kind of permissions normally reserved for manufacturer (OEM) apps — through a small, well-defined integration with the open-source [Shizuku](https://github.com/RikkaApps/Shizuku) project.

See the top-level [`README.md`](../README.md) for the project overview, status, privacy, and license. This file is the Android-specific build and architecture notes.

---

## What it does

| Feature | What it actually does |
|---|---|
| **Profiles** | Save bundles of power settings — brightness, dark mode, screen timeout, Do Not Disturb, radio toggles (with Shizuku), per-profile app allowlists. One tap activates a profile, another deactivates it. |
| **App pausing** | When a profile activates, every app NOT on the profile's allowlist gets `am force-stop`'d, RUN_IN_BACKGROUND-denied, and `cmd package suspend`'d. Tapping a paused app's icon shows Android's "App is paused" dialog. Deactivating restores them. |
| **Quick Settings tile** | Activate / deactivate from the pull-down shade. Defaults to the last-used profile. |
| **Multiple alarms** | Wake-up alarms with day-of-week repeat. Firing an alarm deactivates the active profile so your phone is ready when you are. |
| **Notification filtering** | While a Do-Not-Disturb profile is active, notifications from non-allowlisted apps are quietly cancelled. Foreground-service notifications are deliberately preserved. |

---

## Why Shizuku

Modern Android doesn't let regular apps toggle airplane mode, cap refresh rate, suspend other apps, or change carrier-network preferences. Those permissions are reserved for system / OEM apps.

Shizuku is a free, open-source companion app that bridges this gap safely. The user installs it themselves (Play Store or GitHub), starts its service via Wireless debugging, and grants ThruSpark access through Shizuku's own permission UI.

ThruSpark uses Shizuku for a small, auditable set of shell commands — see [`../SECURITY.md`](../SECURITY.md) §3.5.

Without Shizuku, ThruSpark falls back to "Tier 1" capabilities: brightness, dark mode, screen timeout, and Do Not Disturb. Useful but limited.

---

## Trust model

ThruSpark is built for users who would rather trust a small open-source project than a large one with telemetry. Concrete commitments, enforced by the manifest:

- **Zero network calls.** The `INTERNET` permission is not declared in `AndroidManifest.xml`. The app cannot phone home, cannot send analytics, cannot check for updates. Confirm via the device's app info page.
- **All data on-device.** Custom profiles, alarms, and settings live in app-private storage. No accounts, no email, no identifiers.
- **In-app permission explainer.** Settings → Privacy & transparency → Permissions used. Every permission, plain English, which feature uses it.
- **In-app data export.** GDPR Article 20 compliance, accessible from Settings → Data → Export my data.

Full security audit: [`../SECURITY.md`](../SECURITY.md).
Architecture overview: [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## Building from source

Requires Android Studio (any recent stable channel) and the Android SDK.

```bash
git clone https://github.com/saneshiyer/ThruSpark.git
cd ThruSpark/android
./gradlew assembleDebug
```

Open the project in Android Studio, wait for Gradle sync, hit Run. First sync downloads dependencies (~3 minutes); subsequent builds are quick.

No `local.properties` setup is required — there are no API keys or external services to configure.

### Build configuration

- AGP 9.1.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01
- `compileSdk = 36`, `minSdk = 30` (Android 11), `targetSdk = 36`
- Single module (`:app`) — the project is intentionally not over-engineered
- Gradle version catalog at `gradle/libs.versions.toml`

---

## Verifying a build

Settings → "Verify this build" opens the GitHub releases page. If a release publishes a SHA256 of the AAB / APK, you can confirm the binary you installed matches the source you can read here.

---

## License

MIT, see [`../LICENSE`](../LICENSE).

---

## Status

Paused as a product, published as a portfolio piece. The app builds and runs on Android 11+; the headline features (profiles, app pausing, alarms, notification filtering) are all implemented and tested on a real device. No active roadmap.
