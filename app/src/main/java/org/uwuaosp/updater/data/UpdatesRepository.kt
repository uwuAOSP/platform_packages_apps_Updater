/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.uwuaosp.updater.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import org.uwuaosp.updater.data.source.local.UpdatesLocalDataSource
import org.uwuaosp.updater.data.source.network.UpdatesNetworkDataSource
import org.uwuaosp.updater.data.source.network.toUpdate
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils
import org.uwuaosp.updater.notifications.NotificationHelper
import org.uwuaosp.updater.util.NetworkMonitor
import java.io.IOException

private const val TAG = "UpdatesRepository"

data class UpdateCheckResult(val checkedAt: Long, val updates: List<Update>)

class UpdatesRepository(
    private val networkMonitor: NetworkMonitor,
    private val notificationHelper: NotificationHelper,
    private val networkDataSource: UpdatesNetworkDataSource,
    private val localDataSource: UpdatesLocalDataSource,
) {
    private val checkMutex = Mutex()

    fun observeLocalUpdates(): Flow<List<Update>> = localDataSource.observeUpdates()

    /**
     * Fetches available updates from the server, syncs the local database, and posts a
     * notification if new updates are found. Callers observe [observeLocalUpdates] for results —
     * Room only emits when the stored data actually changes.
     *
     * @return 检查时间和同步后的更新列表，离线时返回null
     * @throws IOException on network or HTTP errors.
     * @throws SerializationException if the response cannot be parsed.
     */
    suspend fun fetchUpdates(): UpdateCheckResult? = checkMutex.withLock {
        if (!networkMonitor.currentNetworkState.isOnline) return@withLock null

        // 先完整校验远端响应，失败或未配置时不能清理已有下载
        val networkUpdates = withContext(Dispatchers.IO) {
            networkDataSource.fetchUpdates().map { it.toUpdate() }.filter { filterUpdates(it) }
        }

        val localUpdates = withContext(Dispatchers.IO) {
            localDataSource.getUpdates()
        }.associateBy { it.downloadId }

        withContext(Dispatchers.IO) {
            localUpdates.values.filter {
                it.downloadId != Update.LOCAL_ID && !filterUpdates(it)
            }.forEach {
                it.file?.delete()
                localDataSource.removeUpdate(it.downloadId)
            }
        }

        val networkIds = networkUpdates.map { it.downloadId }.toSet()

        if (localUpdates.isNotEmpty() && networkUpdates.any { it.downloadId !in localUpdates }) {
            notificationHelper.showNewUpdatesNotification()
        }

        withContext(Dispatchers.IO) {
            // Merge local state into each network update and upsert into the DB.
            // Room's observeUpdates() Flow will emit automatically if anything changed.
            networkUpdates.forEach { networkUpdate ->
                val local = localUpdates[networkUpdate.downloadId]
                val update = if (local != null && local.status.persistentStatus > 0) {
                    networkUpdate.copy(status = local.status, file = local.file)
                } else {
                    networkUpdate
                }
                localDataSource.addUpdate(update)
            }

            // Delete temp files and DB entries for updates no longer advertised by the server.
            localUpdates.values.filter {
                it.status.persistentStatus == 0 &&
                        it.downloadId !in networkIds && it.downloadId != Update.LOCAL_ID &&
                        it.downloadUrl != null
            }.forEach {
                it.file?.delete()
                localDataSource.removeUpdate(it.downloadId)
            }
        }

        UpdateCheckResult(System.currentTimeMillis(), withContext(Dispatchers.IO) { localDataSource.getUpdates() })
    }

    private fun filterUpdates(update: Update): Boolean {
        val isCurrentBuild = update.timestamp == DeviceInfoUtils.buildDateTimestamp
        val isOlderBuild = update.timestamp < DeviceInfoUtils.buildDateTimestamp

        if (!DeviceInfoUtils.isDowngradingAllowed && (isOlderBuild || isCurrentBuild)) {
            Log.d(TAG, "${update.name} is not newer than the current build")
            return false
        }

        if (update.osSdkLevel < DeviceInfoUtils.sdkLevel) {
            Log.d(TAG, "${update.name} is older than current Android version")
            return false
        }

        if (!update.type.equals(DeviceInfoUtils.releaseType, ignoreCase = true)) {
            Log.d(TAG, "${update.name} has type ${update.type}")
            return false
        }
        return true
    }
}
