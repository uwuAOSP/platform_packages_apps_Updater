/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.uwuaosp.updater.util

import android.ota.nano.OtaPackageMetadata.OtaMetadata
import android.os.Build
import org.uwuaosp.updater.deviceinfo.DeviceInfoUtils
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class OtaMetadataParser @Throws(IOException::class) constructor(file: File) {
    val sdkLevel: Int
    val securityPatchLevel: String
    val timestamp: Long
    val isABUpdate: Boolean
    val version: String

    init {
        val metadata = ZipFile(file).use { readMetadata(it) }
        val precondition = metadata.precondition ?: throw IOException("Missing OTA precondition")
        val compatibleDevices = setOf(Build.DEVICE, DeviceInfoUtils.device).filter { it.isNotBlank() }
        if (precondition.device.none { it in compatibleDevices }) throw IOException("OTA is for another device")
        if (precondition.build.isNotEmpty() && Build.FINGERPRINT !in precondition.build) {
            throw IOException("OTA requires another source build")
        }
        if (precondition.buildIncremental.isNotBlank() && precondition.buildIncremental != Build.VERSION.INCREMENTAL) {
            throw IOException("OTA requires another source incremental")
        }
        if (metadata.wipe || metadata.downgrade || metadata.splDowngrade) {
            throw IOException("Wipe and downgrade packages are not supported")
        }
        isABUpdate = when (metadata.type) {
            OtaMetadata.AB -> true
            OtaMetadata.BLOCK -> false
            // OtaMetadata.BRICK -> false
            else -> throw IOException("Unsupported OTA type: ${metadata.type}")
        }

        if (isABUpdate != DeviceInfoUtils.isABDevice) throw IOException("OTA type does not match this device")
        val postcondition = metadata.postcondition ?: throw IOException("Missing OTA postcondition")
        sdkLevel = postcondition.sdkLevel.toIntOrNull() ?: throw IOException("Invalid OTA SDK level")
        securityPatchLevel = postcondition.securityPatchLevel
        timestamp = postcondition.timestamp
        version = postcondition.buildIncremental
        if (timestamp <= 0 || timestamp < DeviceInfoUtils.buildDateTimestamp || sdkLevel < DeviceInfoUtils.sdkLevel) {
            throw IOException("OTA downgrade is not allowed")
        }
        if (Build.VERSION.SECURITY_PATCH.isNotBlank() && securityPatchLevel < Build.VERSION.SECURITY_PATCH) {
            throw IOException("OTA security patch downgrade is not allowed")
        }
    }

    companion object {
        private const val METADATA_PROTO_NAME = "META-INF/com/android/metadata.pb"

        @Throws(IOException::class)
        private fun readMetadata(zipFile: ZipFile): OtaMetadata {
            val entry = zipFile.getEntry(METADATA_PROTO_NAME)
                ?: throw IOException("Couldn't find $METADATA_PROTO_NAME in ${zipFile.name}")

            return zipFile.getInputStream(entry).use { input ->
                val bytes = input.readNBytes(1_048_577)
                if (bytes.size > 1_048_576) throw IOException("OTA metadata exceeds size limit")
                OtaMetadata.parseFrom(bytes)
            }
        }
    }
}
