package com.damn.aisuper.modules

import android.content.Context
import androidx.activity.ComponentActivity

object AndroidAppContextHolder {
    var appContext: Context? = null
    var currentActivity: ComponentActivity? = null
}

