# Vendored mail engine

These sources come from [Thunderbird for Android](https://github.com/thunderbird/thunderbird-android)
(Apache License 2.0), revision `0e1137f45d19ff4b730d39088b9e8e9bf9062db6`
(2026-09-29):

- `mail/common`, `mail/protocols/imap`, `mail/protocols/pop3`, `mail/protocols/smtp`
- `backend/api`, `backend/imap`, `backend/pop3`

Local changes, kept small so updates can be diffed:

- Removed `BackendFactory`, `BackendStorageFactory`, `RemoteFolderCreator` and
  `ImapRemoteFolderCreator` (they depend on Thunderbird's account modules).
- `net/thunderbird/...`: minimal copies of the few types the engine needs from
  Thunderbird's `core/common` and `feature/mail/folder/api` (`MessagingException`,
  `Flag`, `FolderPathDelimiter`, `FolderServerId`, `rootCauseMessage`), and a
  `Log` facade that writes to SLF4J instead of Thunderbird's logging modules.
- `CommandRefreshFolderList` logs through that `Log` facade.

To update: copy the same directories from a newer revision over
`src/main/java`, re-apply the changes above, and run `./gradlew :app:testDebugUnitTest`.
