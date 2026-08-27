# TODO.md — Was der App zu "prod ready / SOTA Android" fehlt

Erhoben am 2026-08-27 am tatsächlichen Code, nicht aus der Doku. Zahlen stammen
aus `app/src/main/java/com/rustypastechat` (67 Kotlin-Dateien, 10.819 Zeilen,
92 `@Composable`, 6 ViewModels, 0 TODO/FIXME).

Ergänzt `SOTA_GAPS.md` (Design-System) — dieses Dokument deckt Architektur,
Test, Performance und Plattform-Konformität ab.

---

## P0 — blockiert "prod ready"

### 1. Keine lokale Persistenz. Room fehlt komplett.
Nachrichten leben in `MutableStateFlow` in `ChatViewModel` / `ChatListViewModel`.
Bei Prozesstod ist der Verlauf weg; die einzige Quelle ist der Server. Für eine
Chat-App ist das der schwerwiegendste Mangel: kein Offline-Lesen, kein
optimistisches Senden über einen Neustart hinweg, kein Suchindex.

- Room + `PagingSource`, Server bleibt Source of Truth, DB ist der Cache.
- Outbox-Muster für ungesendete Nachrichten (Status `PENDING → SENT → FAILED`).
- Erst danach ergeben die WhatsApp-Häkchen überhaupt Sinn — aktuell überleben
  sie keinen App-Neustart.

### 2. Null Lokalisierung. 127 hardcodierte Strings, 0 `stringResource`.
Gemessen: `Text("…")`-Literale 127, `stringResource` 0. Kein `strings.xml` im
Einsatz. Das ist gleichzeitig ein Accessibility-Problem (TalkBack liest
Literale, die nicht übersetzbar sind) und blockiert jede Veröffentlichung
außerhalb von Deutsch/Englisch.

- Alle Strings nach `res/values/strings.xml`, `res/values-de/` dazu.
- Lint-Regel `HardcodedText` auf `error` hochziehen, damit es nicht zurückfällt.

### 3. `READ_EXTERNAL_STORAGE` bei `targetSdk = 36` ist wirkungslos.
Das Manifest fordert `READ_EXTERNAL_STORAGE`. Ab Android 13 (API 33) wird die
Berechtigung ignoriert; ab 34 ist der richtige Weg der **Photo Picker**, der
gar keine Berechtigung braucht.

- `ActivityResultContracts.PickVisualMedia` statt Storage-Permission.
- Permission aus dem Manifest entfernen (weniger Angriffsfläche, bessere
  Store-Bewertung).

### 4. Kein `androidTest`-SourceSet, keine UI-Tests.
`app/src` enthält nur `main` und `test`. `compose-ui-test-junit4` ist bereits
deklariert, wird aber nirgends genutzt. `SOTA_GAPS.md` nennt das selbst als
offen — mit der Begründung, man könne es auf dieser Maschine nicht ausführen.
**Diese Begründung ist seit 2026-08-27 hinfällig**: JDK 17 + SDK 36 sind
installiert, `testDebugUnitTest` läuft.

---

### 4b. Ein Test ist rot: `LlmIntegrationTest > invalid API key returns 401`
Gemessen beim ersten grünen Build auf potatostack (2026-08-27):
`java.lang.AssertionError at LlmIntegrationTest.kt:186`. Der Test erwartet 401
bei ungültigem Schlüssel; der konfigurierte Endpunkt antwortet offenbar anders
(mittwald liefert bei Auth-Fehlern teilweise 500, siehe CLAUDE.md des Stacks).
Entweder die Erwartung an das reale Verhalten anpassen oder den Test gegen
MockWebServer statt gegen einen echten Endpunkt fahren — ein Integrationstest,
der von einem fremden Dienst abhängt, ist per Definition flaky.

## P1 — SOTA-Lücken (Reihenfolge = Nutzen pro Aufwand)

### 5. Roborazzi — headless Screenshot-Tests
Löst direkt das Debug-Problem: rendert Composables auf der JVM zu PNG und
vergleicht sie **numerisch**. Der Agent liest eine Zahl statt Bilder zu
verschicken (mittwald: max. 5 Bilder pro Chat). Setzt auf dem schon
vorhandenen Robolectric auf, kostet also wenig.

