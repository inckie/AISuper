package com.damn.aisuper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AndroidAppContextHolder.appContext = applicationContext
        AndroidAppContextHolder.currentActivity = this

        setContent {
            App()
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