@file:OptIn(ExperimentalMaterial3Api::class)

package com.comixreader.app.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comixreader.app.ComixApp
import com.comixreader.app.network.SilentChallengeSolver
import com.comixreader.app.network.Site
import com.comixreader.app.network.WebViewSetup
import com.comixreader.app.ui.theme.ComixTheme
import kotlinx.coroutines.delay

/**
 * A visible browser on comix.to. In challenge mode it waits for the user to
 * pass the Cloudflare check, notices the new clearance cookie, and closes
 * itself with RESULT_OK so the screen that was blocked can retry.
 */
class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL) ?: Site.BASE_URL
        val challenge = intent.getBooleanExtra(EXTRA_CHALLENGE, false)
        val container = (application as ComixApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            ComixTheme(settings.theme) {
                Surface {
                    BrowserScreen(
                        startUrl = url,
                        challenge = challenge,
                        clearance = { container.http.cookies.get(Site.BASE_URL, SilentChallengeSolver.CLEARANCE_COOKIE) },
                        onSolved = {
                            container.http.cookies.flush()
                            container.http.solver.resetCooldown()
                            Toast.makeText(this, "Verification passed", Toast.LENGTH_SHORT).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onClose = { done ->
                            container.http.cookies.flush()
                            setResult(if (done) Activity.RESULT_OK else Activity.RESULT_CANCELED)
                            finish()
                        },
                        onOpenExternally = { current ->
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current))) }
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_CHALLENGE = "challenge"

        fun intent(context: Context, url: String, challenge: Boolean): Intent =
            Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_CHALLENGE, challenge)
    }

    /** Launch with the blocked URL; the result is true when the user got through. */
    class SolveChallenge : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String) = intent(context, input, challenge = true)
        override fun parseResult(resultCode: Int, intent: Intent?) = resultCode == Activity.RESULT_OK
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
    startUrl: String,
    challenge: Boolean,
    clearance: () -> String?,
    onSolved: () -> Unit,
    onClose: (done: Boolean) -> Unit,
    onOpenExternally: (String) -> Unit,
) {
    var title by remember { mutableStateOf(if (challenge) "Verify you are human" else "comix.to") }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val initialClearance = remember { clearance() }

    if (challenge) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(700)
                val now = clearance()
                if (now != null && now != initialClearance && progress >= 100) {
                    delay(600) // let the page settle so the cookie is stored
                    onSolved()
                    break
                }
            }
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack() && !challenge) view.goBack() else onClose(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onClose(false) }) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                    IconButton(onClick = { onOpenExternally(currentUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
                    }
                    if (challenge) TextButton(onClick = { onClose(true) }) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (challenge) {
                Text(
                    "Complete the Cloudflare check below. This screen closes by itself once it passes.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        WebViewSetup.configure(this)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                currentUrl = url
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                view.title?.takeIf { it.isNotBlank() && !challenge }?.let { title = it }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(startUrl)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
