package com.rustypastechat.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the single periodic sync job, so nothing else has to know WorkManager exists. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * @param intervalMinutes coerced to WorkManager's 15-minute floor for
     * periodic work; asking for less silently gets 15 anyway, so clamp it
     * where the user can see the number rather than pretending.
     */
    fun schedule(intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<ChatSyncWorker>(
            intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES), TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ChatSyncWorker.WORK_NAME,
            // UPDATE, not KEEP: changing the interval in settings must take
            // effect, and KEEP would leave the old schedule running forever.
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ChatSyncWorker.WORK_NAME)
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15L
    }
}
