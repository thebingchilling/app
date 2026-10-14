# Wren: plan

*Wren is a working name; see Open questions.*

A Gmail-style, Material 3 email app for Android that gets new mail **instantly**
(push) for **Outlook.com and Gmail**, built from Thunderbird for Android.

Read `/CLAUDE.md` first. Repo rules: commit and push straight to `main`, no
branches, no PRs.

## Why this exists (user's words, condensed)

- The user likes Samsung Email because new mail arrives straight away, not on
  a sync timer.
- The user likes the **Gmail app's UI**. But the Gmail app only polls Outlook
  accounts added as IMAP (every 15 minutes or so), so it can't do instant
  Outlook mail.
- Goal: Gmail's look and feel, plus push for both accounts.

### Why not patch Samsung Email or take its engine (decided 2026-10-14)

- **No source code.** Samsung Email is closed source, and its code is
  obfuscated by R8. A new UI would mean rewriting tens of thousands of
  lines of smali, and every Samsung update would undo it.
- **A re-signed APK breaks sign-in.** Samsung's Google and Microsoft OAuth
  clients are tied to Samsung's package name and signing key. A modified APK
  has to be re-signed, so Gmail and Outlook sign-in would stop working. It
  would also lose its signature-protected links to One UI (Samsung account,
  Knox, shared providers).
- **Licence.** Samsung's terms don't allow modifying or redistributing the
  app.
- **Theming tools only change colours.** Good Lock / Theme Park can recolour
  Samsung Email but can't change its layout. Runtime overlays need root.
- **The "engine" is not special.** For Gmail, Samsung Email gets push through
  IMAP IDLE. For Outlook.com it uses Exchange ActiveSync. Thunderbird already
  does IMAP IDLE push, and both Gmail and Outlook.com support it.
  Samsung Email's ActiveSync code descends from AOSP's Exchange app, which is
  open (Apache-2.0) but unmaintained since about Android 7. It's a possible
  later route if ActiveSync is ever needed (calendar or contacts sync). It
  isn't needed for instant mail.

## Upstream baseline

- **Thunderbird for Android 24.0:** tag `THUNDERBIRD_24_0`, commit
  `73a7b7fffcaed6db51c5b0aaa90e4ee012549563` (2026-10-05). Licence Apache-2.0.
  About 107 MB of source without git history.
- **Vendoring:** copy the whole tree into `wren/`. The first commit is upstream
  unmodified, as Lychee did with HeliBoard, so `git diff <that commit>` always
  shows exactly what Wren changed.

What the upstream code already gives us (checked in the source):

| Area | Where | State in 24.0 |
| --- | --- | --- |
| White-label apps | `app-thunderbird/`, `app-k9mail/` | Each app provides its own theme, OAuth config, name, icons and feature flags. Everything else is shared. **Wren becomes a third app module.** |
| Compose design system ("Bolt") | `components/ui/bolt/` | Compose **Material 3**, with themes `thunderbird`, `k9mail` and `default`. No dynamic colour. |
| Legacy View theme | `core/ui/legacy/theme2/` | Already `Theme.Material3.*`. |
| New Compose message list | `feature/mail/message/list/internal/` | Built, behind flags `enable_message_list_new_state` and `use_compose_for_message_list_items` (both off). |
| New Compose reader | `feature/mail/message/reader/impl/` | Behind flag `use_compose_for_message_reader` (off). |
| Compose drawer | `feature/navigation/drawer/dropdown/` | In use. |
| Still legacy Views | `legacy/ui/legacy/` | `MessageListFragment.kt` (2.7k lines), `MessageViewFragment.kt` (1.3k), `activity/MessageCompose.java` (2.2k), most settings. |
| Push (IMAP IDLE) | `backend/imap/` (`ImapBackendPusher`, `ImapFolderPusher`, `BackendIdleRefreshManager`); `legacy/core/.../controller/push/` (`PushController`, foreground `PushService`, `AlarmPermissionManager`) | Works. Push is **off per folder by default** (`FolderSettingsProvider`: `pushEnabled` defaults to false). IDLE refresh is set per account (minimum 2 min). |
| OAuth | `app-thunderbird/src/*/.../TbOAuthConfigurationFactory.kt` | Google and Microsoft client IDs are registered to Thunderbird's package name and signing key, so **we need our own** (Phase 0). |
| Feature flags | `config/featureflag/thunderbird_mobile_featureflag.catalog.json`, app `*Overrides` | Wren turns on the new Compose screens in its own overrides. |
| Flavours | `foss` / `full` | Wren uses **foss** only: no Google Play funding and no Glean telemetry. |

## How the fork is organised

- `wren/app-wren/`: Wren's app module, started as a copy of
  `app-thunderbird`. It holds the app ID `app.wren.android`, name, icon,
  OAuth config, theme providers, feature-flag overrides and push defaults.
