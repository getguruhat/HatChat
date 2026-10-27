package com.getguruhat.hatchat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getguruhat.hatchat.data.InstagramApi
import com.getguruhat.hatchat.data.TokenStore
import com.getguruhat.hatchat.data.isMe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InboxViewModel(app: Application) : AndroidViewModel(app) {

    private val store = TokenStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var api: InstagramApi? = null
    private var pollJob: Job? = null

    var hasToken by mutableStateOf(false); private set
    var me by mutableStateOf<InstagramApi.Me?>(null); private set
    var conversations by mutableStateOf<List<InstagramApi.Conversation>>(emptyList()); private set
    var openConversation by mutableStateOf<InstagramApi.Conversation?>(null); private set
    var messages by mutableStateOf<List<InstagramApi.Message>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var tokenSavedAt by mutableStateOf(0L); private set

    var messengerDesktopMode by mutableStateOf(prefs.getBoolean(PREF_DESKTOP, false)); private set

    init {
        store.loadToken()?.let { token ->
            useToken(token)
            viewModelScope.launch {
                // Long-lived tokens expire after 60 days; refresh quietly once a week.
                if (System.currentTimeMillis() - store.savedAt() > REFRESH_AFTER_MS) {
                    runCatching { refreshToken() }
                }
                loadInbox()
            }
        }
    }

    /** Checks the token against Instagram before saving it to the phone. */
    fun saveToken(raw: String, onDone: (Boolean) -> Unit) {
        val token = raw.trim()
        if (token.isEmpty()) return
        viewModelScope.launch {
            loading = true
            try {
                val candidate = InstagramApi(token)
                me = candidate.me()
                store.saveToken(token)
                useToken(token)
                error = null
                loadInbox()
                onDone(true)
            } catch (e: Exception) {
                error = "That key didn't work: ${e.message}"
                onDone(false)
            } finally {
                loading = false
            }
        }
    }

    fun removeToken() {
        closeConversation()
        store.clear()
        api = null
        hasToken = false
        me = null
        conversations = emptyList()
        tokenSavedAt = 0L
    }

    suspend fun refreshToken() {
        val fresh = api?.refreshToken() ?: return
        store.saveToken(fresh)
        useToken(fresh)
    }

    fun refreshTokenNow() {
        viewModelScope.launch {
            try {
                refreshToken()
                error = null
            } catch (e: Exception) {
                error = "Couldn't refresh key: ${e.message}"
            }
        }
    }

    fun loadInbox() {
        val client = api ?: return
        viewModelScope.launch {
            loading = true
            try {
                val who = me ?: client.me().also { me = it }
                conversations = client.conversations(who).sortedByDescending { it.updatedTime }
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun open(conversation: InstagramApi.Conversation) {
        openConversation = conversation
        messages = emptyList()
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadMessages(conversation.id)
                delay(POLL_MS)
            }
        }
    }

    fun closeConversation() {
        pollJob?.cancel()
        openConversation = null
        messages = emptyList()
    }

    fun send(text: String, humanAgent: Boolean, onSent: () -> Unit) {
        val client = api ?: return
        val convo = openConversation ?: return
        val recipient = recipientFor(convo) ?: run {
            error = "Can't tell who to reply to yet — wait for messages to load."
            return
        }
        viewModelScope.launch {
            try {
                client.send(recipient, text, humanAgent)
                onSent()
                loadMessages(convo.id)
            } catch (e: Exception) {
                error = "Not sent: ${e.message}" +
                    if (!humanAgent) " (Instagram only allows replies within 24h of their last message.)" else ""
            }
        }
    }

    fun setDesktopMode(on: Boolean) {
        messengerDesktopMode = on
        prefs.edit().putBoolean(PREF_DESKTOP, on).apply()
    }

    private suspend fun loadMessages(conversationId: String) {
        val client = api ?: return
        try {
            val fresh = client.messages(conversationId)
            if (openConversation?.id == conversationId) messages = fresh
        } catch (e: Exception) {
            error = e.message
        }
    }

    private fun recipientFor(convo: InstagramApi.Conversation): String? {
        convo.otherId?.let { return it }
        val who = me ?: return null
        messages.firstOrNull { !who.isMe(it.fromId) }?.let { return it.fromId }
        return messages.firstOrNull { who.isMe(it.fromId) }?.toId
    }

    private fun useToken(token: String) {
        api = InstagramApi(token)
        hasToken = true
        tokenSavedAt = store.savedAt()
    }

    private companion object {
        const val PREF_DESKTOP = "messenger_desktop_mode"
        const val POLL_MS = 15_000L
        const val REFRESH_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    }
}
