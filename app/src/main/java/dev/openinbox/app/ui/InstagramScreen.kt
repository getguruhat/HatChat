package dev.openinbox.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openinbox.app.InboxViewModel
import dev.openinbox.app.data.InstagramApi
import dev.openinbox.app.data.isMe

@Composable
fun InstagramScreen(vm: InboxViewModel, onOpenSettings: () -> Unit) {
    when {
        !vm.hasToken -> NoTokenPrompt(onOpenSettings)
        vm.openConversation != null -> ChatView(vm, vm.openConversation!!)
        else -> ConversationList(vm)
    }
}

@Composable
private fun NoTokenPrompt(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Instagram DMs", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your Instagram access key to read and answer DMs. " +
                "It's encrypted and stays on this phone.",
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("Add key") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationList(vm: InboxViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(vm.me?.username?.let { "@$it" } ?: "Instagram") },
            actions = {
                IconButton(onClick = vm::loadInbox) { Icon(Icons.Default.Refresh, "Refresh") }
            },
        )
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(vm)
        if (!vm.loading && vm.conversations.isEmpty()) {
            Text("No conversations yet.", Modifier.padding(24.dp))
        }
        LazyColumn {
            items(vm.conversations, key = { it.id }) { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.open(c) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(c.otherUsername?.let { "@$it" } ?: "Conversation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatTime(c.updatedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatView(vm: InboxViewModel, convo: InstagramApi.Conversation) {
    BackHandler { vm.closeConversation() }
    var draft by remember { mutableStateOf("") }
    var humanAgent by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(convo.otherUsername?.let { "@$it" } ?: "Conversation") },
            navigationIcon = {
                IconButton(onClick = vm::closeConversation) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
        ErrorBanner(vm)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "Instagram only shows the last 20 messages here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(vm.messages, key = { it.id }) { m -> Bubble(m, mine = vm.me?.isMe(m.fromId) == true) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = humanAgent, onCheckedChange = { humanAgent = it })
            Text("Reply after 24h (Human Agent, up to 7 days)", style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 4,
            )
            IconButton(
                enabled = draft.isNotBlank(),
                onClick = { vm.send(draft.trim(), humanAgent) { draft = "" } },
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }
}

@Composable
private fun Bubble(m: InstagramApi.Message, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                m.text,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ErrorBanner(vm: InboxViewModel) {
    val msg = vm.error ?: return
    Text(
        msg,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable { vm.error = null }
            .padding(12.dp),
    )
}

/** Graph API returns either ISO-8601 or Unix seconds; show something readable for both. */
private fun formatTime(raw: String): String {
    raw.toLongOrNull()?.let {
        val ms = if (it < 10_000_000_000L) it * 1000 else it
        return java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ms))
    }
    return raw.replace("T", " ").substringBefore("+")
}
