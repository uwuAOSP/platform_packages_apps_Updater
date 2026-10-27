/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.uwuaosp.updater

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.uwuaosp.updater.data.Update
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils
import org.uwuaosp.updater.data.source.network.UpdatesNetworkDataSource

private const val TAG = "UpdatesViewModel"

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    data class UiState(
        val updates: List<Update> = emptyList(),
        val isCheckingForUpdates: Boolean = false,
        val isOnline: Boolean = true,
        val lastCheckedTimestamp: Long = 0L,
        val hasUpdateCheckFailed: Boolean = false,
        val sourceConfigured: Boolean = false,
        val hasCheckedSuccessfully: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState(
        sourceConfigured = UpdatesNetworkDataSource.isConfigured(application),
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UiState> = _uiState.asLiveData()
    private val _feedback = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val feedback = _feedback.asSharedFlow()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val appStateRepository = updaterApplication.appStateRepository
    private val networkMonitor = updaterApplication.networkMonitor

    init {
        viewModelScope.launch {
            appStateRepository.lastCheckedTimestampFlow.collect { ts ->
                _uiState.update { it.copy(lastCheckedTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                _uiState.update { it.copy(updates = updates) }
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .onEach { networkState ->
                    _uiState.update { it.copy(isOnline = networkState.isOnline) }
                }
                .filter { it.isOnline }
                .collect { fetchUpdates(userInitiated = false) }
        }
    }

    @JvmOverloads
    fun fetchUpdates(userInitiated: Boolean = true) {
        if (!_uiState.value.sourceConfigured) {
            if (userInitiated) _feedback.tryEmit(R.string.uwu_source_unconfigured)
            return
        }
        if (!_uiState.value.isOnline) {
            if (userInitiated) _feedback.tryEmit(R.string.check_your_internet_connection)
            return
        }
        if (_uiState.value.isCheckingForUpdates) return
        _uiState.update { it.copy(isCheckingForUpdates = true, hasUpdateCheckFailed = false) }

        viewModelScope.launch {
            try {
                val result = repository.fetchUpdates()
                if (result != null) {
                    appStateRepository.setLastCheckedTimestamp(result.checkedAt)
                    _uiState.update { it.copy(updates = result.updates, hasCheckedSuccessfully = true) }
                    if (userInitiated && result.updates.none { it.timestamp > DeviceInfoUtils.buildDateTimestamp }) {
                        _feedback.emit(R.string.uwu_up_to_date)
                    }
                } else if (userInitiated) {
                    _uiState.update { it.copy(hasCheckedSuccessfully = false) }
                    _feedback.emit(R.string.check_your_internet_connection)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch updates", e)
                _uiState.update {
                    it.copy(
                        hasUpdateCheckFailed = true,
                        hasCheckedSuccessfully = false,
                    )
                }
                if (userInitiated) _feedback.emit(R.string.updates_check_failed)
            } finally {
                _uiState.update { it.copy(isCheckingForUpdates = false) }
            }
        }
    }
}
