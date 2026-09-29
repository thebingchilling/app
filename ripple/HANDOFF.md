# Ripple — handoff for the next agent

Read this first, then `/CLAUDE.md` (repo rules: commit and push straight to
`main`, no branches, no PRs).

## Goal (confirmed by the user)

**Ripple** = the Ltt.rs Android UI kept as it is (Java, XML views, Material 3),
with the JMAP protocol removed and replaced by an open-source IMAP/POP3/SMTP
engine (Thunderbird for Android's), plus OAuth for Gmail/Outlook and
iOS-like instant delivery. Android only. Name chosen by the user: Ripple.

## State (2026-09-29)

Sources: Ltt.rs `codeberg.org/iNPUTmice/lttrs-android` @ `d950cf9`;
Thunderbird for Android @ `0e1137f` (see `engine/README.md`).

Done and verified:
- `./gradlew :app:assembleDebug` / `:app:assembleRelease` build
  (`app.ripple.mail`, label "Ripple", minSdk 26, target 36).
- `./gradlew :app:testDebugUnitTest`: Ltt.rs' own tests plus
  `engine/EngineIntegrationTest` (Robolectric + GreenMail: sync, threading,
  flags, trash/archive/inbox moves, drafts, folder copy, SMTP send, on-demand
  attachment fetch, local search, IMAP IDLE push, login check),
  `engine/AutoconfigTest`, and `ui/UiSmokeTest` (setup screens and an inbox
  synced from GreenMail rendered by the real `LttrsActivity`).
- JMAP, Autocrypt, WebPush/UnifiedPush/Firebase removed. The data classes the
  UI uses were copied from `jmap-common`/`jmap-mua-util` into
  `rs.ltt.android.mail` (Lombok expanded with delombok, so no Lombok).
- New Room schema version 1 for both databases (fresh app, no migrations).

How it fits together:
- `engine/Mua.kt` offers what the UI's workers/repositories call
  (`query`, `setKeyword`, `archive`, `moveToTrash`, `send`, `draft`, ...).
- `RoomBackendStorage` implements Thunderbird's `BackendStorage`; mailbox id =
  folder server id; email id = `<hash(folder)>-<UID>` (`MailIds`).
- `EngineDao.saveEmail` threads by Message-ID/In-Reply-To/References and
  merges threads when a missing link arrives.
- `QueryEngine` evaluates the UI's `EmailQuery` filters over the local DB and
  writes `query`/`query_item` rows (JMAP servers used to do that).
- Local actions keep Ltt.rs' "overwrite" tables: workers call `Mua`, which
  does the IMAP command, updates Room, then `EngineDao.confirmOverwrites`.
- `push/PushService` (foreground service, type specialUse) runs Thunderbird's
  `ImapBackendPusher` per IMAP account; push events sync the inbox and post
  notifications via Ltt.rs' `EmailNotification`. `PushController` also keeps
  a 15-min `MainMailboxQueryRefreshWorker` as fallback.
- Setup: `SetupViewModel` → `Autoconfig.discover` → OAuth (AppAuth, if the
  provider has a client id) or password → `Mua.checkSettings` → insert.
  Manual settings: `ServerSettingsFragment`.

## Open items

- Never run on a device (no KVM here). First real-device test should cover:
  Gmail app password, Outlook, a Dovecot server, IDLE across Doze and network
  changes, notifications, attachments, compose/reply.
- OAuth needs client ids (`RIPPLE_GOOGLE_CLIENT_ID`, `RIPPLE_MICROSOFT_CLIENT_ID`,
  see README); the flow is untested end to end without them.
- HTML-only mail is converted to text (`MessageMapper.htmlToText`); a WebView
  renderer would be the next UI improvement.
- Search is local only (synced mail). IMAP `SEARCH` via `Backend.search` could
  be added to `Mua.query` for text queries.
- Gmail shows a message once per label folder the user opens (no
  X-GM-MSGID de-duplication yet); threads still group them.
- R8 is off for release builds (no device to catch reflection breakage).
- Old Ltt.rs strings about JMAP/Autocrypt remain in `strings.xml` (unused).

## Environment gotchas

- Same as Pebble: the agent proxy returns HTTP 429 from Maven Central; use
  `~/.gradle/init.d/mirror.gradle` pointing at
  `https://maven-central.storage-download.googleapis.com/maven2/` (not committed).
- Android SDK was installed to `/opt/android-sdk` (`local.properties` has
  `sdk.dir`, not committed). Java 21.
- Run Gradle in the background; the user interrupts long foreground commands.
