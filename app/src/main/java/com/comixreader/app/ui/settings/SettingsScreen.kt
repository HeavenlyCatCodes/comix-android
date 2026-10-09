@file:OptIn(ExperimentalMaterial3Api::class)

package com.comixreader.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comixreader.app.BuildConfig
import com.comixreader.app.data.Catalog
import com.comixreader.app.data.ReaderMode
import com.comixreader.app.data.ThemeMode
import com.comixreader.app.network.Site
import com.comixreader.app.network.UserAgent
import com.comixreader.app.ui.common.appContainer
import com.comixreader.app.ui.web.WebViewActivity

private data class Choice<T>(val value: T, val label: String)

@Composable
fun SettingsScreen() {
    val app = appContainer()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val verify = rememberLauncherForActivityResult(WebViewActivity.SolveChallenge()) { ok ->
        Toast.makeText(context, if (ok) "Cloudflare check passed" else "Check not completed", Toast.LENGTH_SHORT).show()
    }

    val readerModes = listOf(
        Choice(ReaderMode.AUTO, "Automatic (by series type)"),
        Choice(ReaderMode.VERTICAL, "Vertical scroll (webtoon)"),
        Choice(ReaderMode.RIGHT_TO_LEFT, "Paged, right to left"),
        Choice(ReaderMode.LEFT_TO_RIGHT, "Paged, left to right"),
    )
    val ratings = Catalog.contentRatings.map { Choice(it.value, it.label) }
    val themes = listOf(Choice(ThemeMode.SYSTEM, "Follow system"), Choice(ThemeMode.LIGHT, "Light"), Choice(ThemeMode.DARK, "Dark"))
    val posters = listOf(Choice("small", "Small"), Choice("medium", "Medium"), Choice("large", "Large"))

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header("Reading")
            Item("Default reading mode", readerModes.first { it.value == settings.readerMode }.label) { dialog = "reader" }
            Toggle("Keep screen on while reading", settings.keepScreenOn) { v -> app.settings.update { it.copy(keepScreenOn = v) } }
            Toggle("Hide duplicate chapter uploads", settings.hideDuplicateChapters) { v ->
                app.settings.update { it.copy(hideDuplicateChapters = v) }
            }

            Header("Browsing")
            Item("Content rating", ratings.first { it.value == settings.maxContentRating }.label) { dialog = "rating" }
            Item("Cover quality", posters.first { it.value == settings.posterQuality }.label) { dialog = "poster" }
            Item("Theme", themes.first { it.value == settings.theme }.label) { dialog = "theme" }

            Header("Connection")
            Item(
                "Pass Cloudflare check",
                "Open comix.to in a WebView to solve the captcha now, before it blocks you.",
            ) { verify.launch(Site.BASE_URL) }
            Item("Open comix.to in WebView", "Browse the site directly, e.g. to sign in.") {
                context.startActivity(WebViewActivity.intent(context, Site.BASE_URL, challenge = false))
            }
            Item("User agent", UserAgent.value) { dialog = "ua" }
            Item("Refresh API keys", "Use when lists stop loading after a site update.") {
                app.source.forgetKeys()
                Toast.makeText(context, "Keys cleared. They're fetched again on the next load.", Toast.LENGTH_SHORT).show()
            }
            Item("Clear cookies", "Signs you out and drops Cloudflare clearance.") {
                app.http.cookies.clear()
                Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
            }

            Header("About")
            Item("Comix Reader ${BuildConfig.VERSION_NAME}", "Unofficial reader for comix.to. Not affiliated with the site.") {}
        }
    }

    when (dialog) {
        "reader" -> ChoiceDialog("Default reading mode", readerModes, settings.readerMode, { dialog = null }) { v ->
            app.settings.update { it.copy(readerMode = v) }
        }
        "rating" -> ChoiceDialog("Content rating", ratings, settings.maxContentRating, { dialog = null }) { v ->
            app.settings.update { it.copy(maxContentRating = v) }
        }
        "poster" -> ChoiceDialog("Cover quality", posters, settings.posterQuality, { dialog = null }) { v ->
            app.settings.update { it.copy(posterQuality = v) }
        }
        "theme" -> ChoiceDialog("Theme", themes, settings.theme, { dialog = null }) { v ->
            app.settings.update { it.copy(theme = v) }
        }
        "ua" -> UserAgentDialog(settings.userAgent, { dialog = null }) { v ->
            app.settings.update { it.copy(userAgent = v) }
            UserAgent.override = v
            app.http.cookies.clear() // clearance is tied to the old agent
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<Choice<T>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(c.value); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.value == selected, onClick = null)
                        Text(c.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun UserAgentDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User agent") },
        text = {
            Column {
                Text("Leave empty to use this device's WebView agent (recommended). Changing it clears cookies.")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Custom user agent") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
