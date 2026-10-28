/*
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.uwuaosp.compose"
    compileSdk = 37

    defaultConfig { minSdk = 26 }

    sourceSets.getByName("main") {
        manifest.srcFile("../../../../../uwu-sdk/uwuCompose/AndroidManifest.xml")
        kotlin.setSrcDirs(listOf("../../../../../uwu-sdk/uwuCompose/src"))
        res.setSrcDirs(listOf("../../../../../uwu-sdk/uwuCompose/res"))
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("androidx.compose.animation:animation:1.12.0-alpha01")
    api("androidx.compose.foundation:foundation:1.12.0-alpha01")
    api("androidx.compose.foundation:foundation-layout:1.12.0-alpha01")
    api(libs.androidx.compose.material3)
    api("androidx.compose.material:material-icons-extended:1.7.0-alpha01")
    api("androidx.compose.runtime:runtime:1.12.0-alpha01")
    api("androidx.compose.runtime:runtime-saveable:1.12.0-alpha01")
    api("androidx.compose.ui:ui:1.12.0-alpha01")
    api("androidx.compose.ui:ui-graphics:1.12.0-alpha01")
}
