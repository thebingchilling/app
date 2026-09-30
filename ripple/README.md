# Ripple

A Material 3 email app for Android. Ripple keeps the Gmail-style interface of
[Ltt.rs](https://codeberg.org/iNPUTmice/lttrs-android) and replaces its JMAP
layer with the IMAP, POP3 and SMTP engine of
[Thunderbird for Android](https://github.com/thunderbird/thunderbird-android)
(formerly K-9 Mail). Mail arrives instantly through IMAP IDLE.

<p align="center">
<img src="docs/inbox.png" width="240"/>
<img src="docs/setup_server_settings.png" width="240"/>
</p>

(Rendered by `UiSmokeTest` from mail synced off a local IMAP server.)

## Features

- **Any IMAP or POP3 account** with SMTP for sending. The address is enough in
  most cases: settings come from a built-in list (Gmail, Outlook.com, Yahoo,
  iCloud, AOL), Thunderbird's ISP database, the provider's own autoconfig
  file, or the MX record. Manual server settings are always available.
- **Sign in with Google or Microsoft** (OAuth 2.0, XOAUTH2) when the build has
  OAuth client ids (see below). Without them, Gmail and Outlook work with an
  app password.
- **Instant delivery.** A foreground service keeps one IMAP IDLE connection
  per account and syncs the inbox the moment the server reports new mail, then
  posts a notification. A 15-minute background refresh covers POP3 accounts,
  servers without IDLE, and times the service is not running.
- **Gmail-like conversations.** Messages are grouped into threads by
  Message-ID, In-Reply-To and References across all folders, so your replies
  from the Sent folder appear in the conversation.
- Everything Ltt.rs offers: archive, trash, labels (IMAP folders; real labels
  on Gmail), stars, read state, search, compose with attachments, drafts,
  multiple accounts, dynamic colour, dark theme.

Not included (yet): end-to-end encryption (Ltt.rs' Autocrypt was built on
JMAP), HTML rendering (HTML-only mail is shown as text), server-side search.

## Layout

| Path | What |
|---|---|
| `app/` | The Ltt.rs app (Java, XML views, Room). Package names stay `rs.ltt.android`; the application id is `app.ripple.mail`. |
| `app/src/main/java/rs/ltt/android/engine/` | Ripple's glue (Kotlin): `Mua` (the operations the UI asks for), `RoomBackendStorage` (lets Thunderbird's sync write into Room), `MessageMapper`, `QueryEngine` (answers the UI's queries locally), `MimeComposer`, `Autoconfig`, `OAuth`. |
| `app/src/main/java/rs/ltt/android/push/` | `PushService` (IMAP IDLE foreground service), `PushController`, `BootReceiver`. |
| `app/src/main/java/rs/ltt/android/mail/` | Plain data classes and helpers the UI uses, taken from Ltt.rs' `jmap-common`/`jmap-mua-util` libraries (Lombok expanded). |
| `engine/` | Thunderbird for Android's `mail/*` and `backend/*` modules, vendored. See `engine/README.md`. |

## Building

Needs JDK 21 and the Android SDK (platform 36).

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # includes tests against a real IMAP/SMTP server (GreenMail)
```

CI (`.github/workflows/build-ripple.yml`) runs the tests and uploads a release
APK as the `Ripple-apk` artifact. It signs with the `RIPPLE_KEYSTORE_*`
secrets, else Tidewall's key, else a throwaway debug key.

### Sign in with Google / Microsoft (OAuth)

Gmail works with an app password, but Outlook.com/Hotmail/Live accounts no
longer accept passwords in mail apps at all: they need "Sign in with
Microsoft". Those buttons appear only in builds that carry OAuth client ids,
which you register once (free) and store as repository secrets. CI then
builds them into every APK.

**Microsoft (Outlook.com, Hotmail, Live, Microsoft 365)**

1. Go to <https://entra.microsoft.com> → *Identity* → *Applications* →
   *App registrations* → *New registration* (a personal Microsoft account
   works).
2. Name: `Ripple`. Supported account types: *Accounts in any organizational
   directory and personal Microsoft accounts*.
3. Redirect URI: platform *Public client/native (mobile & desktop)*, value
   `app.ripple.mail://oauth2redirect`. Register.
4. Copy the *Application (client) ID* (a GUID).
5. Optional: *API permissions* → add *Office 365 Exchange Online* delegated
   `IMAP.AccessAsUser.All`, `POP.AccessAsUser.All`, `SMTP.Send` (Ripple also
   asks for them at sign-in, so this only pre-lists them).

**Google (Gmail, Google Workspace)**

1. <https://console.cloud.google.com> → create a project → *APIs & Services*
   → *OAuth consent screen* (*Google Auth Platform*): user type *External*,
   app name `Ripple`, your email as support and developer contact.
2. *Data access* → *Add or remove scopes* → add `https://mail.google.com/`.
3. *Audience*: while the app is in *Testing*, add your Gmail address(es) as
   test users; Google then expires the sign-in every 7 days (Ripple shows a
   "Sign in again" notification). Clicking *Publish app* avoids that: you get
   an "unverified app" warning at sign-in (*Advanced* → *Go to Ripple*) and a
   limit of 100 users, which is fine for personal use.
4. *Clients* → *Create client* → Application type **iOS**, Bundle ID
   `app.ripple.mail`. (iOS clients use the redirect Ripple expects,
   `com.googleusercontent.apps.<id>:/oauth2redirect`, and need no
   signing-key fingerprint. An *Android* client works too if you enable
   *Custom URI scheme* under its advanced settings.)
5. Copy the client ID (`1234-abc.apps.googleusercontent.com`).

**Put them into the build**

GitHub → this repository → *Settings* → *Secrets and variables* →
*Actions* → *New repository secret*:

| Secret | Value |
| --- | --- |
| `RIPPLE_MICROSOFT_CLIENT_ID` | the Microsoft application (client) ID |
| `RIPPLE_GOOGLE_CLIENT_ID` | the Google client ID |

Then *Actions* → *Build Ripple* → *Run workflow*, and install the new
`Ripple-apk`. For local builds put the same lines in `local.properties` or
the environment.

When a server later rejects a saved login (password changed, sign-in expired
or revoked), Ripple posts a "Sign in to … again" notification; tapping it
replaces the login of that account without removing it.

## Licences

Apache License 2.0 (see `LICENSE`). Ripple is based on Ltt.rs by Daniel
Gultsch and on Thunderbird for Android / K-9 Mail by the Thunderbird and K-9
Mail contributors, both Apache-2.0. Ripple is not affiliated with either
project.
