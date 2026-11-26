package com.damn.aisuper.host

import com.damn.aisuper.applet.AppletEntry
import com.damn.aisuper.applet.AppletProvider
import com.damn.aisuper.runtime.Applet

data class ActiveAppletSession(
    val entry: AppletEntry,
    val provider: AppletProvider,
    val initialFeatureId: String? = null,
    val providedApplet: Applet? = null,
    val sessionKey: Long = 0L
)

data class AppletHostState(
    val defaultDirectoryLabel: String = "",
    val discoveredApplets: List<AppletEntry> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val canSelectDefaultDirectory: Boolean = false,
    val canPickExternalFolder: Boolean = false,
    val canPickExternalFile: Boolean = false,
    val canPinHomeScreenShortcut: Boolean = false,
    val supportsDragAndDrop: Boolean = false,
    val statusMessage: String? = null
)

interface AppletHostActions {
    fun refreshDirectory() {}
    fun openApplet(entry: AppletEntry) {}
    fun selectDefaultDirectory() {}
    fun pickExternalFolder() {}
    fun pickExternalFile() {}
    fun pinShortcutToHomeScreen(entry: AppletEntry, provider: AppletProvider) {}
    fun reloadActiveApplet() {}
    fun clearStatusMessage() {}
}
