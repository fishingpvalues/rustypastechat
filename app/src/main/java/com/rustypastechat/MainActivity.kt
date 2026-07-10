package com.rustypastechat

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.security.BiometricLockManager
import com.rustypastechat.security.SecurePreferences
import com.rustypastechat.ui.navigation.NavGraph
import com.rustypastechat.ui.screens.LockScreen
import com.rustypastechat.ui.screens.OnboardingScreen
import com.rustypastechat.ui.screens.RustySplashContent
import com.rustypastechat.ui.theme.RustyPasteChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var securePrefs: SecurePreferences
    @Inject lateinit var lockManager: BiometricLockManager
    @Inject lateinit var preferencesManager: PreferencesManager

    private var lockTimer: Timer? = null
    private var lastActiveTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by preferencesManager.settingsFlow.collectAsState(initial = com.rustypastechat.data.model.AppSettings())

            RustyPasteChatTheme(
                darkTheme = when (themeMode.themeMode) {
                    com.rustypastechat.data.model.ThemeMode.LIGHT -> false
                    com.rustypastechat.data.model.ThemeMode.DARK -> true
                    com.rustypastechat.data.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                dynamicColor = themeMode.useDynamicColor
            ) {
                var isLocked by remember {
                    mutableStateOf(securePrefs.biometricEnabled)
                }
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(650)
                    showSplash = false
                }
                val hasSeenOnboarding by preferencesManager.hasSeenOnboardingFlow.collectAsState(initial = true)
                val scope = rememberCoroutineScope()

                AnimatedContent(
                    targetState = showSplash,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "splash"
                ) { splashVisible ->
                    if (splashVisible) {
                        RustySplashContent()
                    } else if (!hasSeenOnboarding) {
                        OnboardingScreen(onDone = { scope.launch { preferencesManager.setOnboardingSeen() } })
                    } else if (isLocked) {
                        LockScreen(
                            onUnlock = {
                                if (lockManager.isAvailable()) {
                                    lockManager.authenticate(
                                        activity = this@MainActivity,
                                        onSuccess = {
                                            isLocked = false
                                            lastActiveTime = System.currentTimeMillis()
                                            startLockTimer { isLocked = true }
                                        }
                                    )
                                } else {
                                    isLocked = false
                                }
                            }
                        )
                    } else {
                        NavGraph()
                        if (securePrefs.biometricEnabled) {
                            startLockTimer { isLocked = true }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lastActiveTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        lastActiveTime = System.currentTimeMillis()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastActiveTime = System.currentTimeMillis()
    }

    private fun startLockTimer(onLock: () -> Unit) {
        lockTimer?.cancel()
        val timeoutMs = securePrefs.lockTimeoutSeconds * 1000L
        if (timeoutMs <= 0) return

        lockTimer = Timer("lock-timer", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - lastActiveTime
                    if (elapsed >= timeoutMs) {
                        runOnUiThread { onLock() }
                        cancel()
                    }
                }
            }, 1000, 1000)
        }
    }
}
