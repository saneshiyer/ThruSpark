# ThruSpark

A free Android battery-saver utility for outdoor enthusiasts whose best privacy strategy is staying off the network in the first place.

> Bikepacking. Thru-hiking. Expedition kayaking. Long-haul flights. Battery anxiety in the backcountry.

ThruSpark gives you one-tap access to system-level power controls — the same kind of permissions normally reserved for manufacturer (OEM) apps — through a small, well-defined integration with the open-source [Shizuku](https://github.com/RikkaApps/Shizuku) project.

---

## Status

**Paused as a product, published as a for those who are interested.** The app builds and runs on Android 11+ with all headline features implemented. There is no active roadmap, no Play Store listing, and no plan to pursue iOS in the near future. The source is here so it's auditable and forkable, and so I can point at it. Use at your own risk.

---

## What it does

| Feature | What it actually does |
|---|---|
| **Profiles** | Save bundles of power settings — brightness, dark mode, screen timeout, Do Not Disturb, radio toggles (with Shizuku), per-profile app allowlists. One tap activates a profile, another deactivates it. |
| **App pausing** | When a profile activates, every app NOT on the profile's allowlist gets `am force-stop`'d, `RUN_IN_BACKGROUND`-denied, and `cmd package suspend`'d. Tapping a paused app's icon shows Android's "App is paused" dialog. Deactivating restores them. |
| **Quick Settings tile** | Activate / deactivate from the pull-down shade. Defaults to the last-used profile. |
| **Multiple alarms** | Wake-up alarms with day-of-week repeat. Firing an alarm deactivates the active profile so your phone is ready when you are. |
| **Notification filtering** | While a Do-Not-Disturb profile is active, notifications from non-allowlisted apps are quietly cancelled. Foreground-service notifications are deliberately preserved. |

---

## Tech stack

- Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01), AGP 9.1.1, JVM 17
- `compileSdk = 36`, `minSdk = 30` (Android 11), `targetSdk = 36`
- Single Gradle module (`:app`), version catalog at `android/gradle/libs.versions.toml`
- Compose Navigation, Material 3, Material icons, Compose Foundation pager
- AndroidX DataStore (Preferences) for all on-disk state
- Kotlin Serialization for profile / alarm JSON and the data-export payload
- WorkManager for session timers, AlarmManager for wake-up alarms
- Shizuku SDK (v13) for Tier 2 capability access via ADB-granted binder
- Architecture overview: [`android/ARCHITECTURE.md`](android/ARCHITECTURE.md)

No DI framework, no Room, no RxJava, no third-party analytics, no network library. The dependency graph is short on purpose.

---

## Why Shizuku

Modern Android doesn't let regular apps toggle airplane mode, cap refresh rate, suspend other apps, or change carrier-network preferences. Those permissions are reserved for system / OEM apps.

[Shizuku](https://github.com/RikkaApps/Shizuku) is a free, open-source companion app that bridges this gap safely. The user installs it themselves (Play Store or GitHub), starts its service via Wireless debugging, and grants ThruSpark access through Shizuku's own permission UI.

ThruSpark uses Shizuku for a small, auditable set of shell commands — see [`SECURITY.md`](SECURITY.md) §3.5.

Without Shizuku, ThruSpark falls back to "Tier 1" capabilities: brightness, dark mode, screen timeout, and Do Not Disturb. Useful but limited.

---

## Trust model

ThruSpark is built for users who would rather trust a small open-source project than a large one with telemetry. Concrete commitments, enforced by the manifest:

- **Zero network calls.** The `INTERNET` permission is not declared in `AndroidManifest.xml`. The app cannot phone home, cannot send analytics, cannot check for updates. Confirm via the device's app info page or by reading the manifest yourself.
- **All data on-device.** Custom profiles, alarms, and settings live in app-private storage. No accounts, no email, no identifiers.
- **In-app permission explainer.** Settings → Privacy & transparency → Permissions used. Every declared Android permission, plain English, which feature uses it.
- **In-app data export.** GDPR Article 20 compliance — export everything ThruSpark stores about you as a JSON file you control.

Full security audit: [`SECURITY.md`](SECURITY.md).

---

## Building from source

Requires Android Studio (any recent stable channel) and the Android SDK.

```bash
git clone https://github.com/saneshiyer/ThruSpark.git
cd ThruSpark/android
./gradlew assembleDebug
```

Open the project in Android Studio for the IDE experience. First Gradle sync downloads dependencies (~3 minutes); subsequent builds are quick.

For sideloading and on-device testing, see [`android/README.md`](android/README.md).

### Build configuration

- AGP 9.1.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01
- `compileSdk = 36`, `minSdk = 30` (Android 11), `targetSdk = 36`
- Single module (`:app`) — the project is intentionally not over-engineered
- Gradle version catalog at `android/gradle/libs.versions.toml`

No `local.properties` configuration is required — there are no API keys or external services to wire up for a debug build.

---

## License

[MIT](LICENSE). Copyright (c) 2026 Sanesh Iyer.

---

<a id="privacy"></a>
## Privacy

ThruSpark does not collect, transmit, or share any data.

The app declares no `INTERNET` permission, embeds no networking library, and contains no analytics or telemetry SDKs. Everything ThruSpark knows about you — your custom profiles, your alarms, your last-used profile — lives in app-private storage on your device and is deleted when you uninstall the app.

There are no user accounts. There is no email collection. There are no identifiers (no advertising ID, no Firebase ID, no install token).

Third parties involved when you use ThruSpark:
- **Shizuku** (optional companion app, installed and granted by you): an open-source project ThruSpark sends shell commands to. ThruSpark sends Shizuku the commands listed in [`SECURITY.md`](SECURITY.md) §3.5; Shizuku does not phone home either.
- **Android system browser** (when you tap the "Watch the setup video" link): hands the YouTube URL off to whatever browser you have set as default. That request comes from the browser, not from ThruSpark.

You can export everything ThruSpark stores about you from Settings → Data → Export my data. The output is a JSON file containing your custom profiles and alarms.

---

<a id="terms"></a>
## Terms

ThruSpark is provided under the [MIT License](LICENSE) — "as is", without warranty of any kind. You're free to use, modify, fork, and redistribute the code subject to the license.

Using Shizuku, app-suspension shell commands, and elevated power controls can affect how other apps behave on your device. Read [`SECURITY.md`](SECURITY.md) and the in-app permission explainer before granting permissions you're unsure about.

This is a paused project and a portfolio piece. There is no support obligation and no guarantee of future updates.

---

## Contact

- General feedback: `hello@thebikemechanic.ca`
- Security: `security@thebikemechanic.ca` (see [`SECURITY.md`](SECURITY.md))
- Privacy / data subject requests: `privacy@thebikemechanic.ca`
