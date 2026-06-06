package com.damn.aisuper.modules.impl.platform.android

import android.content.Context
import androidx.activity.ComponentActivity

object AndroidAppContextHolder {
    var appContext: Context? = null
    var currentActivity: ComponentActivity? = null
}


