package com.stevenpg.howlongtocook.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = engine.sync()) {
        is SyncOutcome.Success, SyncOutcome.NotConfigured -> Result.success()
        is SyncOutcome.Failure -> if (outcome.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
