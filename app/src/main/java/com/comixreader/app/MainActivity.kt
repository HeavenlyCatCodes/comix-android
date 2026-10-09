package com.comixreader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comixreader.app.ui.ComixNavHost
import com.comixreader.app.ui.theme.ComixTheme

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = slugFrom(intent)
        val container = (application as ComixApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            ComixTheme(settings.theme) {
                ComixNavHost(
                    deepLinkSlug = deepLink.value,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        slugFrom(intent)?.let { deepLink.value = it }
    }

    /** `https://comix.to/title/<slug>[/...]` -> `<slug>` */
    private fun slugFrom(intent: Intent?): String? {
        val segments = intent?.data?.pathSegments ?: return null
        return if (segments.size >= 2 && segments[0] == "title") segments[1] else null
    }
}
