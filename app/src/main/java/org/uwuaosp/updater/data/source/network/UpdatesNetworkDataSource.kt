/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.data.source.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.uwuaosp.updater.R
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdatesNetworkDataSource(private val context: Context) {
    private val serverUrl: String
        get() {
            val base = context.getString(R.string.updater_server_url).trim()
            check(base.isNotEmpty()) { "Update source is not configured" }
            require(base.startsWith("https://")) {
                "Update server URL must use HTTPS: $base"
            }
            require(DeviceInfoUtils.device.isNotBlank()) { "Missing ro.uwu.device" }
            require(DeviceInfoUtils.releaseType.isNotBlank()) { "Missing ro.uwu.releasetype" }
            return base
                .replace("{device}", DeviceInfoUtils.device)
                .replace("{type}", DeviceInfoUtils.releaseType.lowercase())
                .replace("{incr}", DeviceInfoUtils.buildVersionIncremental)
        }

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun fetchUpdates(): List<NetworkUpdate> {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP status: ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val source = body.source()
            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                throw IOException("Update response exceeds $MAX_RESPONSE_BYTES bytes")
            }
            source.buffer.readUtf8()
        }

        val updates = Json.decodeFromString<List<NetworkUpdate>>(responseBody).onEach { it.validate() }
        require(updates.map { it.files.single().sha256.lowercase() }.distinct().size == updates.size) {
            "Duplicate OTA artifact identity"
        }
        return updates
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1024L * 1024L

        @JvmStatic
        fun isConfigured(context: Context): Boolean =
            context.getString(R.string.updater_server_url).isNotBlank()
    }
}
