package dev.openinbox.app

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.openinbox.app.ui.InstagramScreen
import dev.openinbox.app.ui.MessengerScreen
import dev.openinbox.app.ui.MessengerWeb
import dev.openinbox.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: InboxViewModel by viewModels()
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // One WebView for the whole session so switching tabs doesn't reload Messenger.
        val messenger = MessengerWeb(this) { intent, callback ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            runCatching { filePicker.launch(intent) }.onFailure {
                callback.onReceiveValue(null)
                fileCallback = null
            }
        }

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val colors = when {
                android.os.Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                android.os.Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, label = { Text("Instagram") },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 },
                                icon = { Icon(Icons.Default.Forum, null) }, label = { Text("Messenger") },
                            )
                            NavigationBarItem(
                                selected = tab == 2, onClick = { tab = 2 },
                                icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") },
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> InstagramScreen(vm, onOpenSettings = { tab = 2 })
                            1 -> MessengerScreen(messenger, vm.messengerDesktopMode)
                            else -> SettingsScreen(vm, onClearMessengerLogin = { MessengerWeb.clearLogin(messenger) })
                        }
                    }
                }
            }
        }
    }
}
