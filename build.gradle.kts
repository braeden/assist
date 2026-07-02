// Top-level build file. Plugin versions are pinned in gradle/libs.versions.toml;
// declared here with `apply false` and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Device inner-loop tasks (assist group): runApp, launchApp, stopApp,
// enableAccessibility, listDevices. See gradle/device.gradle.kts.
apply(from = "gradle/device.gradle.kts")
