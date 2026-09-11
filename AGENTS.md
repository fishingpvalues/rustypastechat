# AGENTS.md — RustyPaste Chat

Android chat app (Kotlin, Jetpack Compose, Material 3). Every message is a paste note on a [rustypaste](https://github.com/orhun/rustypaste) server; media shares inline; optional OpenAI-compatible AI replies.

## Build & test

All commands live in the `Makefile` (`make help`): `test`, `lint`, `debug`, `release`, `install`, `clean`, plus `emulator-start`/`emulator-wait`/`emulator-stop`, `logcat` and `rustypaste-start`/`rustypaste-stop`. There is no `make e2e` target - the emulator run is driven by hand, see the skill below.

- `make test` is self-contained and offline: `RustyPasteTestServer` is an in-process fake rustypaste on a free port. Run it before touching API code. The two live suites (`LiveRustyPasteContractTest`, `LlmIntegrationTest`) skip unless their env vars are set - see the contract section.
- Toolchain (this Mac): JDK 21 `/opt/homebrew/opt/openjdk@21`, Android SDK `/opt/homebrew/share/android-commandlinetools`, `adb` at `/opt/homebrew/bin/adb`, Gradle via `make`.

## Emulator facts

- AVD `MelanoScan_API35` (API 35), screen 1080×2400 @ 420dpi. Boot: `make emulator-start` + `make emulator-wait`, or run the emulator directly.
- The emulator reaches this Mac as `10.0.2.2`. Any host service the app must use needs to listen on `0.0.0.0` (not just loopback).
- Drive the UI with `adb shell input tap|text|keyevent`, `adb exec-out screencap -p` + read the PNG, `adb logcat`. For MCP-driven control see the E2E skill below.

## Real-server E2E (potatostack)

The canonical end-to-end test runs the app against the real potatostack rustypaste instance over an SSH tunnel. Full recipe: **skill `.agents/skills/rustypaste-emulator-e2e/SKILL.md`** — invoke it whenever asked to "test the app on the emulator", "e2e against potatostack", or "verify pastes on the real server".

- Credentials live in `local-potato.env` (gitignored). **Never commit tokens, SSH passwords, or LAN IPs.** If the file is missing, fetch fresh values from the potatostack host: `ssh daniel@<host> 'cat /mnt/ssd/docker-data/rustypaste/config.toml'` (docker container name: `rustypaste`, host port 8788 on loopback).
- App-side settings take `serverUrl` + `authToken`; connection test calls `GET /list`.

## API contract (rustypaste 0.18)

Re-verified against the live potatostack instance on 2026-09-11 by
`LiveRustyPasteContractTest`, which is the only thing that should be trusted
here - every earlier version of this table drifted, including a claim that the
server accepted unauthenticated uploads.

| Endpoint | No auth | With auth |
|---|---|---|
| `POST /` multipart field `file` | **401** | 200, body = plain-text paste URL |
| `GET /list` | **401** | 200, JSON `{file_name,file_size,item_type,creation_date_utc,expires_at_utc}` |
| `GET /{filename}` | **200** | 200 |
| `DELETE /{filename}` | 401 | 401 with the auth token; 200 with the *delete* token |
| `GET /version` | 404 | 404 - declared in `RustyPasteApi`, never called, do not build on it |

Auth header is `Authorization: <token>`, raw. A wrong token is refused exactly
like no token.

**`GET /{filename}` needs no token, and that is the app's whole threat model.**
Message bodies are readable by anyone who knows the name; the server's
6-character random filename is the only thing protecting one. Uploading and
listing need the token, so an attacker cannot enumerate - but do not describe
messages as private, and do not add a feature that puts a paste URL somewhere
public.

Run it against a real server (skipped without these):

```bash
ssh -fN -L 8788:127.0.0.1:8788 daniel@<potatostack>
RP_LIVE_URL=http://127.0.0.1:8788 RP_LIVE_TOKEN=... RP_LIVE_DELETE_TOKEN=... \
  ./gradlew testDebugUnitTest --tests '*LiveRustyPasteContractTest*'
```

The live LLM suite is opt-in the same way: `LLM_TEST_BASE_URL`,
`LLM_TEST_API_KEY`, `LLM_TEST_MODELS`. **Never hardcode a key as a fallback** -
one sat in `LlmIntegrationTest` in this public repo for months.

## Conventions

- Tests: `app/src/test/java/com/rustypastechat/...` mirrors main packages. API-level tests use `RustyPasteTestServer` (in-process); repository tests use `PasteRepositoryHelper`.
- Settings persistence: `PreferencesManager` (DataStore) + `SecurePreferences` for the auth token.
- Release signing: `local.properties` locally, repo secrets in CI; debug-signing fallback exists for local builds only.
