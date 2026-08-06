/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.client

/**
 * Platform detection utilities for cross-platform compatibility.
 * Supports Android (PojavLauncher / ZalithLauncher), Linux, Windows, and macOS.
 */
object PlatformUtils {

    /**
     * Whether the current runtime is Android.
     */
    val IS_ANDROID: Boolean by lazy {
        val vendor = System.getProperty("java.vendor", "")
        val runtimeName = System.getProperty("java.runtime.name", "")
        val javaVm = System.getProperty("java.vm.name", "")
        val androidRoot = System.getenv("ANDROID_ROOT") ?: System.getenv("ANDROID_DATA")

        vendor.contains("Android", ignoreCase = true) ||
        runtimeName.contains("Android", ignoreCase = true) ||
        javaVm.contains("Dalvik", ignoreCase = true) ||
        androidRoot != null ||
        try {
            Class.forName("android.os.Build")
            true
        } catch (e: ClassNotFoundException) {
            false
        } ||
        System.getProperty("pojav.launcher", "").isNotEmpty() ||
        System.getProperty("zalith.launcher", "").isNotEmpty()
    }

    /**
     * Whether we are running on a PojavLauncher-based launcher (includes ZalithLauncher).
     */
    val IS_POJAV: Boolean by lazy {
        IS_ANDROID || try {
            Class.forName("net.kdt.pojavlaunch.PojavLaunchUtils")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    enum class PlatformType {
        ANDROID, WINDOWS, LINUX, MACOS, UNKNOWN
    }

    val PLATFORM_TYPE: PlatformType by lazy {
        when {
            IS_ANDROID -> PlatformType.ANDROID
            System.getProperty("os.name", "").contains("Windows", ignoreCase = true) -> PlatformType.WINDOWS
            System.getProperty("os.name", "").contains("Linux", ignoreCase = true) -> PlatformType.LINUX
            System.getProperty("os.name", "").contains("Mac", ignoreCase = true) -> PlatformType.MACOS
            else -> PlatformType.UNKNOWN
        }
    }

    val SUPPORTS_JCEF: Boolean get() = !IS_ANDROID && !IS_POJAV
    val SUPPORTS_DISCORD_IPC: Boolean get() = !IS_ANDROID && !IS_POJAV
    val SUPPORTS_DEEP_LEARNING: Boolean get() = !IS_ANDROID
    val SUPPORTS_NATIVE_DIALOGS: Boolean get() = !IS_ANDROID && !IS_POJAV
    val IS_TOUCH_DEVICE: Boolean get() = IS_ANDROID

    fun getPlatformDisplayName(): String = when (PLATFORM_TYPE) {
        PlatformType.ANDROID -> "Android (${System.getProperty("os.name", "Unknown")})"
        PlatformType.WINDOWS -> "Windows"
        PlatformType.LINUX -> "Linux"
        PlatformType.MACOS -> "macOS"
        PlatformType.UNKNOWN -> "Unknown"
    }

    val IS_ZALITH: Boolean by lazy {
        try {
            Class.forName("com.zalith.launcher.ZalithLauncher") != null
        } catch (e: ClassNotFoundException) {
            System.getProperty("zalith.launcher", "").isNotEmpty()
        }
    }

    val LAUNCHER_NAME: String by lazy {
        when {
            IS_ZALITH -> "ZalithLauncher"
            IS_POJAV -> "PojavLauncher"
            else -> "Standard"
        }
    }
}
