/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.framework.compose.LocalNavController
import java.io.File
import kotlinx.coroutines.launch
import org.uwuaosp.compose.widget.editor.SettingsChoiceDialog
import org.uwuaosp.compose.widget.preference.PreferenceRow
import org.uwuaosp.compose.widget.preference.SettingsSection
import org.uwuaosp.compose.widget.preference.SwitchPreferenceRow
import org.uwuaosp.compose.widget.scaffold.SettingsScaffold
import org.uwuaosp.updater.R
import org.uwuaosp.updater.UpdaterApplication
import org.uwuaosp.updater.data.CheckInterval
import org.uwuaosp.updater.data.source.network.UpdatesNetworkDataSource
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils

@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    val navigation = LocalNavController.current
    val application = context.applicationContext as UpdaterApplication
    val repository = application.userPreferencesRepository
    val scope = rememberCoroutineScope()
    val autoCheck by repository.periodicCheckEnabledFlow.collectAsStateWithLifecycle(true)
    val interval by repository.checkIntervalFlow.collectAsStateWithLifecycle(CheckInterval.default)
    val autoDelete by repository.autoDeleteFlow.collectAsStateWithLifecycle(true)
    val streaming by repository.streamUpdatesFlow.collectAsStateWithLifecycle(true)
    val meteredWarning by repository.meteredNetworkWarningFlow.collectAsStateWithLifecycle(true)
    val perfMode by repository.abPerfModeFlow.collectAsStateWithLifecycle(false)
    val battery by
        application.batteryMonitor.batteryState.collectAsStateWithLifecycle(
            application.batteryMonitor.currentBatteryState
        )
    var recovery by remember { mutableStateOf(repository.getRecoveryUpdateEnabled()) }
    val hasRecoveryScript = remember { File("/vendor/bin/install-recovery.sh").exists() }
    val configured = remember { UpdatesNetworkDataSource.isConfigured(context) }
    var showIntervals by remember { mutableStateOf(false) }
    val intervalNames =
        listOf(R.string.time_unit_day, R.string.time_unit_week, R.string.time_unit_month)

    if (showIntervals)
        SettingsChoiceDialog(
            title = stringResource(R.string.menu_auto_updates_check_interval),
            options = intervalNames.map { stringResource(it) },
            selectedIndex = interval.ordinal,
            onSelect = { index ->
                scope.launch { repository.setCheckInterval(CheckInterval.entries[index]) }
                showIntervals = false
            },
            onDismissRequest = { showIntervals = false },
        )

    SettingsScaffold(
        title = stringResource(R.string.menu_preferences),
        showBackButton = true,
        onNavigateUp = { navigation.navigateBack() },
        startCollapsed = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSection(stringResource(R.string.pref_category_background_sync)) {
                item {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_auto_updates_check),
                        summary =
                            stringResource(
                                if (configured) R.string.menu_auto_updates_check_summary
                                else R.string.uwu_source_unconfigured
                            ),
                        checked = autoCheck,
                        enabled = configured,
                        onCheckedChange = {
                            scope.launch { repository.setPeriodicCheckEnabled(it) }
                        },
                    )
                }
                item {
                    PreferenceRow(
                        title = stringResource(R.string.menu_auto_updates_check_interval),
                        summary = stringResource(intervalNames[interval.ordinal]),
                        enabled = configured && autoCheck,
                        onClick = { showIntervals = true },
                    )
                }
            }
            SettingsSection(stringResource(R.string.pref_category_download_install)) {
                item(visible = DeviceInfoUtils.isABDevice) {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_stream_updates),
                        summary = stringResource(R.string.menu_stream_updates_summary),
                        checked = streaming,
                        onCheckedChange = { scope.launch { repository.setStreamUpdates(it) } },
                    )
                }
                item(visible = !DeviceInfoUtils.isABDevice) {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_auto_delete_updates),
                        summary = stringResource(R.string.menu_auto_delete_updates_summary),
                        checked = autoDelete,
                        onCheckedChange = { scope.launch { repository.setAutoDelete(it) } },
                    )
                }
                item {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_metered_network_warning),
                        summary = stringResource(R.string.menu_metered_network_warning_summary),
                        checked = meteredWarning,
                        onCheckedChange = {
                            scope.launch { repository.setMeteredNetworkWarning(it) }
                        },
                    )
                }
                item(visible = DeviceInfoUtils.isABDevice) {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_ab_perf_mode),
                        summary =
                            stringResource(
                                if (battery.isAcCharging)
                                    R.string.menu_ab_perf_mode_summary_charging
                                else R.string.menu_ab_perf_mode_summary
                            ),
                        checked = perfMode || battery.isAcCharging,
                        enabled = !battery.isAcCharging,
                        onCheckedChange = { scope.launch { repository.setAbPerfMode(it) } },
                    )
                }
                item(visible = hasRecoveryScript) {
                    SwitchPreferenceRow(
                        title = stringResource(R.string.menu_update_recovery),
                        summary = stringResource(R.string.menu_update_recovery_summary),
                        checked = recovery,
                        onCheckedChange = {
                            recovery = it
                            repository.setRecoveryUpdateEnabled(it)
                        },
                    )
                }
            }
        }
    }
}
