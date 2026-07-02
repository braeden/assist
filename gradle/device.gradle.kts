// Device inner-loop tasks so the build tool is the single source of truth for
// install/launch/stop. Applied from the root build.gradle.kts.
//
// Long-running / interactive actions stay as shell scripts (they fit Gradle's
// daemon model poorly): scripts/emulator.sh (boot + poll an AVD),
// scripts/logcat.sh (streaming tail), scripts/enable-service.sh (device-vs-
// emulator branching). All device tasks honor the ANDROID_SERIAL env var, which
// adb reads natively to pick a target when several devices are attached.

import java.util.Properties

val appId = "com.assist"
val launcherActivity = "$appId/.ui.MainActivity"
// phase-03 accessibility service component (created later; referenced by string).
val accessibilityService = "$appId/$appId.service.AssistAccessibilityService"

fun resolveSdkDir(): File {
    System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { return File(it) }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties().apply { localProps.inputStream().use { load(it) } }
        props.getProperty("sdk.dir")?.let { return File(it) }
    }
    error("Android SDK not found. Set ANDROID_HOME or create local.properties with sdk.dir=…")
}

val adbPath: String = File(resolveSdkDir(), "platform-tools/adb").absolutePath

tasks.register<Exec>("listDevices") {
    group = "assist"
    description = "List attached devices/emulators."
    commandLine(adbPath, "devices", "-l")
}

tasks.register<Exec>("launchApp") {
    group = "assist"
    description = "Launch MainActivity on the target device (respects ANDROID_SERIAL)."
    commandLine(adbPath, "shell", "am", "start", "-n", launcherActivity)
}

tasks.register<Exec>("stopApp") {
    group = "assist"
    description = "Force-stop the app on the target device."
    commandLine(adbPath, "shell", "am", "force-stop", appId)
}

tasks.register<Exec>("runApp") {
    group = "assist"
    description = "Install the debug APK, then launch it."
    dependsOn(":app:installDebug")
    commandLine(adbPath, "shell", "am", "start", "-n", launcherActivity)
}

tasks.register<Exec>("enableAccessibility") {
    group = "assist"
    description = "Enable the Assist accessibility service (emulator/rooted only; " +
        "on a locked-down device use scripts/enable-service.sh)."
    commandLine(
        adbPath, "shell",
        "settings put secure enabled_accessibility_services $accessibilityService && " +
            "settings put secure accessibility_enabled 1",
    )
}