- `wren/feature/wren/...` (or similar): Wren's own Compose UI. Gmail-style
  composables live here wherever upstream offers a seam (theme provider,
  feature theme, item renderers), so upstream files change as little as
  possible.
- **Edits to upstream files** get a `// Wren:` comment and are listed in
  `HANDOFF.md`, just as Lychee does.
- **Removed from the app:** Thundermail onboarding, funding prompts,
  telemetry, the "migrate from Thunderbird" launcher, and the K-9 Mail app.
  Delete `app-k9mail` only if that doesn't mean editing shared build logic.
- **Upstream updates:** `wren/UPSTREAM` records the tag and commit. To update,
  apply the diff between the old and new tags with
  `git diff THUNDERBIRD_24_0 THUNDERBIRD_25_0 | git apply -3 --directory=wren`,
  then fix conflicts, build and test. Do this once per Thunderbird release.
- **CI:** `.github/workflows/build-wren.yml` builds a signed `fossRelease`
  APK on every push to `wren/**`, the same way as `build-lychee.yml`.
- **Signing secrets:** `WREN_KEYSTORE_BASE64`, `WREN_KEYSTORE_PASSWORD`,
  `WREN_KEY_ALIAS`, `WREN_KEY_PASSWORD`. These are required, with no fallback
  to a throwaway key, because the Microsoft redirect URI contains the
  signing key's hash.

## Phases

Each phase ends with a CI-built APK the user installs on their Samsung phone.
There's no emulator in the agent container, so the user's phone is the real
test.

### Phase 0: keys and sign-in registrations (user, with step-by-step help)

1. **Signing key:** generate one keystore and add it as GitHub secrets. It
   must never change after Phase 1: changing it breaks Outlook sign-in and
   forces a reinstall.
2. **Microsoft (Outlook.com):** register an app in the Microsoft Entra admin
   centre. It's free and needs no review for personal accounts.
   - Supported accounts: personal Microsoft accounts and any organisation.
   - Platform: Android, package `app.wren.android`, plus the signature hash
     of the key from step 1. This produces
     `msauth://app.wren.android/<hash>`.
   - Delegated permissions: `IMAP.AccessAsUser.All`, `SMTP.Send`,
     `offline_access`, `openid`, `email`, `profile`.
   - Outlook.com no longer accepts passwords from outside apps, so OAuth is
     the only way in.
3. **Google (Gmail):** create a Google Cloud project and set up the OAuth
   consent screen with type External.
   - The scope `https://mail.google.com/` is *restricted*.
   - Set the publishing status to **"In production" but unverified**. Users
     click through a "Google hasn't verified this app" screen, and there's a
     cap of 100 users, which is fine for personal use.
   - **Don't leave it in "Testing":** in that mode refresh tokens expire
     after 7 days, which means signing in again every week.
   - Client: Android type, with the package name and the SHA-1 of the key
     from step 1. Confirm while setting it up that the custom URI scheme
     redirect (`app.wren.android:/oauth2redirect`) is allowed for the client
     type we choose.
   - **Fallback:** a Gmail app password (needs 2-Step Verification) over
     plain IMAP/SMTP. This skips all of the Google Cloud setup.

### Phase 1: fork, rebrand, sign in

- Vendor upstream, in a separate commit and unmodified. Add `app-wren`,
  app ID `app.wren.android`, the name, a placeholder icon, and the OAuth
  config from Phase 0. Strip the items under "Removed from the app". Add CI.
- **Done when:** Wren installs next to Thunderbird and Samsung Email, signs
  into both Outlook.com and Gmail, and sends and receives mail.

### Phase 2: instant mail by default (the reason for the project)

- **New IMAP accounts:** Inbox push on. IDLE refresh every 15 min, since
  Outlook drops quiet connections. Background polling every 60 min as a
  safety net only. Background sync set to "Always".
- **"Instant delivery" setup step**, after adding the first account:
  - Notification permission.
  - Exact-alarm permission (the IDLE refresh needs it).
  - Battery-optimisation exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
    allowed because Wren is sideloaded, not on Play).
  - On Samsung phones (`Build.MANUFACTURER`), a one-tap jump to the app's
    battery page, plus a short card explaining *Never sleeping apps*. Apps
    can't add themselves to that list.
- **Ongoing "listening" notification:** put it on its own channel at
  importance MIN, so no status-bar icon shows.
- **"Delivery status" screen** for each account: connected, idle since, last
  new mail, last error. This makes "mail was late" debuggable.
- **Done when:** on the user's phone, with the screen off for an hour, test
  mails to both accounts notify within about 10 s. Test 5 times per
  account, and repeat after a reboot.

### Phase 3: Material 3 / Gmail look foundation

- **New Bolt theme `wren`:** dynamic colour (Material You, Android 12+). The
  fallback is a neutral tonal palette close to Gmail's. Also Gmail-like
  shape scale (pill search bar, large rounded cards and sheets) and
  typography.
- **Font:** Roboto Flex or the system font, because Google Sans isn't
  licensed for apps. Light and dark.
