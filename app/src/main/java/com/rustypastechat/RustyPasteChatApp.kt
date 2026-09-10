package com.rustypastechat

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.sync.MessageNotifier
import com.rustypastechat.data.sync.SyncScheduler
import com.rustypastechat.security.EncryptedCache
import com.rustypastechat.security.EncryptedMediaInterceptor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class RustyPasteChatApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var encryptedCache: EncryptedCache
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var messageNotifier: MessageNotifier

    // ChatSyncWorker is @HiltWorker, so WorkManager must be built with Hilt's
    // factory. The manifest removes WorkManager's default initializer for the
    // same reason; without both halves the worker fails to construct at run
    // time with "Could not instantiate ChatSyncWorker".
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Coil's ImageLoader is built once (newImageLoader() below); keep it in sync with the
        // user's live "Encrypt Media Cache" toggle instead of requiring an app restart.
        CoroutineScope(Dispatchers.Default).launch {
            preferencesManager.settingsFlow
                .map { it.encryptMediaCache }
                .distinctUntilChanged()
                .collect { enabled -> Coil.setImageLoader(buildImageLoader(enabled)) }
        }
        messageNotifier.ensureChannel()
        // Keep the periodic sync job in step with the setting, including
        // after a reinstall or a "clear data" that wiped WorkManager's own
        // database while the preference survived (or vice versa).
        CoroutineScope(Dispatchers.Default).launch {
            preferencesManager.settingsFlow
                .map { it.backgroundSyncEnabled to it.syncIntervalMinutes }
                .distinctUntilChanged()
                .collect { (enabled, interval) ->
                    if (enabled) syncScheduler.schedule(interval.toLong()) else syncScheduler.cancel()
                }
        }
    }

    // Cold-start default before the settings flow above has emitted — secure by default.
    override fun newImageLoader(): ImageLoader = buildImageLoader(encryptMediaCache = true)

    private fun buildImageLoader(encryptMediaCache: Boolean): ImageLoader {
        val builder = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
        if (encryptMediaCache) {
            builder.okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(EncryptedMediaInterceptor(encryptedCache))
                    .build()
            }
            // Don't also keep an unencrypted copy in Coil's own disk cache.
            builder.diskCache(null)
        }
        return builder.build()
    }
}
