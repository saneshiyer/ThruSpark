# ThruSpark for iOS

Native SwiftUI app, iOS 17.0+, bundle ID `ca.thebikemechanic.thruspark`.

**Status: Phase 0 — pre-Xcode-project.** No `.xcodeproj`, no `Package.swift` yet. The canonical scope and 10-phase build plan lives at `../../Claude/Projects/Expedition Mode/ThruSpark-iOS-Scope.md` (off-repo).

## Honesty tiers

iOS cannot do everything Android can. The product compensates with three honest tiers:

| Tier | Mechanism | Examples |
|---|---|---|
| **1 — Native** | Public iOS APIs | Brightness, in-app dark mode, Focus Filter (App Intents), Live Activity, `UNNotification` alarms |
| **2 — Shortcuts** | User-installed ThruSpark Shortcut | Low Power Mode, Airplane Mode, Wi-Fi, Bluetooth toggles |
| **3 — Guided** | Deep-links + step-by-step UI | Cellular toggle, Auto-Lock duration, Grayscale |

## What's NOT included on iOS

iOS provably cannot: force LTE-only, toggle NFC, cap refresh rate, restrict per-app background activity, read other apps' notifications, list installed apps, programmatically toggle Airplane / Wi-Fi / Bluetooth without a user-installed Shortcut, ring alarms through silent mode (without Critical Alerts entitlement), or expose a Quick Settings tile (iOS 17). The product schema drops these fields rather than lying about them.

## Build phases (high level)

0. Repo restructure + Xcode project skeleton ← **next step**
1. Data layer (port `ThruSparkProfile`, drop `force_lte` and `nfc`)
2. Capability core (`actor ProfileEngine`, `NativeCapabilityProvider`)
3. Auth (`URLSession` + Keychain, same Supabase endpoints as Android)
4. UI shell + onboarding
5. Alarms (`UNCalendarNotificationTrigger`, `.timeSensitive`)
6. Live Activity (ActivityKit)
7. Shortcuts integration (Tier 2)
8. Builder + iTunes Search picker (replaces Android's installed-apps picker)
9. Settings + compliance
10. Pre-submission (TestFlight → App Store)

Estimate: ~9–10 weekends for an Apple-platform beginner.

## Apple-side prerequisites

- Apple Developer Program — enrolled under `sanesh101@gmail.com`.
- Bundle ID registration: `ca.thebikemechanic.thruspark`.
- Apple Services ID for OAuth: `ca.thebikemechanic.signin` (single "e" — verify spelling before generating the private key).
- Capabilities: Associated Domains, App Groups (`group.ca.thebikemechanic.thruspark`).
- Critical Alerts entitlement — apply *after* TestFlight is feature-complete (1–2 week Apple turnaround).
- Universal link: `apple-app-site-association` file at `https://thebikemechanic.ca/.well-known/` (coordinated with the website work).
