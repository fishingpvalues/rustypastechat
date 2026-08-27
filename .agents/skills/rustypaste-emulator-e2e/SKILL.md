---
name: rustypaste-emulator-e2e
description: End-to-end test RustyPaste Chat on the local Android emulator against the real potatostack rustypaste server over an SSH tunnel. WHEN: "test the app on the emulator", "e2e test rustypastechat", "run against potatostack", "verify pastes land on the real server", "drive the android app", "check message status/checkmarks".
---

# RustyPaste Chat → potatostack E2E

Full loop: tunnel → emulator → build → install → drive UI → verify bytes on the real server → fix → repeat until green.

## 0. Preconditions

- Load creds: `source local-potato.env` (repo root, gitignored). Vars: `POTATO_HOST`, `POTATO_USER`, `POTATO_PASS`, `TUNNEL_PORT`, `RP_AUTH_TOKEN`, `RP_DELETE_TOKEN`, `POTATO_UPLOAD_DIR`.
  - Missing file → fetch live values: `sshpass -p <pw> ssh daniel@$POTATO_HOST 'cat /mnt/ssd/docker-data/rustypaste/config.toml'` (tokens under `[server.auth]`) and rewrite the file.
- Unit tests green first: `make test`. They are in-process; no tunnel needed.
- Done when: all vars non-empty and `make test` exits 0.

## 1. SSH tunnel (Mac ⇄ potatostack)

```bash
sshpass -p "$POTATO_PASS" ssh -o StrictHostKeyChecking=no -o ServerAliveInterval=30 \
  -o ExitOnForwardFailure=yes -N -L 0.0.0.0:$TUNNEL_PORT:127.0.0.1:$POTATO_RUSTYPASTE_PORT \
  $POTATO_USER@$POTATO_HOST
```

Run as a background task (`bg_run`). The container port is loopback-only on potatostack, so the tunnel is mandatory. Bind `0.0.0.0` so the emulator can reach it.

Verify from Mac: `curl -sf http://127.0.0.1:$TUNNEL_PORT/list | head -c 120` returns JSON.
Done when: curl 200 AND `netstat -an | grep $TUNNEL_PORT` shows `*.8788 LISTEN`.

## 2. Emulator

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
$ANDROID_HOME/emulator/emulator -avd MelanoScan_API35 -no-boot-anim -no-snapshot \
  -netdelay none -netspeed full -gpu swiftshader_indirect   # bg_run
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
```

Screen 1080×2400 @ 420dpi. Done when: `sys.boot_completed=1` and `adb devices` shows `emulator-5554 device`.

## 3. MCP control (optional but preferred)

One-time install:

```bash
npm install -g android-adb-mcp
```

Add to `~/.pi/agent/mcp.json` → `mcpServers.android`: `{"command":"android-adb-mcp","args":[],"env":{"ANDROID_ADB_HOST":"localhost","ANDROID_ADB_PORT":"5037"},"lifecycle":"lazy"}`. Pi loads MCP config at startup only — run `/reload` in the pi TUI before `mcp({connect:"android"})` sees it. Until then, raw adb works identically:

- Screenshot: `adb exec-out screencap -p > /tmp/s.png` then read the image
- Tap: `adb shell input tap X Y` · Type: focus field, `adb shell input text "..."`
- Keys: `adb shell input keyevent KEYCODE_BACK|ESCAPE|ENTER`
- Logs: `adb logcat -d | grep -i rustypastechat` (or `-v time` tail)

## 4. Build + install

```bash
make debug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rustypastechat/.MainActivity
```

Done when: launcher icon opens straight into chat screen (screenshot confirms).

## 5. Configure real server in-app

Settings (nav rail / gear) → Paste Server:
- URL: `http://10.0.2.2:$TUNNEL_PORT` (emulator→Mac alias; never `127.0.0.1` — that is the emulator itself)
- Auth token: `$RP_AUTH_TOKEN`
- Save, then **Test Connection** → expect `Connected! N pastes on server` (it calls `GET /list`).

Done when: test result string contains "Connected!".

## 6. Text message round-trip

Type a unique marker (e.g. `e2e-$(date +%s)`) in the composer, send. Then verify both sides:

1. In-app: bubble appears with status checkmarks progressing single → double → blue.
2. Server-side truth: `sshpass -p $POTATO_PASS ssh $POTATO_USER@$POTATO_HOST "ls -t $POTATO_UPLOAD_DIR | head -3"` shows the new paste, and `curl -s http://127.0.0.1:$TUNNEL_PORT/<file>.txt` returns the marker.

Done when: marker byte-identical on potatostack disk.

## 7. Media share

```bash
adb push /path/to/test-image.jpg /sdcard/Pictures/e2e-test.jpg
adb shell pm grant com.rustypastechat android.permission.READ_MEDIA_IMAGES
```

Pick the image via the gallery picker, send. Verify like step 6 (JPEG lands in uploads dir; in-app inline preview renders).

## 8. Persistence + reconstruction

Kill and relaunch the app (`am force-stop` then `am start`). Chat history must reconstruct from `GET /list` (chat files are grouped by prefix; see `ChatHistoryReconstructionTest`). Done when: previous messages reappear in order with correct send/receive styling.

## 9. Cleanup

Delete test pastes with the delete token: `curl -X DELETE -H "Authorization: Token $RP_DELETE_TOKEN" http://127.0.0.1:$TUNNEL_PORT/<file>.txt`. If the server answers 404 ("delete endpoint not served"), the container predates its delete-token config — ask the user to `docker restart rustypaste` on potatostack, or leave the test files (they expire per server policy).

## Failure triage

- App crash: `adb logcat -d -v time | grep -A20 FATAL` — fix, rebuild, rerun the failing step only.
- Network error in-app: confirm tunnel alive (step 1 verify), confirm URL uses `10.0.2.2`, confirm service bound `0.0.0.0`.
- Test regression: `make test` output names the class; API-shape changes usually need `RustyPasteTestServer` updated in lockstep with `RustyPasteApi`.
