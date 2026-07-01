package com.rustypastechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.rustypastechat.security.BiometricLockManager
import com.rustypastechat.security.SecurePreferences
import com.rustypastechat.ui.navigation.NavGraph
import com.rustypastechat.ui.screens.LockScreen
import com.rustypastechat.ui.theme.RustyPasteChatTheme
import java.util.Timer
import java.util.TimerTask

class MainActivity : FragmentActivity() {

    private var lockTimer: Timer? = null
    private var lastActiveTime = System.currentTimeMillis()
    private lateinit var securePrefs: SecurePreferences
    private lateinit var lockManager: BiometricLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        securePrefs = SecurePreferences(this)
        lockManager = BiometricLockManager(this)

        setContent {
            RustyPasteChatTheme {
                var isLocked by remember {
                    mutableStateOf(securePrefs.biometricEnabled)
                }

                if (isLocked) {
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
                                // Device has no biometric/PIN — allow access
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
