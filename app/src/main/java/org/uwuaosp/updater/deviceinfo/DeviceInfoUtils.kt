/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.uwuaosp.updater.deviceinfo

import android.os.Build
import android.os.SystemProperties
import com.android.settingslib.DeviceInfoUtils as SettingsLibDeviceInfoUtils

object DeviceInfoUtils : SettingsLibDeviceInfoUtils() {

    private const val PROP_AB_DEVICE = "ro.build.ab_update"
    private const val PROP_BUILD_DATE = "ro.build.date.utc"
    private const val PROP_BUILD_VERSION = "ro.uwu.release"
    private const val PROP_BUILD_VERSION_INCREMENTAL = "ro.build.version.incremental"
    private const val PROP_DEVICE = "ro.uwu.device"
    private const val PROP_NEXT_DEVICE = "ro.updater.next_device"
    private const val PROP_RELEASE_TYPE = "ro.uwu.releasetype"
    private const val PROP_UPDATE_RECOVERY = "persist.vendor.recovery_update"

    // Read-only
    val androidVersion: String = Build.VERSION.RELEASE

    val maintainer: String = SystemProperties.get("ro.uwu.maintainer")

    val buildSecurityPatch: String = Build.VERSION.SECURITY_PATCH

    val sdkLevel: Int = Build.VERSION.SDK_INT

    @JvmStatic
    val buildDateTimestamp: Long = SystemProperties.getLong(PROP_BUILD_DATE, 0)

    @JvmStatic
    val buildVersion: String = SystemProperties.get(PROP_BUILD_VERSION, "")

    @JvmStatic
    val buildVersionIncremental: String = SystemProperties.get(PROP_BUILD_VERSION_INCREMENTAL, "")

    @JvmStatic
    val device: String = SystemProperties.get(PROP_NEXT_DEVICE, SystemProperties.get(PROP_DEVICE))

    @JvmStatic
    val isABDevice: Boolean = SystemProperties.getBoolean(PROP_AB_DEVICE, false)

    @JvmStatic
    val releaseType: String = SystemProperties.get(PROP_RELEASE_TYPE)

    // Mutable at runtime
    @JvmStatic
    val isDowngradingAllowed: Boolean
        get() = false

    @JvmStatic
    val isMajorUpdateAllowed: Boolean
        get() = false

    @JvmStatic
    var isRecoveryUpdateEnabled: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATE_RECOVERY, false)
        set(value) = SystemProperties.set(PROP_UPDATE_RECOVERY, value.toString())

}
