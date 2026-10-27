#!/usr/bin/env bash
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-FileCopyrightText: 2026 The uwuAOSP Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ! -f "$1" ]]; then
    echo "Usage: $0 OTA.zip [SERIAL]" >&2
    exit 1
fi
command -v adb >/dev/null || { echo "adb is required" >&2; exit 1; }
adb_args=()
[[ $# -eq 1 ]] || adb_args=(-s "$2")
adb "${adb_args[@]}" wait-for-device
destination=/sdcard/Download/uwu-local-update.zip
adb "${adb_args[@]}" push "$1" "$destination"
adb "${adb_args[@]}" shell am start -a android.settings.SYSTEM_UPDATE_SETTINGS -p org.uwuaosp.updater
echo "Select Local update and import Download/uwu-local-update.zip on the device."
echo "The updater verifies the signature and device metadata before accepting the package."
