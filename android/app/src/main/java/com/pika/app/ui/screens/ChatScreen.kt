package com.pika.app.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pika.app.AppManager
import com.pika.app.rust.AppAction
import com.pika.app.rust.ChatMediaAttachment
import com.pika.app.rust.ChatMessage
import com.pika.app.rust.MessageDeliveryState
import com.pika.app.rust.ReactionSummary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Reply
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.pika.app.rust.Screen
import com.pika.app.ui.theme.PikaBlue
import com.pika.app.ui.TestTags
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import org.json.JSONObject

// ─── timeline item types ──────────────────────────────────────────────────────

private sealed class ChatListItem {
    data class Message(val message: ChatMessage) : ChatListItem()
    object NewMessagesDivider : ChatListItem()
}

// ─── quick reactions ──────────────────────────────────────────────────────────

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// ─── ChatScreen ───────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatScreen(
    manager: AppManager,
    chatId: String,
    padding: PaddingValues,
    onOpenCallSurface: (String) -> Unit,
) {
    val chat = manager.state.currentChat
    if (chat == null || chat.chatId != chatId) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Loading chat…")
        }
        return
    }

    var draft by remember { mutableStateOf("") }
    var replyDraft by remember(chat.chatId) { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val newestMessageId = chat.messages.lastOrNull()?.id
    var shouldStickToBottom by remember(chat.chatId) { mutableStateOf(true) }
    var programmaticScrollInFlight by remember { mutableStateOf(false) }
    val isAtBottom by remember(listState) {
        derivedStateOf { listState.isNearBottomForReverseLayout() }
    }

    val capturedUnreadCount = remember(chat.chatId) {
        manager.state.chatList.find { it.chatId == chatId }?.unreadCount?.toInt() ?: 0
    }

    var newMessageCount by remember(chat.chatId) { mutableIntStateOf(0) }
    var prevMessageCount by remember(chat.chatId) { mutableIntStateOf(chat.messages.size) }

    val myPubkey =
        when (val a = manager.state.auth) {
            is com.pika.app.rust.AuthState.LoggedIn -> a.pubkey
            else -> null
        }
    val title = chatTitle(chat, myPubkey)
    val activeCall = manager.state.activeCall
    val callForChat = activeCall?.takeIf { it.chatId == chat.chatId }
    val hasLiveCallElsewhere = activeCall?.let { it.chatId != chat.chatId && it.isLive } ?: false
    val isCallActionDisabled = callForChat == null && hasLiveCallElsewhere
    val messagesById = remember(chat.messages) { chat.messages.associateBy { it.id } }
    val reversed = remember(chat.messages) { chat.messages.asReversed() }
    val reversedIndexById =
        remember(reversed) { reversed.mapIndexed { index, message -> message.id to index }.toMap() }

    val listItems: List<ChatListItem> = remember(reversed, capturedUnreadCount) {
        buildList {
            for ((i, msg) in reversed.withIndex()) {
                if (i == capturedUnreadCount && capturedUnreadCount > 0 && capturedUnreadCount < reversed.size) {
                    add(ChatListItem.NewMessagesDivider)
                }
                add(ChatListItem.Message(msg))
            }
        }
    }

    LaunchedEffect(chat.chatId) {
        if (capturedUnreadCount > 0 && reversed.isNotEmpty()) {
            shouldStickToBottom = false
            val dividerIndex = minOf(capturedUnreadCount, listItems.size - 1)
            listState.scrollToItem(dividerIndex)
        } else if (chat.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
        replyDraft = null
    }

    LaunchedEffect(isAtBottom, listState.isScrollInProgress, programmaticScrollInFlight) {
        if (isAtBottom) {
            shouldStickToBottom = true
            newMessageCount = 0
        } else if (listState.isScrollInProgress && !programmaticScrollInFlight) {
            shouldStickToBottom = false
        }
    }

    LaunchedEffect(newestMessageId) {
        if (newestMessageId == null || !shouldStickToBottom) return@LaunchedEffect
        programmaticScrollInFlight = true
        try {
            listState.animateScrollToItem(0)
        } finally {
            programmaticScrollInFlight = false
        }
    }

    LaunchedEffect(reversed.size) {
        val current = reversed.size
        if (current > prevMessageCount && !shouldStickToBottom) {
            newMessageCount += current - prevMessageCount
        }
        prevMessageCount = current
    }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && shouldStickToBottom) {
            coroutineScope.launch { listState.animateScrollToItem(0) }
        }
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val stack = manager.state.router.screenStack
                            manager.dispatch(AppAction.UpdateScreenStack(stack.dropLast(1)))
                        },
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onOpenCallSurface(chat.chatId) },
                        enabled = !isCallActionDisabled,
                        modifier = Modifier.testTag(
                            if (callForChat?.isLive == true) TestTags.CHAT_CALL_OPEN
                            else TestTags.CHAT_CALL_START,
                        ),
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call")
                    }
                    if (chat.isGroup) {
                        IconButton(
                            onClick = { manager.dispatch(AppAction.PushScreen(Screen.GroupInfo(chat.chatId))) },
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Group info")
                        }
                    } else {
                        val peer = chat.members.firstOrNull { it.pubkey != myPubkey }
                            ?: chat.members.firstOrNull()
                        if (peer != null) {
                            IconButton(
                                onClick = { manager.dispatch(AppAction.OpenPeerProfile(peer.pubkey)) },
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Contact info")
                            }
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(top = 8.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag(TestTags.CHAT_MESSAGE_LIST),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = listItems,
                        key = { item ->
                            when (item) {
                                is ChatListItem.Message -> item.message.id
                                is ChatListItem.NewMessagesDivider -> "new-messages-divider"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ChatListItem.Message -> {
                                val msg = item.message
                                MessageBubble(
                                    message = msg,
                                    chatId = chatId,
                                    messagesById = messagesById,
                                    onSendMessage = { text ->
                                        manager.dispatch(AppAction.SendMessage(chat.chatId, text, null, null))
                                    },
                                    onReplyTo = { replyMessage -> replyDraft = replyMessage },
                                    onJumpToMessage = { targetId ->
                                        val index = reversedIndexById[targetId] ?: return@MessageBubble
                                        coroutineScope.launch { listState.animateScrollToItem(index) }
                                    },
                                    onReact = { emoji ->
                                        manager.dispatch(AppAction.ReactToMessage(chatId, msg.id, emoji))
                                    },
                                    onDownloadMedia = { attachment ->
                                        manager.dispatch(
                                            AppAction.DownloadChatMedia(
                                                chatId = chatId,
                                                messageId = msg.id,
                                                originalHashHex = attachment.originalHashHex,
                                            ),
                                        )
                                    },
                                )
                            }
                            is ChatListItem.NewMessagesDivider -> NewMessagesDividerRow()
                        }
                    }
                }

                if (!isAtBottom) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (newMessageCount > 0) {
                            Badge(containerColor = PikaBlue) {
                                Text(
                                    text = "$newMessageCount new",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                shouldStickToBottom = true
                                newMessageCount = 0
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                replyDraft?.let { replying ->
                    ReplyComposerPreview(message = replying, onClear = { replyDraft = null })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).testTag(TestTags.CHAT_MESSAGE_INPUT),
                        placeholder = { Text("Message") },
                        singleLine = false,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val text = draft
                            draft = ""
                            manager.dispatch(AppAction.SendMessage(chat.chatId, text, null, replyDraft?.id))
                            replyDraft = null
                        },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.testTag(TestTags.CHAT_SEND),
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────────

private fun LazyListState.isNearBottomForReverseLayout(tolerancePx: Int = 100): Boolean {
    if (firstVisibleItemIndex != 0) return false
    return firstVisibleItemScrollOffset <= tolerancePx
}

private fun chatTitle(chat: com.pika.app.rust.ChatViewState, selfPubkey: String?): String {
    if (chat.isGroup) {
        return chat.groupName?.trim().takeIf { !it.isNullOrBlank() } ?: "Group chat"
    }
    val peer =
        chat.members.firstOrNull { selfPubkey == null || it.pubkey != selfPubkey }
            ?: chat.members.firstOrNull()
    return peer?.name?.trim().takeIf { !it.isNullOrBlank() } ?: peer?.npub ?: "Chat"
}

// ─── message segments ─────────────────────────────────────────────────────────

private sealed class MessageSegment {
    data class Markdown(val text: String) : MessageSegment()
    data class PikaPrompt(val title: String, val options: List<String>) : MessageSegment()
    data class PikaHtml(val html: String) : MessageSegment()
}

private fun parseMessageSegments(content: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val pattern = Regex("```pika-([\\w-]+)(?:[ \\t]+(\\S+))?\\n([\\s\\S]*?)```")
    var lastEnd = 0

    for (match in pattern.findAll(content)) {
        val before = content.substring(lastEnd, match.range.first)
        if (before.isNotBlank()) segments.add(MessageSegment.Markdown(before))

        val blockType = match.groupValues[1]
        val blockBody = match.groupValues[3].trim()

        when (blockType) {
            "prompt" -> {
                try {
                    val json = JSONObject(blockBody)
                    val title = json.getString("title")
                    val optionsArray = json.getJSONArray("options")
                    val options = (0 until optionsArray.length()).map { optionsArray.getString(it) }
                    segments.add(MessageSegment.PikaPrompt(title, options))
                } catch (_: Exception) {
                    segments.add(MessageSegment.Markdown("```$blockType\n$blockBody\n```"))
                }
            }
            "html" -> segments.add(MessageSegment.PikaHtml(blockBody))
            "html-update", "prompt-response" -> { /* consumed by Rust core */ }
            else -> segments.add(MessageSegment.Markdown("```$blockType\n$blockBody\n```"))
        }

        lastEnd = match.range.last + 1
    }

    val tail = content.substring(lastEnd)
    if (tail.isNotBlank()) segments.add(MessageSegment.Markdown(tail))
    return segments
}

// ─── divider ──────────────────────────────────────────────────────────────────

@Composable
private fun NewMessagesDividerRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = PikaBlue.copy(alpha = 0.35f))
        Text(
            text = "NEW MESSAGES",
            style = MaterialTheme.typography.labelSmall,
            color = PikaBlue.copy(alpha = 0.8f),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = PikaBlue.copy(alpha = 0.35f))
    }
}

// ─── MessageBubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    chatId: String,
    messagesById: Map<String, ChatMessage>,
    onSendMessage: (String) -> Unit,
    onReplyTo: (ChatMessage) -> Unit,
    onJumpToMessage: (String) -> Unit,
    onReact: (String) -> Unit,
    onDownloadMedia: (ChatMediaAttachment) -> Unit,
) {
    val isMine = message.isMine
    val bubbleColor = if (isMine) PikaBlue else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val align = if (isMine) Alignment.End else Alignment.Start
    val segments = remember(message.displayContent) { parseMessageSegments(message.displayContent) }
    val ctx = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val replyTarget = remember(message.replyToMessageId, messagesById) {
        message.replyToMessageId?.let { messagesById[it] }
    }
    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(message.timestamp * 1000L))
    }
    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = 80f
    var replyTriggered by remember { mutableStateOf(false) }

    // Overlay state
    var fullscreenAttachment by remember { mutableStateOf<ChatMediaAttachment?>(null) }
    var showReactionPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        message.replyToMessageId?.let { replyToMessageId ->
            ReplyReferencePreview(
                replyToMessageId = replyToMessageId,
                target = replyTarget,
                isMine = isMine,
                onJumpToMessage = onJumpToMessage,
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Text segments ──────────────────────────────────────────────────
        for (segment in segments) {
            when (segment) {
                is MessageSegment.Markdown -> {
                    var showTimestamp by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    val newOffset = (swipeOffset.value + delta).coerceIn(0f, swipeThreshold * 1.2f)
                                    coroutineScope.launch { swipeOffset.snapTo(newOffset) }
                                    if (swipeOffset.value >= swipeThreshold && !replyTriggered) {
                                        replyTriggered = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDragStopped = {
                                    if (replyTriggered) {
                                        onReplyTo(message)
                                        replyTriggered = false
                                    }
                                    coroutineScope.launch { swipeOffset.animateTo(0f) }
                                },
                            ),
                    ) {
                        if (swipeOffset.value > 8f) {
                            Icon(
                                Icons.Default.Reply,
                                contentDescription = "Reply",
                                tint = PikaBlue.copy(alpha = (swipeOffset.value / swipeThreshold).coerceIn(0f, 1f)),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                                    .size(20.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) },
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        ) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(bubbleColor)
                                        .combinedClickable(
                                            onClick = { showTimestamp = !showTimestamp },
                                            onLongClick = { showMenu = true },
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .widthIn(max = 280.dp),
                                ) {
                                    MarkdownText(
                                        markdown = segment.text.trim(),
                                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                                        enableSoftBreakAddsNewLine = true,
                                        afterSetMarkdown = { textView ->
                                            textView.includeFontPadding = false
                                        },
                                    )
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Reply") },
                                        onClick = { onReplyTo(message); showMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("React") },
                                        onClick = { showReactionPicker = true; showMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy text") },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(message.displayContent))
                                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                            if (isMine) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when (message.delivery) {
                                        is MessageDeliveryState.Pending -> "…"
                                        is MessageDeliveryState.Sent -> "✓"
                                        is MessageDeliveryState.Failed -> "!"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (showTimestamp) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = if (isMine) TextAlign.End else TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                    }
                }

                is MessageSegment.PikaPrompt -> {
                    PikaPromptCard(
                        title = segment.title,
                        options = segment.options,
                        message = message,
                        onSelect = onSendMessage,
                    )
                }

                is MessageSegment.PikaHtml -> {
                    PikaHtmlCard(html = segment.html, htmlState = message.htmlState)
                }
            }
        }

        // ── Media attachments ──────────────────────────────────────────────
        for (attachment in message.media) {
            Spacer(Modifier.height(4.dp))
            when {
                attachment.mimeType.startsWith("image/") -> {
                    ImageAttachmentBubble(
                        attachment = attachment,
                        isMine = isMine,
                        onTap = { fullscreenAttachment = attachment },
                        onDownload = { onDownloadMedia(attachment) },
                    )
                }
                attachment.mimeType.startsWith("audio/") -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(bubbleColor)
                            .widthIn(max = 280.dp),
                    ) {
                        VoiceMessageRow(
                            attachment = attachment,
                            isMine = isMine,
                            onDownload = { onDownloadMedia(attachment) },
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(bubbleColor)
                            .widthIn(max = 280.dp),
                    ) {
                        FileAttachmentRow(
                            attachment = attachment,
                            isMine = isMine,
                            onDownload = { onDownloadMedia(attachment) },
                        )
                    }
                }
            }
        }

        // ── Reaction strip ─────────────────────────────────────────────────
        if (message.reactions.isNotEmpty()) {
            ReactionStrip(
                reactions = message.reactions,
                onToggle = onReact,
            )
        }

        Spacer(Modifier.height(2.dp))
    }

    // ── Fullscreen image overlay ───────────────────────────────────────────
    fullscreenAttachment?.let { att ->
        if (att.localPath != null) {
            Dialog(
                onDismissRequest = { fullscreenAttachment = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                FullscreenImageViewer(
                    localPath = att.localPath,
                    filename = att.filename,
                    onDismiss = { fullscreenAttachment = null },
                )
            }
        }
    }

    // ── Quick reaction picker ─────────────────────────────────────────────
    if (showReactionPicker) {
        QuickReactionPicker(
            onSelect = { emoji -> onReact(emoji); showReactionPicker = false },
            onDismiss = { showReactionPicker = false },
        )
    }
}

// ─── image attachment bubble ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageAttachmentBubble(
    attachment: ChatMediaAttachment,
    isMine: Boolean,
    onTap: () -> Unit,
    onDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    if (attachment.localPath != null) {
        Box(
            modifier = Modifier
                .clip(shape)
                .combinedClickable(onClick = onTap)
                .widthIn(max = 240.dp),
        ) {
            AsyncImage(
                model = File(attachment.localPath),
                contentDescription = attachment.filename,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .wrapContentHeight(),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (isMine) PikaBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onDownload() }
                .padding(vertical = 20.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Tap to download image",
                style = MaterialTheme.typography.bodySmall,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── pika-html WebView ────────────────────────────────────────────────────────

@Composable
private fun PikaHtmlCard(html: String, htmlState: String?) {
    var webViewHeightDp by remember { mutableStateOf(160) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .widthIn(max = 280.dp)
            .height(webViewHeightDp.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // JS bridge: page reports its natural scroll height back so we can size the Box
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onHeightChanged(px: Float) {
                                val dp = with(density) { px.toDp().value.toInt() }.coerceAtLeast(40)
                                webViewHeightDp = dp + 16 // small vertical padding
                            }
                        },
                        "PikaAndroid",
                    )

                    val wrappedHtml = """
                        <html><head>
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>
                          body { margin:0; padding:8px; font-family:system-ui,-apple-system,sans-serif;
                                 word-break:break-word; background:transparent; }
                        </style>
                        </head><body>$html</body>
                        <script>
                          function reportHeight() {
                            PikaAndroid.onHeightChanged(document.body.scrollHeight);
                          }
                          window.addEventListener('load', reportHeight);
                          new ResizeObserver(reportHeight).observe(document.body);
                        </script></html>
                    """.trimIndent()

                    loadDataWithBaseURL(null, wrappedHtml, "text/html", "utf-8", null)
                }
            },
            update = { webView ->
                if (htmlState != null) {
                    val escaped = htmlState
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                    webView.evaluateJavascript("window.__pikaState='$escaped';", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─── reaction strip ───────────────────────────────────────────────────────────

@Composable
private fun ReactionStrip(
    reactions: List<ReactionSummary>,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (reaction in reactions) {
            Surface(
                onClick = { onToggle(reaction.emoji) },
                shape = RoundedCornerShape(12.dp),
                color = if (reaction.reactedByMe) PikaBlue.copy(alpha = 0.20f)
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = reaction.emoji, style = MaterialTheme.typography.bodyMedium)
                    if (reaction.count > 1u) {
                        Text(
                            text = reaction.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reaction.reactedByMe) PikaBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─── quick reaction picker ────────────────────────────────────────────────────

@Composable
private fun QuickReactionPicker(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (emoji in QUICK_REACTIONS) {
                    TextButton(
                        onClick = { onSelect(emoji) },
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

// ─── reply composer preview ───────────────────────────────────────────────────

@Composable
private fun ReplyComposerPreview(message: ChatMessage, onClear: () -> Unit) {
    val sender = when {
        message.isMine -> "You"
        !message.senderName.isNullOrBlank() -> message.senderName!!
        else -> message.senderPubkey.take(8)
    }
    val snippet = message.displayContent.trim().lineSequence().firstOrNull()?.let {
        if (it.length > 80) it.take(80) + "…" else it
    } ?: "(empty message)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.width(2.dp).height(28.dp).background(PikaBlue))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Replying to $sender",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onClear) { Text("Cancel") }
    }
}

// ─── reply reference preview ──────────────────────────────────────────────────

@Composable
private fun ReplyReferencePreview(
    replyToMessageId: String,
    target: ChatMessage?,
    isMine: Boolean,
    onJumpToMessage: (String) -> Unit,
) {
    val sender = remember(target) {
        when {
            target == null -> "Original message"
            target.isMine -> "You"
            !target.senderName.isNullOrBlank() -> target.senderName!!
            else -> target.senderPubkey.take(8)
        }
    }
    val snippet = remember(target) {
        val text = target?.displayContent?.trim().orEmpty()
        when {
            target == null -> "Original message not loaded"
            text.isEmpty() -> "(empty message)"
            else -> text.lineSequence().first().let { first ->
                if (first.length > 80) first.take(80) + "…" else first
            }
        }
    }

    val modifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(if (isMine) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f))
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .widthIn(max = 280.dp)

    Row(
        modifier = if (target != null) modifier.clickable { onJumpToMessage(replyToMessageId) } else modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(2.dp).height(28.dp).background(if (isMine) Color.White.copy(alpha = 0.8f) else PikaBlue))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = sender,
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = if (isMine) Color.White.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── pika prompt card ─────────────────────────────────────────────────────────

@Composable
private fun PikaPromptCard(
    title: String,
    options: List<String>,
    message: ChatMessage,
    onSelect: (String) -> Unit,
) {
    val hasVoted = message.myPollVote != null
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
            .widthIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        for (option in options) {
            val tally = message.pollTally.firstOrNull { it.option == option }
            val isMyVote = message.myPollVote == option
            TextButton(
                onClick = {
                    val response = "```pika-prompt-response\n{\"prompt_id\":\"${message.id}\",\"selected\":\"$option\"}\n```"
                    onSelect(response)
                },
                enabled = !hasVoted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (isMyVote) PikaBlue.copy(alpha = 0.25f) else PikaBlue.copy(alpha = 0.1f),
                    contentColor = PikaBlue,
                    disabledContainerColor = if (isMyVote) PikaBlue.copy(alpha = 0.25f) else PikaBlue.copy(alpha = 0.1f),
                    disabledContentColor = PikaBlue.copy(alpha = 0.7f),
                ),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(option)
                    if (tally != null) Text("${tally.count}", style = MaterialTheme.typography.titleSmall)
                }
            }
            if (tally != null && tally.voterNames.isNotEmpty()) {
                Text(
                    text = tally.voterNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
