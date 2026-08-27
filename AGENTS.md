# AGENTS.md — RustyPaste Chat

Android chat app (Kotlin, Jetpack Compose, Material 3). Every message is a paste note on a [rustypaste](https://github.com/orhun/rustypaste) server; media shares inline; optional OpenAI-compatible AI replies.

## Build & test

All commands live in the `Makefile` (`make help`): `test`, `lint`, `debug`, `release`, `install`, `e2e`.

- Unit tests are self-contained: `RustyPasteTestServer` runs an in-process fake rustypaste on a free port. No external services needed. Run `make test` before touching API code.
- Toolchain (this Mac): JDK 21 `/opt/homebrew/opt/openjdk@21`, Android SDK `/opt/homebrew/share/android-commandlinetools`, `adb` at `/opt/homebrew/bin/adb`, Gradle via `make`.

## Emulator facts

- AVD `MelanoScan_API35` (API 35), screen 1080×2400 @ 420dpi. Boot: `make emulator-start` + `make emulator-wait`, or run the emulator directly.
- The emulator reaches this Mac as `10.0.2.2`. Any host service the app must use needs to listen on `0.0.0.0` (not just loopback).
- Drive the UI with `adb shell input tap|text|keyevent`, `adb exec-out screencap -p` + read the PNG, `adb logcat`. For MCP-driven control see the E2E skill below.

## Real-server E2E (potatostack)

The canonical end-to-end test runs the app against the real potatostack rustypaste instance over an SSH tunnel. Full recipe: **skill `.agents/skills/rustypaste-emulator-e2e/SKILL.md`** — invoke it whenever asked to "test the app on the emulator", "e2e against potatostack", or "verify pastes on the real server".

- Credentials live in `local-potato.env` (gitignored). **Never commit tokens, SSH passwords, or LAN IPs.** If the file is missing, fetch fresh values from the potatostack host: `ssh daniel@<host> 'cat /mnt/ssd/docker-data/rustypaste/config.toml'` (docker container name: `rustypaste`, host port 8788 on loopback).
- App-side settings take `serverUrl` + `authToken`; connection test calls `GET /list`.

## API contract (rustypaste 0.18, verified live 2026-08-27)

| Endpoint | Behavior |
|---|---|
| `POST /` multipart field `file` | Upload; 200 body = plain-text paste URL |
| `GET /list` | JSON array `{file_name,file_size,item_type,creation_date_utc,expires_at_utc}` |
| `GET /{filename}` | Download content |
| `DELETE /{filename}` | Requires delete token; 404 if none configured on server |
| `GET /version` | 404 on potatostack instance. Declared in `RustyPasteApi` but never called — do not build features on it |

Auth header is `Authorization: <token>` (raw token; `Token <token>` also accepted by the running instance). Note: the running potatostack container currently accepts uploads **without** auth — it was started before its config gained tokens; treat unauthenticated success as an environment quirk, not a spec.

## Conventions

- Tests: `app/src/test/java/com/rustypastechat/...` mirrors main packages. API-level tests use `RustyPasteTestServer` (in-process); repository tests use `PasteRepositoryHelper`.
- Settings persistence: `PreferencesManager` (DataStore) + `SecurePreferences` for the auth token.
- Release signing: `local.properties` locally, repo secrets in CI; debug-signing fallback exists for local builds only.
