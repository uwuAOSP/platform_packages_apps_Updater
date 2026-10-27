/*
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import org.uwuaosp.updater.R
import org.uwuaosp.updater.UpdaterApplication
import org.uwuaosp.updater.data.ChangelogDocument
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as UpdaterApplication).changelogRepository
    val version = DeviceInfoUtils.buildVersion
    var document by remember(version) { mutableStateOf<ChangelogDocument?>(null) }
    var loading by remember(version) { mutableStateOf(true) }
    LaunchedEffect(version) {
        try { document = repository.load(version) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { document = null }
        finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.uwu_changelog), style = MaterialTheme.typography.headlineSmall)
            Text("uwuAOSP $version", style = MaterialTheme.typography.titleLarge)
            if (loading) {
                LoadingIndicator(Modifier.padding(bottom = 24.dp))
            } else if (document == null) {
                Text(stringResource(R.string.uwu_changelog_missing), Modifier.padding(bottom = 32.dp))
            } else {
                val content = document!!
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(content.blocks) { block ->
                        Text(block.text, style = when (block.heading) {
                            2 -> MaterialTheme.typography.titleLarge
                            in 3..6 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.bodyMedium
                        }, fontFamily = if (block.code) FontFamily.Monospace else null)
                    }
                    item {
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, content.url.toUri())
                            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                        }, modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(stringResource(R.string.uwu_view_source))
                        }
                    }
                }
            }
        }
    }
}
