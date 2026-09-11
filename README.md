# rustypaste-chat

An Android chat client for a [rustypaste](https://github.com/orhun/rustypaste)
server you run yourself.

Every message is a paste. Sending writes a file to your rustypaste instance and
reading a conversation is a `GET /list` plus one fetch per paste. There is no
account, no backend of ours, and no database anywhere but your own server.

That design has a consequence worth reading before you install it, not after:
**messages are not private.** rustypaste serves `GET /{filename}` to anyone,
with no token. Uploading and listing require your auth token, so nobody can
enumerate your pastes, but the 6-character random filename is the only thing
protecting a message that someone has the URL to. Nothing is end-to-end
encrypted. Treat it as a shared notes channel between your own devices, not as
a replacement for Signal.

## Requirements

- Android 8.0 (API 26) or newer
- A rustypaste instance with `auth_tokens` set, reachable from the phone
- Optional: any OpenAI-compatible endpoint for the AI replies

## Install

Download the APK from
[releases](https://github.com/fishingpvalues/rustypastechat/releases), or build
it (below). Then Settings -> Connection: server URL and auth token, and use
Test Connection, which calls `GET /list` and tells you which of the two is
wrong.

Set `auth_tokens` on the server. Without it anyone who can reach the host can
write to your chat.

## What it does

| | |
|---|---|
| Chat | Threads, drafts, delivery status, date headers, markdown |
| Media | Images, video, files, voice notes, inline previews, link previews |
| AI | Streaming replies from any OpenAI-compatible endpoint |
| Sync | Background poll on an interval, with notifications |
| Import | WhatsApp chat exports |
| Backup | Encrypted archive, uploaded over SFTP to a host you pin |
| Lock | Biometric app lock with a timeout |
| Theme | Material 3, light/dark, dynamic color on Android 12+ |

## Security

What is protected and what is not:

- The auth token and the LLM API key are in `EncryptedSharedPreferences`;
  cached message bodies and backup archives are `EncryptedFile`. Both are
  bound to a key in the Android Keystore.
- **Android backup is off.** The Keystore key is never part of a backup, so a
  restore onto a new device would hand the app ciphertext it cannot read, and
  DataStore holds unsent drafts, which are message text. `res/xml/data_extraction_rules.xml`
  carries the reasoning. Use the app's own SFTP backup to move chats.
- SFTP refuses to upload until you pin the server's host key. `StrictHostKeyChecking`
  is on and there is no override; Test Connection shows you the fingerprint to pin.
- Cleartext HTTP is allowed app-wide, because a self-hosted rustypaste on a LAN
  usually has no TLS and the host is configured at runtime, so it cannot be
  narrowed to an allowlist. On an untrusted network, put the server behind TLS
  or a VPN. The token travels in a header, so cleartext exposes it.
- The SFTP password is never stored. You type it per upload.

Report a vulnerability through
[private advisories](https://github.com/fishingpvalues/rustypastechat/security/advisories/new).

## Build

```bash
# JDK 17+, Android SDK 36
echo "sdk.dir=$ANDROID_HOME" > local.properties
make debug      # assembleDebug
make install    # adb install
make test       # unit tests, offline
make lint
```

`make release` builds a signed release APK. Signing comes from environment
variables in CI or `local.properties` locally, never from committed source. If
neither is set the release build falls back to **debug signing** so it still
compiles; that output is for local testing and nothing else.

```properties
release.storeFile=/path/to/your.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```

CI reads `RUSTYPASTECHAT_KEYSTORE_BASE64`, `RUSTYPASTECHAT_KEYSTORE_PASSWORD`,
`RUSTYPASTECHAT_KEY_ALIAS`, `RUSTYPASTECHAT_KEY_PASSWORD`. Keep the keystore
forever: Play requires the same key for every update.

## Tests

`make test` is hermetic. API tests run against `RustyPasteTestServer`, an
in-process fake, so the suite needs no network and no server.

A fake only proves the client and the fake agree. Two suites check the real
thing and are skipped unless you point them at one:

```bash
# rustypaste: upload, list, download, delete, and the auth boundary
RP_LIVE_URL=http://127.0.0.1:8788 RP_LIVE_TOKEN=... RP_LIVE_DELETE_TOKEN=... \
  ./gradlew testDebugUnitTest --tests '*LiveRustyPasteContractTest*'

# any OpenAI-compatible endpoint
LLM_TEST_BASE_URL=https://host/v1 LLM_TEST_API_KEY=... LLM_TEST_MODELS=a,b \
  ./gradlew testDebugUnitTest --tests '*LlmIntegrationTest*'
```

Never put a key in the test as a fallback. One lived in `LlmIntegrationTest` in
this public repository for months before an audit found it.

## Architecture

```
app/src/main/java/com/rustypastechat/
  data/
    api/         Retrofit + OkHttp (RustyPasteApi, OpenAiApi, auth interceptor)
    backup/      encrypted archive + SFTP upload with host-key pinning
    local/       DataStore settings, chat metadata
    model/       Message, PasteItem, ChatThread, LLM models
    repository/  PasteRepository, LlmRepository, LinkPreviewRepository
    sync/        WorkManager poll, notifications
    whatsapp/    chat-export importer
  security/      Keystore-backed stores, biometric lock, encrypted cache
  ui/            Compose screens: chat, chat list, settings, onboarding, lock
```

Kotlin, Jetpack Compose, Material 3, Hilt, WorkManager, DataStore, Retrofit,
Coil, kotlinx.serialization.

## Provenance

Written with AI assistance (Claude Code), by one maintainer. Saying so is the
thing you would otherwise have to guess at.

What that sits on top of: a unit suite that runs offline on every build,
contract tests that run against a real rustypaste rather than only the fake,
and commits that explain why rather than what. The audit that produced the
current security section is in the history, findings included - it removed a
committed API key, turned off a backup path that would have broken the app on
restore, and deleted crypto helpers whose key never reached the Keystore their
name claimed.

Limits that process does not fix: one maintainer, so the bus factor is one, and
no end-to-end encryption, which is a property of building on rustypaste rather
than an omission to be patched.

## License

MIT. rustypaste itself is by [orhun](https://github.com/orhun/rustypaste).
