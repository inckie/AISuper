package com.damn.aisuper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.damn.aisuper.applet.AndroidAppletScanner
import com.damn.aisuper.applet.AndroidShortcutHelper
import com.damn.aisuper.applet.AppletEntry
import com.damn.aisuper.applet.AppletProvider
import com.damn.aisuper.applet.ResolvedApplet
import com.damn.aisuper.applet.decodeAndroidAppletRef
import com.damn.aisuper.host.ActiveAppletSession
import com.damn.aisuper.host.AppletHostActions
import com.damn.aisuper.host.AppletHostState
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var activeSession by mutableStateOf<ActiveAppletSession?>(null)
    private var showPicker by mutableStateOf(true)
    private var sessionCounter by mutableLongStateOf(0L)
    private var hostState by mutableStateOf(
        AppletHostState(
            canSelectDefaultDirectory = true,
            canPickExternalFolder = true,
            canPickExternalFile = true,
            canPinHomeScreenShortcut = false
        )
    )

    private val selectDefaultDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                AndroidAppletScanner.setDefaultDirectoryTreeUri(this, uri)
                refreshDirectoryScan()
            }
        }

    private val pickExternalFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            AndroidAppletScanner.resolvePickedFolderTreeUri(this@MainActivity, uri)
                        }
                    }.onSuccess { resolved ->
                        launchResolvedApplet(resolved)
                    }.onFailure { err ->
                        hostState = hostState.copy(
                            scanError = err.message ?: "Failed to open selected folder."
                        )
                    }
                }
            }
        }

    private val pickExternalZipLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            AndroidAppletScanner.resolvePickedDocumentUri(this@MainActivity, uri)
                        }
                    }.onSuccess { resolved ->
                        launchResolvedApplet(resolved)
                    }.onFailure { err ->
                        hostState = hostState.copy(
                            scanError = err.message ?: "Failed to open selected .zip applet."
                        )
                    }
                }
            }
        }

    private val hostActions = object : AppletHostActions {
        override fun refreshDirectory() {
            refreshDirectoryScan()
        }

        override fun openApplet(entry: AppletEntry) {
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val ref = decodeAndroidAppletRef(entry.sourceRef)
                        AndroidAppletScanner.resolveRef(this@MainActivity, ref)
                    }
                }.onSuccess { resolved ->
                    launchResolvedApplet(resolved)
                }.onFailure { err ->
                    hostState = hostState.copy(
                        scanError = "Failed to open '${entry.name}': ${err.message}"
                    )
                }
            }
        }

        override fun selectDefaultDirectory() {
            selectDefaultDirLauncher.launch(null)
        }

        override fun pickExternalFolder() {
            pickExternalFolderLauncher.launch(null)
        }

        override fun pickExternalFile() {
            pickExternalZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }

        override fun pinShortcutToHomeScreen(entry: AppletEntry, provider: AppletProvider) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    AndroidShortcutHelper.pinToHomeScreen(this@MainActivity, entry, provider)
                }
                val msg = result.fold(
                    onSuccess = { it },
                    onFailure = { it.message ?: "Unable to create home screen shortcut." }
                )
                hostState = hostState.copy(statusMessage = msg)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        override fun reloadActiveApplet() {
            val current = activeSession ?: return
            sessionCounter += 1L
            activeSession = current.copy(sessionKey = sessionCounter)
        }

        override fun clearStatusMessage() {
            hostState = hostState.copy(statusMessage = null, scanError = null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeSession?.let { session ->
            outState.putString(STATE_ACTIVE_REF, session.entry.sourceRef)
            session.initialFeatureId?.let { outState.putString(STATE_FEATURE_ID, it) }
        }
        outState.putBoolean(STATE_SHOW_PICKER, showPicker)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AndroidAppContextHolder.appContext = applicationContext
        AndroidAppContextHolder.currentActivity = this

        hostState = hostState.copy(
            defaultDirectoryLabel = AndroidAppletScanner.getDefaultDirectoryDisplay(this),
            canPinHomeScreenShortcut = AndroidShortcutHelper.canPinShortcuts(this)
        )

        val savedRef = savedInstanceState?.getString(STATE_ACTIVE_REF)
        val savedFeature = savedInstanceState?.getString(STATE_FEATURE_ID)
        val savedShowPicker = savedInstanceState?.getBoolean(STATE_SHOW_PICKER, true) ?: true

        if (savedRef != null) {
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val ref = decodeAndroidAppletRef(savedRef)
                        AndroidAppletScanner.resolveRef(this@MainActivity, ref)
                    }
                }.onSuccess { resolved ->
                    sessionCounter += 1L
                    activeSession = ActiveAppletSession(
                        entry = resolved.entry,
                        provider = resolved.provider,
                        initialFeatureId = savedFeature,
                        sessionKey = sessionCounter
                    )
                    showPicker = savedShowPicker
                }
            }
        }

        refreshDirectoryScan()
        handleIntent(intent)

        setContent {
            BackHandler(enabled = showPicker && activeSession != null) {
                showPicker = false
            }

            App(
                activeSession = activeSession,
                hostState = hostState,
                hostActions = hostActions,
                showPicker = showPicker,
                onOpenPicker = {
                    refreshDirectoryScan()
                    showPicker = true
                },
                onClosePicker = {
                    if (activeSession != null) showPicker = false
                },
                onCloseApplet = {
                    activeSession = null
                    showPicker = true
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val featureId = intent.getStringExtra("featureId")
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    lifecycleScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                AndroidAppletScanner.resolvePickedDocumentUri(this@MainActivity, uri)
                            }
                        }.onSuccess { resolved ->
                            launchResolvedApplet(resolved, featureId)
                        }.onFailure { err ->
                            hostState = hostState.copy(
                                scanError = err.message ?: "Failed to open applet file."
                            )
                        }
                    }
                }
            }

            AndroidShortcutHelper.ACTION_LAUNCH_APPLET -> {
                val rawRef = intent.getStringExtra(AndroidShortcutHelper.EXTRA_APPLET_REF) ?: return
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val ref = decodeAndroidAppletRef(rawRef)
                            AndroidAppletScanner.resolveRef(this@MainActivity, ref)
                        }
                    }.onSuccess { resolved ->
                        launchResolvedApplet(resolved, featureId)
                    }.onFailure { err ->
                        showPicker = true
                        hostState = hostState.copy(
                            scanError = "Shortcut applet is unavailable: ${err.message}"
                        )
                    }
                }
            }

            "com.damn.aisuper.ACTION_PICK_APPLET" -> {
                showPicker = true
                pickExternalZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
        }
    }

    private fun refreshDirectoryScan() {
        val dirLabel = AndroidAppletScanner.getDefaultDirectoryDisplay(this)
        hostState = hostState.copy(
            defaultDirectoryLabel = dirLabel,
            isScanning = true,
            scanError = null
        )
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AndroidAppletScanner.scanDefaultDirectory(this@MainActivity) }
            }
            hostState = result.fold(
                onSuccess = { list ->
                    hostState.copy(
                        defaultDirectoryLabel = AndroidAppletScanner.getDefaultDirectoryDisplay(this@MainActivity),
                        discoveredApplets = list,
                        isScanning = false,
                        scanError = null
                    )
                },
                onFailure = { err ->
                    hostState.copy(
                        isScanning = false,
                        scanError = err.message ?: "Failed to scan applets directory."
                    )
                }
            )
        }
    }

    private fun launchResolvedApplet(resolved: ResolvedApplet, initialFeatureId: String? = null) {
        sessionCounter += 1L
        activeSession = ActiveAppletSession(
            entry = resolved.entry,
            provider = resolved.provider,
            initialFeatureId = initialFeatureId,
            sessionKey = sessionCounter
        )
        showPicker = false
        hostState = hostState.copy(scanError = null, statusMessage = null)

        lifecycleScope.launch(Dispatchers.IO) {
            AndroidShortcutHelper.pushDynamicLauncherShortcut(
                context = this@MainActivity,
                entry = resolved.entry,
                provider = resolved.provider
            )
        }
    }

    override fun onDestroy() {
        if (AndroidAppContextHolder.currentActivity === this) {
            AndroidAppContextHolder.currentActivity = null
        }
        super.onDestroy()
    }

    companion object {
        private const val STATE_ACTIVE_REF = "activeAppletRef"
        private const val STATE_FEATURE_ID = "initialFeatureId"
        private const val STATE_SHOW_PICKER = "showPicker"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
