package com.android.music.duo.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.android.music.R
import com.android.music.duo.chat.model.ChatMessage
import com.android.music.duo.chat.model.ChatState
import com.android.music.duo.chat.ui.components.ChatBubble
import com.android.music.duo.chat.ui.components.ParticleBackground
import com.android.music.duo.chat.ui.components.TypingIndicator

@Composable
fun DuoChatScreen(
    chatState: ChatState,
    onSendMessage: (String) -> Unit,
    onTyping: () -> Unit,
    onDismiss: () -> Unit,
    onVoiceRecord: () -> Unit = {},
    onMarkMessagesRead: () -> Unit = {},
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }
    
    // Mark messages as read when chat is opened
    LaunchedEffect(Unit) {
        onMarkMessagesRead()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 200) {
                            onDismiss()
                        }
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            dragOffset += dragAmount
                        }
                    }
                )
            }
    ) {
        // Particle background
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = Color(0xFF04FF00).copy(alpha = 0.6f)
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            ChatHeader(
                connectionType = chatState.connectionType,
                signalStrength = chatState.signalStrength,
                onDismiss = onDismiss
            )

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = chatState.messages,
                    key = { "${it.id}_${it.status}" }
                ) { message ->
                    ChatBubble(message = message)
                }

                // Typing indicator
                if (chatState.isPartnerTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Input field
            ChatInputField(
                value = messageText,
                onValueChange = { 
                    messageText = it
                    if (it.isNotEmpty()) {
                        onTyping()
                    }
                },
                onSend = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                    }
                },
                onVoiceRecord = onVoiceRecord,
                isRecording = isRecording
            )
        }
    }
}

@Composable
private fun ChatHeader(
    connectionType: String,
    signalStrength: Int,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4A4A4A))
            )
        }

        // Header content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Duo icon
            val context = LocalContext.current
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/Duo.png")
                        .build()
                ),
                contentDescription = "Duo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title and connection info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Duo Chat",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = connectionType.ifEmpty { "Connected" },
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFF8696A0)
                    )
                )
            }

            // Signal strength icon
            SignalStrengthIcon(strength = signalStrength)
        }
    }
}

@Composable
private fun SignalStrengthIcon(strength: Int) {
    val iconRes = when (strength) {
        0 -> R.drawable.ic_signal_wifi_0
        1 -> R.drawable.ic_signal_wifi_1
        2 -> R.drawable.ic_signal_wifi_2
        3 -> R.drawable.ic_signal_wifi_3
        else -> R.drawable.ic_signal_wifi_4
    }

    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = "Signal strength",
        modifier = Modifier.size(24.dp),
        tint = Color.White
    )
}

@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceRecord: () -> Unit = {},
    isRecording: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A1A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                // Recording UI
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF2A2A2A))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Recording indicator
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording...",
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            } else {
                // Text input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF2A2A2A))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(Color(0xFF04FF00)),
                        decorationBox = { innerTextField ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        text = "Type a message",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            color = Color(0xFF8696A0)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Voice/Send button
            if (value.isBlank() && !isRecording) {
                // Voice record button
                IconButton(
                    onClick = onVoiceRecord,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Record voice message",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Send button
                IconButton(
                    onClick = if (isRecording) onVoiceRecord else onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFE53935) else Color(0xFF00A884)
                        )
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isRecording) R.drawable.ic_stop else R.drawable.ic_send
                        ),
                        contentDescription = if (isRecording) "Stop recording" else "Send",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
