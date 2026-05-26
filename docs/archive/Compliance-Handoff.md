# ThruSpark Compliance Handoff

Reference doc for store submission (Google Play Console, Apple App Store Connect) and ongoing compliance (GDPR, PIPEDA, Supabase DPA). Written so you can copy-paste straight into the relevant forms.

Updated 2026-04-27 during v0.4 work. Re-verify before each submission — store policies change quarterly.

---

## 1. Privacy policy + Terms — required URLs

These need to live at:
- `https://thebikemechanic.ca/privacy` — Privacy Policy with ThruSpark addendum
- `https://thebikemechanic.ca/terms` — Terms of Service

Both are linked from the app:
- Sign-up screen: inline "By creating an account, you agree to our [Terms] and [Privacy Policy]"
- Welcome screen: footer link
- Settings → Account section: footer link

The URLs are hard-coded in `ui/LegalLinks.kt` constants `URL_PRIVACY_POLICY` and `URL_TERMS_OF_SERVICE`. Update there if the URL pattern changes.

**Both stores REQUIRE the URL to be live and serve actual policy content before submission.** A 404 or "coming soon" page = rejection.

---

## 2. Supabase DPA

**On Free tier:** the DPA terms apply automatically through the Terms of Service you accepted at signup. Cite the public DPA in your privacy policy:
> ThruSpark uses Supabase (a third-party processor) for account authentication. Supabase processes data per their Data Processing Agreement at <https://supabase.com/legal/dpa>.

**On Pro tier or above:** explicit acceptance available in dashboard. To find it:
1. https://supabase.com/dashboard
2. Switch to the org containing the ThruSpark project
3. Settings (org-level, gear icon at bottom of org sidebar)
4. Compliance / Privacy and Security tab
5. Click "Sign DPA" — you'll get a counter-signed PDF

If you can't find the panel: email `privacy@supabase.com` with your org ID.

---

## 3. Account deletion — Supabase Edge Function

The in-app "Delete account" flow re-authenticates the user and POSTs to a `delete-account` Edge Function. **You must deploy this function before account deletion will work end-to-end.**

### Deploy the Edge Function

```bash
# In your Supabase project directory
supabase functions new delete-account
```

