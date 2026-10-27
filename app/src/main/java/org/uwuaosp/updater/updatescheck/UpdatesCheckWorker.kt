/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.uwuaosp.updater.updatescheck

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.uwuaosp.updater.UpdaterApplication
import org.uwuaosp.updater.data.CheckInterval
import org.uwuaosp.updater.data.source.network.UpdatesNetworkDataSource
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class UpdatesCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!UpdatesNetworkDataSource.isConfigured(applicationContext)) return Result.success()
        return try {
            val application = applicationContext as UpdaterApplication
            val result = application.updatesRepository.fetchUpdates()
            if (result != null) {
                application.appStateRepository.setLastCheckedTimestamp(result.checkedAt)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdatesCheckWorker"

        private const val WORK_NAME_ONESHOT = "update_check_oneshot"
        private const val WORK_NAME_PERIODIC = "update_check_periodic"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        @JvmStatic
        fun cancelPeriodicCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }

        @JvmStatic
        fun scheduleOneshotCheck(context: Context) {
            if (!UpdatesNetworkDataSource.isConfigured(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONESHOT)
                return
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdatesCheckWorker>()
                    .setConstraints(networkConstraints)
                    .build(),
            )
        }

        @JvmStatic
        fun schedulePeriodicCheck(context: Context) {
            if (!UpdatesNetworkDataSource.isConfigured(context)) {
                cancelPeriodicCheck(context)
                return
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val prefs =
                    (context.applicationContext as UpdaterApplication).userPreferencesRepository
                if (prefs.getPeriodicCheckEnabled()) {
                    enqueuePeriodicCheck(
                        context,
                        prefs.getCheckInterval(),
                        ExistingPeriodicWorkPolicy.KEEP
                    )
                }
            }
        }

        @JvmStatic
        fun reschedulePeriodicCheck(context: Context) {
            if (!UpdatesNetworkDataSource.isConfigured(context)) {
                cancelPeriodicCheck(context)
                return
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val prefs =
                    (context.applicationContext as UpdaterApplication).userPreferencesRepository
                if (!prefs.getPeriodicCheckEnabled()) {
                    cancelPeriodicCheck(context)
                } else {
                    enqueuePeriodicCheck(
                        context,
                        prefs.getCheckInterval(),
                        ExistingPeriodicWorkPolicy.UPDATE
                    )
                }
            }
        }

        private fun enqueuePeriodicCheck(
            context: Context,
            interval: CheckInterval,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val request =
                PeriodicWorkRequestBuilder<UpdatesCheckWorker>(interval.duration.toJavaDuration())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1.hours.toJavaDuration())
                    .setConstraints(networkConstraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                policy,
                request,
            )
            Log.d(TAG, "Scheduled periodic check (policy=$policy, interval=${interval.duration})")
        }
    }
}