### 6. Baseline Profile + Macrobenchmark
Fehlt beides. Baseline Profiles sind seit AGP 8 der Standardweg für
Startup-Performance (typisch 20–30 % schnellerer Kaltstart). Ohne
Macrobenchmark-Modul gibt es keine Zahl, gegen die man optimiert.

### 7. WorkManager
Uploads laufen im ViewModel-Scope. Verlässt der Nutzer die App während eines
Medien-Uploads, bricht er ab. WorkManager mit `Constraints` (Netz vorhanden)
und Backoff ist hier der korrekte Mechanismus.

### 8. Paging 3
Chat- und Chatlisten laden vollständig in den Speicher. Bei einer echten
Historie skaliert das nicht.

### 9. Turbine + MockK
Flow-Tests ohne Turbine sind unnötig fragil; für Repository-Tests fehlt ein
Mocking-Framework. Beides fehlt im Katalog.

### 10. Detekt + ktlint
Keine statische Analyse, keine Formatprüfung. Bei 10.819 Zeilen und
weiterem Wachstum ist das der billigste Qualitätsgewinn.

### 11. LeakCanary (nur `debugImplementation`)
Eine Chat-UI mit Coil-Bildern und ViewModels ist der klassische Ort für
Context-Leaks. Kosten: eine Zeile.

### 12. Core Library Desugaring
`minSdk = 26`, aber kein `coreLibraryDesugaring`. Ohne das sind `java.time` und
neuere `java.util`-APIs unterhalb API 26 nicht sicher nutzbar.

---

## P2 — Korrektheit und Design

### 13. `runBlocking` im OkHttp-Interceptor
`security/EncryptedMediaInterceptor.kt:23,37` blockiert einen OkHttp-Thread pro
Medien-Request. Bei mehreren parallelen Bildern erschöpft das den Dispatcher.
Der Interceptor sollte synchron auf dem Cache arbeiten oder der Cache eine
blockierende API anbieten — nicht eine Coroutine im Thread parken.

### 14. `runBlocking` in `BackupManager.kt:37`
Liest die Settings synchron. In einer `suspend`-Funktion aufrufen statt zu
blockieren.

### 15. `contentDescription` nur teilweise
58 Vorkommen bei 92 Composables. Jedes bedienbare Element braucht eine
Beschreibung, jedes rein dekorative explizit `null` — beides ist eine bewusste
Entscheidung, kein Vergessen.

### 16. Top-Bar zeigt immer "RustyPaste Chat"
Aus `SOTA_GAPS.md` übernommen. Sollte den Chat-Namen zeigen; das ist eine
Produktentscheidung und kein Design-Detail.

### 17. Dynamic Color ohne Ausweichpfad
Auf API 31+ bedingungslos aktiv. Material You kann die Bubble- und
Status-Farben (die semantisch belegt sind) überschreiben. Mindestens ein
Schalter in den Einstellungen, besser die semantischen Farben davon ausnehmen.

### 18. Spacing-Tokens nicht durchgezogen
`RustySpacing` existiert in `ui/theme/Shape.kt`, die Screens nutzen weiter
`8.dp`/`12.dp`/`16.dp`-Literale. Mechanisch, risikoarm, aber großer Diff —
inkrementell pro Screen erledigen.

---

## Was bereits gut ist (nicht anfassen)

- **Sicherheit ist überdurchschnittlich**: `EncryptedSharedPreferences`,
  `MasterKey`, `BiometricLockManager`, `EncryptedCache`, verschlüsselter
  Medien-Interceptor, `networkSecurityConfig` im Manifest. Nur zwei
  Berechtigungen (`INTERNET`, `READ_EXTERNAL_STORAGE`).
- Hilt, DataStore, Navigation-Compose, Coil, Retrofit + kotlinx.serialization,
  Material 3, SplashScreen-API — der moderne Standardstapel ist da.
- 0 `TODO`/`FIXME`, 0 `GlobalScope`. Der Code ist gepflegt.
