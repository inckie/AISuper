package com.damn.aisuper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.damn.aisuper.applet.AndroidZipAppletProvider
import com.damn.aisuper.applet.AppletProvider
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var customAppletProvider by mutableStateOf<AppletProvider?>(null)
    private var customAppletPath: String? = null

    private val pickAppletLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleAppletUri(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("customAppletPath", customAppletPath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AndroidAppContextHolder.appContext = applicationContext
        AndroidAppContextHolder.currentActivity = this

        savedInstanceState?.getString("customAppletPath")?.let { path ->
            val file = File(path)
            if (file.exists()) {
                customAppletPath = path
                customAppletProvider = AndroidZipAppletProvider(file)
            }
        }

        handleIntent(intent)

        setContent {
            val entryPath = if (customAppletProvider != null) "applet.json" else "files/applet.json"
            App(customProvider = customAppletProvider, entryPath = entryPath)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    handleAppletUri(uri)
                }
            }
            "com.damn.aisuper.ACTION_PICK_APPLET" -> {
                pickAppletLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
        }
    }

    private fun handleAppletUri(uri: Uri) {
        try {
            val tempFile = File(cacheDir, "temp_applet.zip")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            customAppletPath = tempFile.absolutePath
            customAppletProvider = AndroidZipAppletProvider(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            // Optionally set an error state here or let App() handle the failure gracefully
        }
    }

    override fun onDestroy() {
        if (AndroidAppContextHolder.currentActivity === this) {
            AndroidAppContextHolder.currentActivity = null
        }
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}