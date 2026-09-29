package dev.tidewall.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.tidewall.app
import dev.tidewall.core.LogBuffer
import dev.tidewall.libcore.Libcore
import dev.tidewall.vpn.VpnStateHolder
import dev.tidewall.vpn.VpnStatus
import java.util.concurrent.TimeUnit

/** Refreshes subscription profiles whose update interval has passed. */
class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val force = inputData.getBoolean(KEY_FORCE, false)
        val due = if (force) app.profiles.profiles.value.filter { it.url != null } else app.profiles.dueForUpdate()
        var failed = false
        for (p in due) {
            try {
                val updated = app.profiles.update(p)
                LogBuffer.add("info", "Updated subscription ${p.name}")
                val state = VpnStateHolder.state.value
                if (state.status == VpnStatus.CONNECTED && state.profileId == updated.id && Libcore.isProxyRunning()) {
                    Libcore.reloadProfile(app.profiles.content(updated), app.settings.current().engineOptionsJson())
                }
            } catch (e: Exception) {
                failed = true
                LogBuffer.add("warning", "Updating ${p.name} failed: ${e.message}")
            }
        }
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_FORCE = "force"
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("subscriptions", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun updateAllNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setConstraints(constraints)
                .setInputData(androidx.work.workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