- **Legacy View screens:** apply `DynamicColors` and the same tokens, so
  settings and other old screens match.
- **Done when:** every screen follows the wallpaper colours, in light and
  dark.

### Phase 4: Inbox (the main screen)

- Turn on upstream's Compose message list in `app-wren`'s flag overrides,
  then fix whatever is incomplete there before restyling.
- **Rows:** avatar (contact photo or coloured letter), sender (bold when
  unread), subject, one-line snippet, time, star toggle, attachment chips,
  thread count.
- **Top bar:** "Search in mail" pill, with the menu button on the left and
  the account avatar on the right.
- **Compose button:** extended FAB ("✏ Compose") that shrinks to an icon
  while scrolling.
- **Swipe:** archive and delete, with Gmail-style coloured backgrounds;
  configurable.
- **Selection:** tap the avatar to select, which flips it to a check mark and
  shows a contextual top bar.
- **Other:** pull to refresh, empty states, and "All inboxes" (upstream's
  unified inbox).

### Phase 5: drawer and account switching

- **Drawer:** the app name at the top, then All inboxes, Inbox, Starred,
  Sent, Drafts, Outbox, All mail, Spam, Bin. Below that, a "Labels" heading
  for every other folder, then Settings. The selected item is a filled pill,
  and unread counts are shown.
- **Gmail folders:** map Gmail's `[Gmail]/…` folders and Outlook's
  special-use folders to the same names and icons.
- **Account switcher:** tapping the avatar opens a Gmail-style sheet listing
  accounts with unread counts. Swiping down on the avatar switches account.

### Phase 6: reading a conversation

- Use upstream's Compose reader if it's usable, otherwise restyle
  `MessageViewFragment`.
- **Header:** subject, folder chip and star.
- **Message cards:** avatar, sender, "to me ▾" (expands the details), time,
  Reply and ⋮.
- **Older messages:** collapsed behind an "N older messages" pill.
- **Bottom row:** Reply / Reply all / Forward as outlined pill buttons.
- **Body:** HTML with proper dark mode, using upstream's new reader CSS.
- **Attachments:** cards with thumbnails.
- **Stretch:** Gmail-accurate threading using `X-GM-THRID` for Gmail
  accounts.

### Phase 7: writing mail

- **Layout:** Gmail-style From, To/Cc/Bcc as chips, Subject, then the body.
  Top bar has attach, send and ⋮. Leaving the screen saves a draft.
- **Approach:** first restyle `MessageCompose`'s layouts and keep its proven
  logic (encryption, drafts, identities). A full Compose rewrite comes later
  and only if needed.

### Phase 8: search, notifications, settings polish

- **Search:** a search screen with recent searches and filter chips (from,
  has attachment, date). Use server search for IMAP.
- **Notifications:** sender photo, plus Archive / Reply / Mark read actions
  (upstream has the actions).
- **Settings:** restyle to M3 list style, with the most-used options first.

### Later

- Two-pane layout for tablets and foldables (Bolt has adaptive layouts).
- A widget restyle.

## Not doing (and why)

- **Exchange ActiveSync:** IMAP IDLE already gives instant mail for
  Outlook.com and Gmail.
- **Snooze, schedule send, smart compose, Gmail categories as tabs:** these
  are Gmail server features with no IMAP equivalent.
- **Google's name, logo, icons or the Google Sans font:** the layout is
  Gmail-*style*; the branding is Wren's own.

## Verification

- **Each phase:**
  - `./gradlew :app-wren:assembleFossRelease`.
  - Upstream unit tests for every module Wren touched.
  - Wren's own tests: push defaults, OAuth config, list-row formatting.
- **Screenshot tests (Roborazzi, Wren modules only):** they render Wren's
  Compose screens in light and dark, so CI can show the user the UI before
  anything is installed. Upstream doesn't use a screenshot tool; adding one
  only in our modules leaves upstream untouched.
- **On the phone (user):** a short checklist per phase in `HANDOFF.md`.
  Phase 2's push timing test is the one that matters most.

## Risks

- **Upstream is mid-rewrite to Compose.** Our edits to its new screens will
  conflict with later releases. To limit that, keep Gmail styling in Wren's
  theme and Wren-owned composables, and sync on every release rather than
  rarely.
- **Samsung's battery management** can still kill push despite Phase 2. The
  "Delivery status" screen shows when that happens; the fix is the
  *Never sleeping apps* setting.
- **Google OAuth policy** for restricted scopes can change. The app-password
  fallback keeps Gmail working.

## Open questions for the user

1. **Name and icon.** "Wren" is a placeholder. The name is cheap to change
   until Phase 0 registers the app ID with Microsoft and Google.
2. **Gmail sign-in:** your own Google OAuth app (one-time Cloud console
   setup, then normal "Sign in with Google") or a Gmail app password
   (no setup, but you create the password once)?
3. **Phone model and One UI version**, for Samsung-specific battery
   instructions.
