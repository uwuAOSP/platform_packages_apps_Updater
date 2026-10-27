/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.uwuaosp.updater.data.source.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.uwuaosp.updater.data.Update
import org.uwuaosp.updater.misc.Constants
import java.net.URI
import java.time.LocalDate

// Commented out fields below are available in production, but not needed in runtime.
// If you wish to uncomment any of them, please update the README.md to indicate that.

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    // @SerialName("date") val date: String,
    @SerialName("datetime") val datetime: Long,
    @SerialName("files") val files: List<NetworkUpdateFile>,
    @SerialName("type") val type: String,
    @SerialName("version") val version: String,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdateFile(
    // @SerialName("date") val date: String? = null,
    // @SerialName("datetime") val datetime: Long? = null,
    @SerialName("filename") val filename: String,
    // @SerialName("filepath") val filepath: String,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
    // @SerialName("sha1") val sha1: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
    // @SerialName("type") val type: String? = null,
    @SerialName("url") val url: String,
)

private data class PackageFileRange(
    val offset: Long,
    val size: Long,
)

private fun String.parsePackageFileRanges(): Map<String, PackageFileRange> {
    val ranges = linkedMapOf<String, PackageFileRange>()
    split(",").forEach { token ->
        val parts = token.trim().split(":")
        require(parts.size == 3) { "Invalid OTA file range" }
        val name = parts[0].trim()
        val offset = parts[1].trim().toLong()
        val size = parts[2].trim().toLong()
        require(name.isNotBlank() && name !in ranges && offset >= 0 && size > 0) {
            "Invalid or duplicate OTA file range"
        }
        ranges[name] = PackageFileRange(offset, size)
    }
    return ranges
}

fun NetworkUpdate.validate() {
    require(datetime > 0 && version.isNotBlank() && type.isNotBlank()) { "Invalid release metadata" }
    require(files.size == 1) { "Expected exactly one OTA artifact" }
    val file = files.single()
    require(file.filename.isNotBlank() && file.filename == file.filename.substringAfterLast('/')) {
        "Invalid OTA filename"
    }
    require(file.filename != "." && file.filename != ".." && '\\' !in file.filename) {
        "Invalid OTA filename"
    }
    require(file.size > 0 && file.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid OTA hash or size" }
    val uri = URI(file.url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid OTA URL" }
    require(file.osSdkLevel != null && file.osSdkLevel > 0) { "Missing OTA SDK level" }
    file.osPatchLevel?.let { LocalDate.parse(it) }
    file.otaPropertyFiles?.parsePackageFileRanges()?.let { ranges ->
        listOf(Constants.AB_PAYLOAD_METADATA_PATH, Constants.AB_PAYLOAD_BIN_PATH,
            Constants.AB_PAYLOAD_PROPERTIES_PATH).forEach { require(it in ranges) { "Incomplete payload ranges" } }
        ranges.values.forEach {
            require(it.offset < file.size && it.size <= file.size - it.offset) { "OTA range exceeds artifact size" }
        }
        require(ranges.getValue(Constants.AB_PAYLOAD_PROPERTIES_PATH).size <= 65_536) { "Payload properties exceed size limit" }
        require(ranges.getValue(Constants.AB_PAYLOAD_METADATA_PATH).size <= 16_777_216) { "Payload metadata exceeds size limit" }
    }
}

fun NetworkUpdate.toUpdate(): Update {
    val file = files[0]
    val packageFileRanges = file.otaPropertyFiles?.parsePackageFileRanges().orEmpty()
    val payloadMetadataRange = packageFileRanges[Constants.AB_PAYLOAD_METADATA_PATH]
    val payloadRange = packageFileRanges[Constants.AB_PAYLOAD_BIN_PATH]
    val payloadPropertiesRange = packageFileRanges[Constants.AB_PAYLOAD_PROPERTIES_PATH]

    return Update(
        downloadId = file.sha256.lowercase(),
        name = file.filename,
        timestamp = datetime,
        type = type,
        fileSize = file.size,
        downloadUrl = file.url,
        version = version,
        osPatchLevel = file.osPatchLevel,
        osSdkLevel = file.osSdkLevel ?: 0,
        payloadMetadataOffset = payloadMetadataRange?.offset,
        payloadMetadataSize = payloadMetadataRange?.size,
        payloadOffset = payloadRange?.offset,
        payloadSize = payloadRange?.size,
        payloadPropertiesOffset = payloadPropertiesRange?.offset,
        payloadPropertiesSize = payloadPropertiesRange?.size,
        isAvailableOnline = true,
    )
}
