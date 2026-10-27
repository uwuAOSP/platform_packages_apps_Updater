/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.uwuaosp.updater.R
import org.uwuaosp.updater.updates.action.UpdateAction
import org.uwuaosp.updater.updates.action.UpdateActionType
import org.uwuaosp.updater.updates.state.ProgressState
import org.uwuaosp.updater.updates.state.UpdateItemState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatePackageCard(state: UpdateItemState, onAction: (UpdateAction) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("uwuAOSP ${state.buildVersion}", style = MaterialTheme.typography.titleMedium)
                    Text("${state.fileSize} - ${state.buildDate}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.status.isNotEmpty()) Text(state.status, style = MaterialTheme.typography.bodySmall)
                }
                if (state.actions.overflow.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_uwu_more), stringResource(R.string.uwu_more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            state.actions.overflow.forEach { action ->
                                DropdownMenuItem(text = { Text(action.type.title(context)) }, enabled = action.enabled,
                                    onClick = { menu = false; onAction(action) },
                                    leadingIcon = { Icon(painterResource(action.type.icon()), null) })
                            }
                        }
                    }
                }
            }
            state.progress?.let { progress -> PackageProgress(progress, state) }
            if (state.progress == null) {
                if (state.securityUpdate.isNotBlank()) Text(state.securityUpdate, style = MaterialTheme.typography.bodySmall)
                Text(if (state.installNote == R.string.list_major_upgrade_recovery_install)
                    stringResource(state.installNote, stringResource(R.string.brand_name))
                    else stringResource(state.installNote), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.actions.secondary?.let { action ->
                    TextButton(onClick = { onAction(action) }, enabled = action.enabled) { Text(action.type.title(context)) }
                }
                Spacer(Modifier.weight(1f))
                val primary = state.actions.primary
                val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    Icon(painterResource(primary.type.icon()), null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(primary.type.title(context))
                }
                if (primary.type == UpdateActionType.PAUSE_DOWNLOAD || primary.type == UpdateActionType.PAUSE_INSTALL) {
                    FilledTonalButton(onClick = { onAction(primary) }, enabled = primary.enabled, content = content)
                } else {
                    Button(onClick = { onAction(primary) }, enabled = primary.enabled, content = content)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PackageProgress(progress: ProgressState, state: UpdateItemState) {
    var foreground by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { foreground = true; onPauseOrDispose { foreground = false } }
    val active = state.actions.primary.type in setOf(UpdateActionType.PAUSE_DOWNLOAD,
        UpdateActionType.PAUSE_INSTALL, UpdateActionType.CANCEL_INSTALL) || progress is ProgressState.Indeterminate
    val stroke = with(LocalDensity.current) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) }
    val speed = if (foreground && active) 40.dp else 0.dp
    when (progress) {
        is ProgressState.Determinate -> {
            val value = animateFloatAsState((progress.percent / 100).coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = 400f), label = "OTA progress")
            LinearWavyProgressIndicator(progress = { value.value }, modifier = Modifier.fillMaxWidth().height(16.dp),
                stroke = stroke, trackStroke = stroke, wavelength = 28.dp, waveSpeed = speed, gapSize = 4.dp, stopSize = 4.dp)
            Text("${progress.percent.toInt()}%", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (progress.downloadedSize.isNotEmpty()) Text(listOf(progress.downloadedSize, progress.eta)
                .filter { it.isNotEmpty() }.joinToString(" - "), style = MaterialTheme.typography.bodySmall)
        }
        ProgressState.Indeterminate -> LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().height(16.dp),
            stroke = stroke, trackStroke = stroke, wavelength = 20.dp, waveSpeed = speed, gapSize = 4.dp)
    }
}

private fun UpdateActionType.icon(): Int = when (this) {
    UpdateActionType.PAUSE_DOWNLOAD, UpdateActionType.PAUSE_INSTALL -> R.drawable.ic_uwu_pause
    UpdateActionType.RESUME_DOWNLOAD, UpdateActionType.RESUME_INSTALL -> R.drawable.ic_uwu_play
    UpdateActionType.REBOOT -> R.drawable.ic_uwu_restart
    UpdateActionType.DELETE, UpdateActionType.CANCEL_DOWNLOAD, UpdateActionType.CANCEL_INSTALL -> R.drawable.ic_uwu_delete
    UpdateActionType.EXPORT -> R.drawable.ic_uwu_download
    UpdateActionType.START_INSTALL -> R.drawable.ic_uwu_check
    UpdateActionType.SHOW_INFO, UpdateActionType.OPEN_GUIDE -> R.drawable.ic_uwu_article
    else -> R.drawable.ic_uwu_cloud
}
