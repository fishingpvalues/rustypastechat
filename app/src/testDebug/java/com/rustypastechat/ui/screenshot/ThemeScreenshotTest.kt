package com.rustypastechat.ui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.rustypastechat.ui.theme.RustyPasteChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests auf der JVM - kein Emulator, kein Geraet.
 *
 * Der Zweck ist NICHT, Bilder anzusehen. Roborazzi vergleicht die erzeugten
 * PNGs numerisch gegen die eingecheckten Referenzen und schreibt im Fehlerfall
 * ein _compare.png. Der Agent liest also ein Testergebnis statt Screenshots zu
 * verschicken - mittwald erlaubt nur 5 Bilder pro Chat.
 *
 *   ./gradlew recordRoborazziDebug    # Referenzen aufnehmen
 *   ./gradlew verifyRoborazziDebug    # dagegen pruefen
 *
 * sdk = 34: Robolectric 4.13 kann hoechstens API 34, die App hat targetSdk 36.
 * Ohne das bricht der Runner mit "targetSdkVersion=36 > maxSdkVersion=34" ab.
 * Kann weg, sobald Robolectric >= 4.15 im Katalog steht.
 *
 * Warum hier das Farbschema und nicht MessageBubble: SwipeableMessageBubble
 * zieht ueber SecurePreferences -> VaultCrypto den AndroidKeyStore in die
 * Komposition, den es unter Robolectric nicht gibt. Das ist ein
 * Architekturproblem der App, kein Testproblem - siehe TODO.md P0.
 */
// Lives in src/testDebug, not src/test: androidx.compose.ui.test.manifest -
// which is what contributes the ComponentActivity these tests launch - merges
// into the DEBUG manifest only. Under testReleaseUnitTest the same test dies
// with "Unable to resolve activity for Intent ... ComponentActivity", which
// failed `make test` on every run regardless of what was being changed.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp-xhdpi",
    // Nackte Application statt der Hilt-App: RustyPasteChatApplication zieht
    // beim Start ueber SecurePreferences -> VaultCrypto den AndroidKeyStore
    // hoch, den es unter Robolectric nicht gibt. Das blockiert JEDEN
    // Robolectric-Test, nicht nur Screenshots - siehe TODO.md P0.
    application = android.app.Application::class,
)
class ThemeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Palette() {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Headline", style = MaterialTheme.typography.headlineMedium)
            Text("Body text", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {}) { Text("Primary") }
            Card { Text("Card surface", Modifier.padding(16.dp)) }
        }
    }

    @Test
    fun theme_light() {
        composeRule.setContent {
            RustyPasteChatTheme(darkTheme = false, dynamicColor = false) { Surface { Palette() } }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/theme_light.png")
    }

    @Test
    fun theme_dark() {
        composeRule.setContent {
            RustyPasteChatTheme(darkTheme = true, dynamicColor = false) { Surface { Palette() } }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/theme_dark.png")
    }
}
