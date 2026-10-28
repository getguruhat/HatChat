package com.getguruhat.hatchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getguruhat.hatchat.R
import com.getguruhat.hatchat.BuildConfig
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.getguruhat.hatchat.InboxViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    vm: InboxViewModel,
    onSignOutInstagram: () -> Unit,
    onClearMessengerLogin: () -> Unit,
    onAlertsEnabled: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    var keyInput by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    var messengerCleared by remember { mutableStateOf(false) }
    var instagramCleared by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                FilterChip(
                    selected = vm.themeMode == mode,
                    onClick = {
                        if (vm.themeMode != mode) {
                            vm.setTheme(mode)
                            onThemeChanged()
                        }
                    },
                    label = { Text(label) },
                )
            }
        }
        Text(
            "System follows your phone. Instagram and Messenger switch too (Facebook needs its own Dark mode set to Automatic).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Swipe between tabs")
                Text(
                    "Swipe left or right to move between Instagram, Messenger and Settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = vm.swipeTabs, onCheckedChange = vm::setSwipeTabsEnabled)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Instagram", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use Instagram website")
                Text(
                    "Shows all your DMs by signing in on instagram.com. Turn off to use a " +
                        "Meta developer key instead (business accounts with App Review only).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = vm.instagramWebMode, onCheckedChange = vm::useInstagramWebsite)
        }
        if (vm.instagramWebMode) {
            OutlinedButton(onClick = { onSignOutInstagram(); instagramCleared = true }) {
                Text("Sign out of Instagram")
            }
            if (instagramCleared) Text("Signed out.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Instagram key", style = MaterialTheme.typography.titleMedium)

            if (vm.hasToken) {
                Text("Connected" + (vm.me?.username?.let { " as @$it" } ?: ""))
                if (vm.tokenSavedAt > 0) {
                    Text(
                        "Key saved ${DateFormat.getDateInstance().format(Date(vm.tokenSavedAt))}. " +
                            "Keys last 60 days; the app refreshes it automatically every week.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::refreshTokenNow) { Text("Refresh key now") }
                    if (!confirmRemove) {
                        TextButton(onClick = { confirmRemove = true }) { Text("Remove key") }
                    } else {
                        Button(onClick = { vm.removeToken(); confirmRemove = false }) { Text("Confirm remove") }
                    }
                }
                Text("Replace with a new key:", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "Paste the Instagram access token from your Meta developer app " +
                        "(see SETUP.md in the project). It's checked with Instagram, then " +
                        "encrypted with the phone's Android Keystore and never leaves this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = keyInput.isNotBlank() && !vm.loading,
                onClick = { vm.saveToken(keyInput) { ok -> if (ok) keyInput = "" } },
            ) { Text(if (vm.loading) "Checking…" else "Save key") }

            ErrorBanner(vm)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Messenger", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Desktop mode")
                Text(
                    "Turn on if Facebook keeps asking you to install the Messenger app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = vm.messengerDesktopMode, onCheckedChange = vm::setDesktopMode)
        }
        OutlinedButton(onClick = { onClearMessengerLogin(); messengerCleared = true }) {
            Text("Sign out of Messenger")
        }
        if (messengerCleared) Text("Signed out.", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Notifications", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Message alerts")
                Text(
                    "Notifies you of new Instagram and Messenger messages while HatChat is running in the " +
                        "background. Android may close HatChat after a while; open it again to keep alerts coming. " +
                        "Alerts never show names or message text.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = vm.messageAlerts,
                onCheckedChange = { on -> vm.setMessageAlertsEnabled(on); if (on) onAlertsEnabled() },
            )
        }

        if (vm.hasToken && !vm.instagramWebMode) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
            Text(
                "Shows Instagram's raw replies, with your key hidden, so you can share them when something looks wrong.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::runDiagnostics) { Text("Run diagnostics") }
                vm.diagnostics?.let { report ->
                    TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
                }
            }
            vm.diagnostics?.let { report ->
                SelectionContainer {
                    Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        AboutSection()
    }
}

/** Same About block as GuruHat's other apps (see AI Meter): who makes it, and a way to support it. */
@Composable
private fun AboutSection() {
    val links = LocalUriHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Image(
            painterResource(R.drawable.about_icon),
            contentDescription = "HatChat icon",
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text("HatChat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Version ${BuildConfig.VERSION_NAME}", color = muted)
        Text("A GuruHat open-source tool", fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 10.dp).fillMaxWidth(0.85f))
        TextButton(onClick = { links.openUri(SUPPORT_URL) }) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Support HatChat", style = MaterialTheme.typography.titleMedium)
        }
        Text("Thank you for supporting this project!", style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { links.openUri("https://guruhat.com/apps/hatchat/") }) { Text("guruhat.com") }
            TextButton(onClick = { links.openUri("https://github.com/getguruhat/HatChat") }) { Text("Source code") }
        }
        Text(
            "Not affiliated with Meta, Instagram or Facebook.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

// GuruHat's donation page, shared with AI Meter.
private const val SUPPORT_URL = "https://www.paypal.com/donate/?hosted_button_id=FHGYWD46722JS"
