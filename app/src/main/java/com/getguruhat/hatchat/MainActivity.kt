package com.getguruhat.hatchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.getguruhat.hatchat.ui.InstagramScreen
import com.getguruhat.hatchat.ui.WebInbox
import com.getguruhat.hatchat.ui.WebInboxScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getguruhat.hatchat.ui.SettingsScreen
import com.getguruhat.hatchat.update.UpdateDialog
import com.getguruhat.hatchat.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val vm: InboxViewModel by viewModels()
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    // Lives on the activity so a notification tap (onNewIntent) can switch tabs.
    private var tab by mutableIntStateOf(0)

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tab = savedInstanceState?.getInt(STATE_TAB) ?: tabFrom(intent) ?: 0

        val pickFiles = { intent: Intent, callback: ValueCallback<Array<Uri>> ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            runCatching { filePicker.launch(intent) }.onFailure {
                callback.onReceiveValue(null)
                fileCallback = null
            }
            Unit
        }
        // One WebView per tab for the whole session, so switching tabs doesn't reload them.
        // Inspectable over USB (chrome://inspect) for troubleshooting; this needs the phone's
        // own USB debugging to be on and the computer to be approved, so it is off for most people.
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        val instagram = WebInbox.instagram(this, pickFiles)
        val messenger = WebInbox.messenger(this, pickFiles)
        instagram.onUnreadCount = { MessageAlerts.onUnreadCount(this, MessageAlerts.Service.Instagram, it) }
        messenger.onUnreadCount = { MessageAlerts.onUnreadCount(this, MessageAlerts.Service.Messenger, it) }
        val swipeTo: (Int) -> Unit = { step -> if (vm.swipeTabs) { tab = (tab + step).coerceIn(0, 2) } }
        instagram.onSwipe = swipeTo
        messenger.onSwipe = swipeTo

        askForNotificationsOnce()

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
                val updates: UpdateViewModel = viewModel()
                val updateState by updates.state.collectAsStateWithLifecycle()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updates.onResume() }
                UpdateDialog(updateState, onUpdate = updates::update, onDismiss = updates::dismiss)

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
                    // imePadding shrinks the tab above the keyboard, so the typing box stays visible.
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        // Both web tabs stay loaded (hidden when not selected) so they can
                        // notice new messages; the other screens draw on top of them.
                        if (vm.instagramWebMode) WebInboxScreen(instagram, visible = tab == 0)
                        WebInboxScreen(messenger, vm.messengerDesktopMode, visible = tab == 1)
                        val overlay = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .swipeTabs(vm.swipeTabs, swipeTo)
                        if (tab == 0 && !vm.instagramWebMode) {
                            Box(overlay) { InstagramScreen(vm, onOpenSettings = { tab = 2 }) }
                        }
                        if (tab == 2) {
                            Box(overlay) {
                                SettingsScreen(
                                    vm,
                                    onSignOutInstagram = instagram::signOut,
                                    onClearMessengerLogin = messenger::signOut,
                                    onAlertsEnabled = ::askForNotifications,
                                    onThemeChanged = ::recreate,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Light/Dark override: switches the night resources, so both the app colours and the web
    // pages (prefers-color-scheme follows the app theme) change together.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppTheme.wrap(base))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { tab = it }
    }

    override fun onStart() {
        super.onStart()
        MessageAlerts.onAppVisible(this)
    }

    override fun onStop() {
        super.onStop()
        MessageAlerts.appVisible = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
    }

    private fun tabFrom(intent: Intent?) =
        intent?.getIntExtra(MessageAlerts.EXTRA_TAB, -1)?.takeIf { it >= 0 }

    /** Android 13+ needs permission to show notifications; ask on first launch only. */
    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(PREF_ASKED_NOTIFICATIONS, true).apply()
        askForNotifications()
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !MessageAlerts.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val PREF_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}

/** Sideways drag on the native screens (Settings, key-mode inbox) switches tabs, like on the web tabs. */
private fun Modifier.swipeTabs(enabled: Boolean, onSwipe: (Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                val threshold = 90.dp.toPx()
                if (total <= -threshold) onSwipe(1) else if (total >= threshold) onSwipe(-1)
            },
        ) { _, amount -> total += amount }
    }
