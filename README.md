# ThruSpark

A free battery-saver utility for outdoor enthusiasts whose best privacy strategy is staying off the network in the first place.

> Bikepacking. Thru-hiking. Expedition kayaking. Long-haul flights. Battery anxiety in the backcountry.

ThruSpark gives you one-tap access to system-level power controls — the same kind of permissions normally reserved for manufacturer (OEM) apps — through small, well-defined integrations with each platform's native capability surface.

This is a monorepo for the Android and iOS apps. The two share a brand, a profile JSON schema, a Supabase backend, and a trust model — but each is a native app written in the platform's idiomatic language and frameworks. There is no cross-platform code-sharing layer.

---

## Layout

| Path | What it is |
|---|---|
| [`android/`](android/) | Android app — Kotlin + Compose, package `ca.thebikemechanic.thruspark`. Shipped. |
| [`ios/`](ios/) | iOS app — Swift + SwiftUI, bundle `ca.thebikemechanic.thruspark`. In development. |
| [`branding/`](branding/) | Shared brand assets (logos, icons, color tokens). |
| [`SECURITY.md`](SECURITY.md) | App-wide security and privacy commitments — applies to both platforms. |
| [`Compliance-Handoff.md`](Compliance-Handoff.md) | Pre-submission compliance reference for Google Play Console + Apple App Store Connect. |

Platform-specific build instructions live in each subfolder's README:
- [`android/README.md`](android/README.md)
- [`ios/README.md`](ios/README.md)

---

## Trust model

ThruSpark is built for users who would rather trust a small open-source project than a large one with telemetry. Concrete commitments (enforced on both platforms):

- **No analytics, no telemetry, no tracking.** Zero. Verifiable in-app via Settings → Network activity (the audit log shows every network request the app has ever made).
- **All data on-device by default.** Custom profiles, alarms, and settings live in app-private storage. Email is sent only when you explicitly sign in.
- **No persistent auth tokens.** Sensitive operations (account deletion) re-prompt for password.
- **In-app permission explainer.** Every permission, plain English, which feature uses it.
- **In-app account deletion + data export.** GDPR Article 17 + Article 20 compliance, accessible without emailing us.

Full security audit: [`SECURITY.md`](SECURITY.md).

---

## License

[MIT](LICENSE).

---

## Contact

- General feedback: `hello@thebikemechanic.ca`
- Security: `security@thebikemechanic.ca` (see [`SECURITY.md`](SECURITY.md))
- Privacy / data subject requests: `privacy@thebikemechanic.ca`
