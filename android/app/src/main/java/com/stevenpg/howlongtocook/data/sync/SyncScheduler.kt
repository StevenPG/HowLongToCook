package com.stevenpg.howlongtocook.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Abstraction so the repository can request syncs without depending on WorkManager in tests. */
interface SyncRequester {
    /** Debounced background sync after a local edit. */
    fun requestSync()
}

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncRequester {
    private val workManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun requestSync() = enqueue(delaySeconds = 5)

    fun syncNow() = enqueue(delaySeconds = 0)

    private fun enqueue(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** True while a sync is running. */
    val isSyncing: Flow<Boolean>
        get() = workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private companion object {
        const val ONE_TIME = "sync-now"
        const val PERIODIC = "sync-periodic"
    }
}
