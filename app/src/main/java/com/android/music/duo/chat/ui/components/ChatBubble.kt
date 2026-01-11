package com.android.music.duo.chat.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.music.R
import com.android.music.duo.chat.model.ChatMessage
import com.android.music.duo.chat.model.MessageStatus
import com.android.music.duo.chat.model.MessageType
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    // Monochrome colors for dark mode
    val bubbleColor = if (message.isFromMe) {
        Color(0xFF2A2A2A) // Dark gray for sent messages
    } else {
        Color(0xFF1A1A1A) // Slightly darker for received messages
    }

    val bubbleShape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.VOICE -> {
                        VoiceMessageContent(
                            duration = message.voiceDuration,
                            voiceData = message.voiceData,
                            messageId = message.id
                        )
                    }
                    else -> {
                        // Text message with markdown support
                        MarkdownText(
                            text = message.text,
                            style = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Timestamp and status
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Color(0xFF8696A0)
                        )
                    )

                    if (message.isFromMe) {
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(status: MessageStatus) {
    val (iconRes, tint) = when (status) {
        MessageStatus.SENDING -> R.drawable.ic_clock to Color(0xFF8696A0)
        MessageStatus.SENT -> R.drawable.ic_check to Color(0xFF8696A0)
        MessageStatus.DELIVERED -> R.drawable.ic_check_double to Color(0xFF8696A0)
        MessageStatus.READ -> R.drawable.ic_check_double to Color(0xFF53BDEB)
        MessageStatus.FAILED -> R.drawable.ic_error to Color(0xFFFF0000)
    }

    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = status.name,
        modifier = Modifier.size(16.dp),
        tint = tint
    )
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            AnimatedDot(delayMillis = index * 200)
        }
    }
}

@Composable
private fun AnimatedDot(delayMillis: Int) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 600,
                delayMillis = delayMillis,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF8696A0).copy(alpha = alpha))
    )
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun VoiceMessageContent(
    duration: Long,
    voiceData: ByteArray?,
    messageId: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // MediaPlayer management
    val mediaPlayer = remember {
        android.media.MediaPlayer()
    }
    
    androidx.compose.runtime.DisposableEffect(messageId) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e("VoiceMessage", "Error releasing media player", e)
            }
        }
    }
    
    val playVoiceMessage = {
        try {
            if (voiceData != null && voiceData.isNotEmpty()) {
                if (isPlaying) {
                    // Pause
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                        mediaPlayer.reset()
                    }
                    
                    // Write to temp file and play
                    val tempFile = java.io.File(context.cacheDir, "voice_play_$messageId.m4a")
                    tempFile.writeBytes(voiceData)
                    
                    mediaPlayer.apply {
                        reset()
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            isPlaying = false
                            currentPosition = 0L
                            tempFile.delete()
                        }
                        start()
                    }
                    isPlaying = true
                    
                    // Update progress
                    @OptIn(DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        while (isPlaying && mediaPlayer.isPlaying) {
                            currentPosition = mediaPlayer.currentPosition.toLong()
                            kotlinx.coroutines.delay(100)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceMessage", "Error playing voice message", e)
            isPlaying = false
        }
    }
    
    Row(
        modifier = modifier.clickable { playVoiceMessage() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3A3A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Waveform and duration
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(20) { index ->
                    val height = (8 + (index % 5) * 4).dp
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    val barProgress = index.toFloat() / 20f
                    val isActive = barProgress <= progress
                    
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (isActive) Color(0xFF04FF00) else Color(0xFF8696A0))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Duration
            Text(
                text = if (isPlaying) {
                    "${formatDuration(currentPosition)} / ${formatDuration(duration)}"
                } else {
                    formatDuration(duration)
                },
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color(0xFF8696A0)
                )
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
