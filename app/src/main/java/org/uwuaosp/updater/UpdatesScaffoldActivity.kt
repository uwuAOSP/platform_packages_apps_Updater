/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.uwuaosp.compose.settingslib.PreferenceRow
import org.uwuaosp.compose.settingslib.SettingsPreferenceGroup
import org.uwuaosp.compose.settingslib.SettingsScaffold
import org.uwuaosp.updater.controller.UpdaterController
import org.uwuaosp.updater.data.Update
import org.uwuaosp.updater.data.UpdateStatus
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils
import org.uwuaosp.updater.preferences.PreferencesActivity
import org.uwuaosp.updater.ui.ChangelogSheet
import org.uwuaosp.updater.ui.UpdatePackageCard
import org.uwuaosp.updater.ui.UwuHero
import org.uwuaosp.updater.updates.action.AlertDialogState
import org.uwuaosp.updater.updates.action.UpdateActionDialog
import org.uwuaosp.updater.updates.action.UpdateActionHandler
import org.uwuaosp.updater.updates.state.UpdateItemStateMapper
import org.uwuaosp.updater.util.StringUtil

abstract class UpdatesScaffoldActivity : ComponentActivity() {
    private val viewModel by viewModels<UpdatesViewModel>()
    private var activeController: UpdaterController? by mutableStateOf(null)
    private var controllerRevision by mutableIntStateOf(0)
    private var importing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun setupCompose() {
        setContent {
            SettingsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val owner = LocalLifecycleOwner.current
                LaunchedEffect(viewModel, owner) {
                    owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.feedback.collect { message ->
                            Toast.makeText(this@UpdatesScaffoldActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                UpdatesContent(uiState, activeController, controllerRevision,
                    onBack = { finish() }, onRefresh = { onRefreshClick() },
                    onImport = { onLocalUpdateClick() },
                    onPreferences = { startActivity(Intent(this, PreferencesActivity::class.java)) },
                    activity = this)
                ImportDialogs()
            }
        }
    }

    protected fun setUpdaterController(controller: UpdaterController?) {
        activeController = controller
        notifyControllerStateChanged()
    }

    protected fun notifyControllerStateChanged() { controllerRevision++ }
    protected fun showImportProgress() { importing = true }
    protected fun hideImportProgress() { importing = false }
    protected fun showImportResult(message: String) {
        importing = false
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ImportDialogs() {
        if (importing) AlertDialog(onDismissRequest = {}, confirmButton = {},
            title = { Text(stringResource(R.string.local_update_import)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
                    Text(
                        text = stringResource(R.string.uwu_importing),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            })
    }

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun exportUpdate(update: Update) {}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdatesContent(
    state: UpdatesViewModel.UiState, controller: UpdaterController?, revision: Int,
    onBack: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit,
    onPreferences: () -> Unit, activity: UpdatesScaffoldActivity,
) {
    val application = activity.application as UpdaterApplication
    val network by application.networkMonitor.networkState.collectAsStateWithLifecycle(
        application.networkMonitor.currentNetworkState)
    val streaming by application.userPreferencesRepository.streamUpdatesFlow.collectAsStateWithLifecycle(true)
    var menu by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<AlertDialogState?>(null) }
    val items = remember(state.updates, controller, revision, streaming, network) {
        controller?.let {
            val mapper = UpdateItemStateMapper(activity, it, streaming)
            state.updates.mapNotNull { update -> it.getUpdate(update.downloadId)?.let { actual -> mapper.map(actual, network) } }
        }.orEmpty()
    }
    val actions = remember(controller) {
        controller?.let { UpdateActionHandler(activity, it, { update -> activity.exportUpdate(update) }, { dialog = it }) }
    }
    val newVersion = state.updates.any { it.timestamp > DeviceInfoUtils.buildDateTimestamp }
    val hasPausedOrFailedUpdate = (controller?.updates ?: state.updates).any { update ->
        when (update.status) {
            UpdateStatus.PAUSED,
            UpdateStatus.PAUSED_ERROR,
            UpdateStatus.VERIFICATION_FAILED,
            UpdateStatus.INSTALLATION_FAILED,
            UpdateStatus.INSTALLATION_SUSPENDED -> true
            else -> false
        }
    }
    val showUpdateAnimation = newVersion || controller?.isBusy == true ||
        hasPausedOrFailedUpdate || state.hasUpdateCheckFailed
    if (changelog) ChangelogSheet { changelog = false }
    dialog?.let { value -> UpdateActionDialog(value) { dialog = null } }

    SettingsScaffold(title = stringResource(R.string.display_name),
        showBackButton = true, onNavigateUp = onBack, startCollapsed = false,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_uwu_more), stringResource(R.string.uwu_more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_preferences)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_uwu_settings), null) },
                        onClick = { menu = false; onPreferences() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.local_update_import)) },
                        enabled = controller != null && !controller.isBusy,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_uwu_upload), null) },
                        onClick = { menu = false; onImport() })
                }
            }
        }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val header: @Composable () -> Unit = {
                UwuHero(showUpdateAnimation = showUpdateAnimation)
                val maintainer = DeviceInfoUtils.maintainer.trim()
                if (maintainer.isNotEmpty()) {
                    val description = "${stringResource(R.string.uwu_maintainer)}: $maintainer"
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_uwu_person), null, Modifier.size(16.dp))
                                Text(maintainer, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                UpdateCheckControl(state.isCheckingForUpdates, newVersion, onRefresh)
            }
            val info: @Composable () -> Unit = {
                SystemInformation()
                SettingsPreferenceGroup {
                    item {
                        PreferenceRow(title = stringResource(R.string.uwu_changelog),
                            summary = "uwuAOSP ${DeviceInfoUtils.buildVersion}",
                            icon = ImageVector.vectorResource(R.drawable.ic_uwu_article), onClick = { changelog = true })
                    }
                }
            }
            val updates: @Composable () -> Unit = {
                items.forEach { item ->
                    key(item.downloadId) {
                        UpdatePackageCard(item) { action ->
                            val update = controller?.getUpdate(item.downloadId)
                            if (update != null && action.enabled) actions?.perform(action, update)
                        }
                    }
                }
            }
            header()
            updates()
            info()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCheckControl(checking: Boolean, found: Boolean, onCheck: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val reference = measurer.measure(stringResource(R.string.uwu_status_reference), MaterialTheme.typography.headlineSmall)
    val label = stringResource(when {
        found -> R.string.uwu_new_version_found
        checking -> R.string.checking_for_updates
        else -> R.string.check_for_updates
    })
    val labelSize = measurer.measure(label, MaterialTheme.typography.labelLarge)
    val height = with(density) { reference.size.height.toDp() }.coerceAtLeast(ButtonDefaults.ExtraSmallContainerHeight)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val contentWidth = with(density) { labelSize.size.width.toDp() } + ButtonDefaults.ExtraSmallIconSize +
        ButtonDefaults.ExtraSmallIconSpacing + padding.calculateLeftPadding(direction) + padding.calculateRightPadding(direction)
    val width = with(density) { reference.size.width.toDp() }.coerceAtLeast(contentWidth)
    Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
        if (found) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.width(width).height(height)) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        } else {
            FilledTonalButton(onClick = onCheck, enabled = !checking,
                shapes = ButtonDefaults.shapes(), contentPadding = padding,
                modifier = Modifier.width(width).height(height)) {
                if (checking) LoadingIndicator(Modifier.size(ButtonDefaults.ExtraSmallIconSize))
                else Icon(painterResource(R.drawable.ic_uwu_refresh), null, Modifier.size(ButtonDefaults.ExtraSmallIconSize))
                Text(label, Modifier.padding(start = ButtonDefaults.ExtraSmallIconSpacing), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SystemInformation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val unknown = stringResource(R.string.uwu_unknown)
    val rows = listOf(
        Triple(R.string.uwu_version, DeviceInfoUtils.buildVersion, R.drawable.ic_uwu_version),
        Triple(R.string.uwu_device, "${Build.MODEL} (${DeviceInfoUtils.device})", R.drawable.ic_uwu_device),
        Triple(R.string.uwu_android, DeviceInfoUtils.androidVersion, R.drawable.ic_uwu_android),
        Triple(R.string.uwu_security, DeviceInfoUtils.buildSecurityPatch.takeIf { it.isNotBlank() }
            ?.let { StringUtil.formatSecurityPatch(context, it) }.orEmpty(), R.drawable.ic_uwu_security),
        Triple(R.string.uwu_build_date, if (DeviceInfoUtils.buildDateTimestamp > 0)
            StringUtil.formatBuildDate(context, DeviceInfoUtils.buildDateTimestamp) else "", R.drawable.ic_uwu_calendar),
    )
    SettingsPreferenceGroup {
        rows.forEach { (title, value, icon) -> item {
            PreferenceRow(title = stringResource(title), summary = value.ifBlank { unknown },
                icon = ImageVector.vectorResource(icon))
        } }
    }
}
