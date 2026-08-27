# PLAN.md — Wie an dieser App gearbeitet wird (potatostack, headless)

Arbeitsanweisung für den Hermes-Agenten. Kein Emulator, keine GUI, keine
Screenshot-Flut. Stand 2026-08-27, alles hier ist auf potatostack verifiziert.

## 0. Wo und womit

Repo liegt auf **potatostack** unter `/home/daniel/workdir/rustypastechat`
(gleicher Baum wie auf dem Mac). Der Agent läuft im Container und erreicht die
Host-Toolchain über `onhost` — siehe Skill `go-builds`.

```bash
onhost -C /home/daniel/workdir/rustypastechat './gradlew assembleDebug'
```

Installiert und geprüft: JDK 17, Android SDK 36 (`/opt/android-sdk`),
build-tools 36.0.0, platform-tools/adb 1.0.41, Gradle-Wrapper 8.11.1.
`JAVA_HOME`/`ANDROID_HOME` kommen aus `/etc/profile.d/android-sdk.sh`, damit
**kein `local.properties` ins Repo geschrieben werden muss**.

## 1. Die Speicherfalle, die drei Builds gekostet hat

`onhost` wechselt Dateisystem und Pfade — **nicht die cgroup**. Der Build läuft
weiter im Speicher-Limit des hermes-Containers. Bei 2G starb der Gradle-Daemon
reproduzierbar bei `mergeExtDexDebug`, während der Host 14G frei hatte. Die
Fehlermeldung nennt den Grund nicht:

```
Gradle build daemon disappeared unexpectedly
```

Der Beweis steht nur im Kernel-Log:

```
Memory cgroup out of memory: Killed process (java) constraint=CONSTRAINT_MEMCG
```

Limit ist jetzt 8G (`compose.apps-base.yml`, live per `docker update` gesetzt).
**Wenn ein Build ohne Fehlermeldung stirbt, zuerst hier nachsehen:**

```bash
docker exec hermes sh -c 'cat /sys/fs/cgroup/memory.events'   # oom_kill > 0 ?
```

Nicht den Host-RAM prüfen. Der ist nie das Problem.

## 2. Headless testen statt Screenshots verschicken

Die mittwald-Anbindung erlaubt max. 5 Bilder pro Chat. Screenshots sind deshalb
**kein** Debug-Weg, sondern ein Notnagel. Reihenfolge:

1. **JVM-Unit-Tests** — `./gradlew testDebugUnitTest`. 13 Testdateien vorhanden,
   inkl. `PasteApiIntegrationTest` und `RustyPasteTestServer` (MockWebServer).
2. **Robolectric** — 4.13 ist bereits deklariert. Android-Framework auf der JVM,
   kein Gerät. Damit laufen auch Compose-UI-Tests (`compose-ui-test-junit4` ist
   ebenfalls schon im Katalog).
3. **Roborazzi** — fehlt noch, siehe TODO P1. Rendert Composables auf der JVM zu
   PNG **und vergleicht sie numerisch**. Das ist die eigentliche Antwort auf das
   Bilder-Limit: der Agent liest einen Zahlenwert, kein Bild, und schaut nur bei
   einer Abweichung ins Diff.
4. **Echtes Gerät per adb over WiFi** — nur wenn 1–3 nicht reichen. Kein
   Emulator nötig, das Poco X7 Pro genügt:
   ```bash
   onhost 'adb connect <phone-ip>:5555 && adb logcat -s RustyPasteChat:*'
   ```

Text schlägt Bild: `adb logcat`, Testreports unter
`app/build/reports/tests/`, und `./gradlew ... --scan` liefern mehr Information
pro Token als jeder Screenshot.

## 3. Gegen echte Daten testen, nicht gegen Mocks

Auf potatostack läuft ein echter rustypaste-Container (`orhunp/rustypaste:0.18.0`,
seit 8 Tagen up). Integrationstests sollen dagegen laufen, nicht nur gegen
MockWebServer — nur so fallen Serialisierungs- und HTTP-Details auf, die ein Mock
verschweigt. Basis-URL aus der Umgebung ziehen, damit CI weiter Mocks nutzt.

## 4. Reihenfolge bei jeder Änderung

```bash
onhost -C /home/daniel/workdir/rustypastechat './gradlew testDebugUnitTest'   # schnell
onhost -C /home/daniel/workdir/rustypastechat './gradlew assembleDebug'       # baut es?
onhost -C /home/daniel/workdir/rustypastechat './gradlew lint'                # Android Lint
```

Erst wenn das grün ist, committen. Und: **Exit-Code niemals durch eine Pipe
lesen** — `./gradlew build | tail` liefert den Status von `tail`. Immer in eine
Datei umleiten und `$?` separat prüfen.

## 5. Was nicht angefasst wird

- Kein `local.properties` ins Repo (Umgebungsvariablen sind gesetzt).
- Kein Umbau der Verzeichnisstruktur im workdir.
- `hermes` selbst nie neu bauen oder recreaten — das ist der Container, in dem
  der Agent läuft.
