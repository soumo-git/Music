package com.android.music.duo.chat.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders markdown text with support for common formatting
 * Supports: **bold**, *italic*, `code`, ~~strikethrough~~, [links](url), # headers
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.White,
    onLinkClick: ((String) -> Unit)? = null
) {
    val annotatedString = remember(text, color) {
        parseMarkdown(text, color)
    }

    if (onLinkClick != null) {
        ClickableText(
            text = annotatedString,
            modifier = modifier,
            style = style.copy(color = color),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onLinkClick(annotation.item)
                    }
            }
        )
    } else {
        androidx.compose.material3.Text(
            text = annotatedString,
            modifier = modifier,
            style = style.copy(color = color)
        )
    }
}

private fun parseMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val length = text.length

        while (currentIndex < length) {
            when {
                // Code block (```)
                text.startsWith("```", currentIndex) -> {
                    val endIndex = text.indexOf("```", currentIndex + 3)
                    if (endIndex != -1) {
                        val code = text.substring(currentIndex + 3, endIndex).trim()
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFF04FF00)
                        )) {
                            append(code)
                        }
                        currentIndex = endIndex + 3
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Inline code (`)
                text.startsWith("`", currentIndex) && !text.startsWith("```", currentIndex) -> {
                    val endIndex = text.indexOf("`", currentIndex + 1)
                    if (endIndex != -1) {
                        val code = text.substring(currentIndex + 1, endIndex)
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFF04FF00)
                        )) {
                            append(code)
                        }
                        currentIndex = endIndex + 1
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Bold (**)
                text.startsWith("**", currentIndex) -> {
                    val endIndex = text.indexOf("**", currentIndex + 2)
                    if (endIndex != -1) {
                        val boldText = text.substring(currentIndex + 2, endIndex)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(boldText)
                        }
                        currentIndex = endIndex + 2
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Strikethrough (~~)
                text.startsWith("~~", currentIndex) -> {
                    val endIndex = text.indexOf("~~", currentIndex + 2)
                    if (endIndex != -1) {
                        val strikeText = text.substring(currentIndex + 2, endIndex)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(strikeText)
                        }
                        currentIndex = endIndex + 2
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Italic (*)
                text.startsWith("*", currentIndex) && !text.startsWith("**", currentIndex) -> {
                    val endIndex = text.indexOf("*", currentIndex + 1)
                    if (endIndex != -1 && !text.startsWith("**", endIndex)) {
                        val italicText = text.substring(currentIndex + 1, endIndex)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(italicText)
                        }
                        currentIndex = endIndex + 1
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Link [text](url)
                text.startsWith("[", currentIndex) -> {
                    val closeBracket = text.indexOf("]", currentIndex)
                    val openParen = text.indexOf("(", closeBracket)
                    val closeParen = text.indexOf(")", openParen)
                    
                    if (closeBracket != -1 && openParen == closeBracket + 1 && closeParen != -1) {
                        val linkText = text.substring(currentIndex + 1, closeBracket)
                        val url = text.substring(openParen + 1, closeParen)
                        
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(
                            color = Color(0xFF64B5F6),
                            textDecoration = TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                        pop()
                        currentIndex = closeParen + 1
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                // Header (#)
                (currentIndex == 0 || text[currentIndex - 1] == '\n') && text.startsWith("#", currentIndex) -> {
                    var headerLevel = 0
                    var i = currentIndex
                    while (i < length && text[i] == '#' && headerLevel < 6) {
                        headerLevel++
                        i++
                    }
                    
                    if (i < length && text[i] == ' ') {
                        val endOfLine = text.indexOf('\n', i).let { if (it == -1) length else it }
                        val headerText = text.substring(i + 1, endOfLine)
                        
                        val fontSize = when (headerLevel) {
                            1 -> 24.sp
                            2 -> 22.sp
                            3 -> 20.sp
                            4 -> 18.sp
                            5 -> 16.sp
                            else -> 14.sp
                        }
                        
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize
                        )) {
                            append(headerText)
                        }
                        currentIndex = endOfLine
                    } else {
                        append(text[currentIndex])
                        currentIndex++
                    }
                }
                else -> {
                    append(text[currentIndex])
                    currentIndex++
                }
            }
        }
    }
}
