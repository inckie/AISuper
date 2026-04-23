package com.damn.aisuper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.modules.AndroidAppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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