Replace the contents of `supabase/functions/delete-account/index.ts` with:

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const authHeader = req.headers.get('Authorization')
  if (!authHeader) {
    return new Response(JSON.stringify({ error: 'Missing auth' }), {
      status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }

  // Service-role client for admin operations
  const admin = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
  )

  // Verify the user's JWT and get their UID
  const token = authHeader.replace('Bearer ', '')
  const { data: { user }, error: getUserError } = await admin.auth.getUser(token)
  if (getUserError || !user) {
    return new Response(JSON.stringify({ error: 'Invalid auth' }), {
      status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }

  // Delete the auth row + cascade-delete any rows in your own tables that
  // reference this user (add your own cleanup here as the schema grows)
  const { error: deleteError } = await admin.auth.admin.deleteUser(user.id)
  if (deleteError) {
    return new Response(JSON.stringify({ error: deleteError.message }), {
      status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }

  return new Response(JSON.stringify({ ok: true, deletedUserId: user.id }), {
    status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' }
  })
})
```

Deploy:
```bash
supabase functions deploy delete-account
```

The function reads `SUPABASE_SERVICE_ROLE_KEY` from the function environment — no extra config needed since Supabase auto-injects it. Verify in dashboard: Edge Functions → delete-account → Settings.

The Kotlin side calls `${SUPABASE_URL}/functions/v1/delete-account` with the user's JWT, which is the standard URL for any Edge Function. No changes to `local.properties` needed.

---

## 4. Google Play Console — sensitive permission justifications

When you submit, Play asks you to justify each sensitive permission. Copy-paste-ready text:

### `BIND_NOTIFICATION_LISTENER_SERVICE`
> ThruSpark uses Notification Access to suppress notifications from apps not on a user-defined allowlist while a battery-saving profile is active. Notification content is read locally only and is never transmitted off-device or stored. The user explicitly grants this permission via Android system Settings during onboarding, and notification filtering only activates when a profile with Do-Not-Disturb is running.

### `FOREGROUND_SERVICE_SPECIAL_USE`
> Used by ProfileLifecycleService to maintain a persistent "active profile" status notification while a battery-saving profile is running, and to detect when the user dismisses the app from recents (`onTaskRemoved`) so paused apps automatically restore. The service runs only while a profile is active and stops the moment the profile deactivates.

Subtype value declared in manifest: `profile_lifecycle`

### `SCHEDULE_EXACT_ALARM`
> User-created wake-up alarms in the Alarms tab require precise scheduling so the alarm fires at the exact configured time. Each alarm is explicitly created by the user via the in-app Alarms tab; the app does not schedule alarms autonomously.

### `RECEIVE_BOOT_COMPLETED`
> Restores the user's previously-active battery-saving profile after device reboot, so they don't have to manually re-activate.

### `WRITE_SETTINGS`
> Applies user-configured screen brightness and screen timeout values when a battery-saving profile is activated. User explicitly grants via Settings during onboarding.

### `ACCESS_NOTIFICATION_POLICY`
> Toggles Do-Not-Disturb on/off when a battery-saving profile is activated/deactivated, per the profile's configuration.

### Shizuku / shell access
You don't request a sensitive permission for this, but Play reviewers may ask. Justification:
> ThruSpark optionally integrates with Shizuku (a separate, free, open-source app the user installs themselves) to enable advanced battery-saving capabilities like airplane mode toggling and per-app pause. Shizuku is not bundled with ThruSpark; the user installs it themselves and grants ThruSpark access via Shizuku's own permission UI. Without Shizuku, ThruSpark falls back to a smaller set of capabilities that don't require shell access.

---

## 5. Google Play — Data Safety form

Fill out the Data Safety section as follows.

**Data collection:**

| Data type | Collected? | Linked to user? | Optional? | Purpose |
|---|---|---|---|---|
| Email address | Yes | Yes | Yes (Skip option) | Account management, future cloud sync |
| Other user IDs | No | – | – | – |
| Photos / videos / audio / files | No | – | – | – |
| Location | No | – | – | – |
| Health & fitness | No | – | – | – |
| Financial info | No | – | – | – |
| Calendar | No | – | – | – |
| Contacts | No | – | – | – |
| App activity (installed apps list) | **Accessed locally, NOT collected** | – | – | – |
| Notifications content | **Accessed locally, NOT collected** | – | – | – |

**Data sharing with third parties:**
- Email address shared with Supabase (Auth provider). Document this in the Data Safety form's "Third parties" question.

**Security practices:**
- Data encrypted in transit: Yes (Supabase enforces HTTPS)
- Data encrypted at rest: Yes (Supabase manages storage encryption)
- User can request data deletion: Yes (in-app via Settings → Account → Delete account)
- User can request data export: Yes (in-app via Settings → Account → Export my data)

---

## 6. Apple App Store Connect — Privacy Nutrition Labels

When iOS lands and you submit:

**Data Linked to You:**
- Email Address (Account, App Functionality)

**Data Not Linked to You:**
- (none)

**Data Used to Track You:**
- (none) — ThruSpark does no advertising or cross-app tracking

---

## 7. Pre-submission checklist

Run through these BEFORE hitting submit on either store:

### Both stores
- [ ] `thebikemechanic.ca/privacy` returns 200 and serves real policy content (not "coming soon")
- [ ] `thebikemechanic.ca/terms` same
- [ ] Privacy policy explicitly mentions: email storage with Supabase, account deletion path, data export path, contact email for privacy requests
- [ ] App version (`versionCode` and `versionName` in `android/app/build.gradle.kts`) bumped from previous submission
- [ ] APK / AAB built in `release` mode with ProGuard/R8 enabled
- [ ] Tested on a real device that the release build installs and runs cleanly
- [ ] Account deletion flow tested end-to-end against deployed Edge Function
- [ ] Account creation → Sign-in → Sign-out → Delete account loop verified

### Google Play
- [ ] Data Safety form filled per section 5 above
- [ ] All sensitive permission justifications submitted per section 4
- [ ] Target SDK is at or above current Play requirement (currently 35; verify at https://support.google.com/googleplay/android-developer/answer/11926878)
- [ ] App signing key generated and uploaded to Play Console (see `Build-And-Sideload-v01.md` for keystore generation; `keytool -list -v -keystore ...` to extract SHA-256 for Play upload key)
- [ ] App Bundle (AAB) submitted, not APK
- [ ] Listed app category: **Tools** (or **Utilities** depending on Play taxonomy at submission time)

### Apple App Store
- [ ] Privacy Nutrition Labels filled per section 6
- [ ] Apple Developer Program membership active ($99/year)
- [ ] Sign in with Apple offered if any third-party sign-in is offered (we only do email/password, so NOT required for v0.4)
- [ ] App Tracking Transparency: NOT required since ThruSpark does no tracking — but if Apple's submission form asks, declare "Does not track"

---

## 8. Ongoing compliance

After launch:

- **Privacy policy version**: bump the "last updated" date on the policy any time you change what data is collected. Material changes (new data type, new third party) should trigger an in-app re-prompt for consent — out of scope for v0.4 but plan for v0.5.
- **DPA renewals**: Supabase's DPA is evergreen on Free tier; on Pro+, it renews with your subscription.
- **GDPR data subject requests**: PIPEDA requires response within 30 days, GDPR within 1 month. Set up a `privacy@thebikemechanic.ca` mailbox and document the process for handling deletion / export / access requests outside the in-app flows.
- **Breach notification**: GDPR requires notification within 72 hours of becoming aware. Have a plan, even an informal one, before launch.

---

## 9. What's NOT covered

This doc handles the v0.4-shipped compliance work and the immediate store-submission paperwork. Out of scope, plan for later:

- **Cookie consent** — N/A for the app, but if `thebikemechanic.ca` collects analytics, it needs its own cookie banner (separate from the app)
- **Children's online privacy (COPPA / GDPR-K)** — ThruSpark targets 16+ via the sign-up checkbox, so we don't need parental consent flows
- **Subscription / IAP compliance** — N/A for v0.4 (no monetization yet); revisit when/if a Pro tier ships
- **Google's Families policy** — N/A unless we ever target the Designed for Families program
- **Accessibility labels (Apple)** — should add `accessibilityLabel` to all controls before iOS submission
