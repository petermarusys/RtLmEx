package com.peyo.rtlmex

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

sealed interface MarkdownBlock {
    data class Header(val level: Int, val content: AnnotatedString) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class BulletList(val items: List<AnnotatedString>) : MarkdownBlock
    data class Paragraph(val content: AnnotatedString) : MarkdownBlock
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    val processedText = text
        .replace("$\\rightarrow$", "→")
        .replace("\\rightarrow", "→")
        .replace("$\\to$", "→")
        .replace("\\to", "→")
        .replace("$\\leftarrow$", "←")
        .replace("\\leftarrow", "←")
        .replace("$\\leftrightarrow$", "↔")
        .replace("\\leftrightarrow", "↔")
        .replace("$\\Rightarrow$", "⇒")
        .replace("\\Rightarrow", "⇒")
        .replace("$\\Leftarrow$", "⇐")
        .replace("\\Leftarrow", "⇐")
        .replace("$\\Leftrightarrow$", "⇔")
        .replace("\\Leftrightarrow", "⇔")

    return buildAnnotatedString {
        var i = 0
        while (i < processedText.length) {
            // Check inline code
            if (processedText.startsWith("`", i)) {
                val end = processedText.indexOf("`", i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f)))
                    append(processedText.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            // Check bold
            if (processedText.startsWith("**", i)) {
                val end = processedText.indexOf("**", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(processedText.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            // Check italic
            if (processedText.startsWith("*", i)) {
                val end = processedText.indexOf("*", i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(processedText.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            if (processedText.startsWith("_", i)) {
                val end = processedText.indexOf("_", i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(processedText.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            
            append(processedText[i])
            i++
        }
    }
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeBlock = false
    val currentCode = StringBuilder()
    var currentCodeLanguage: String? = null
    
    val currentBulletList = mutableListOf<AnnotatedString>()
    
    fun flushBulletList() {
        if (currentBulletList.isNotEmpty()) {
            blocks.add(MarkdownBlock.BulletList(currentBulletList.toList()))
            currentBulletList.clear()
        }
    }

    for (line in lines) {
        if (inCodeBlock) {
            if (line.trim().startsWith("```")) {
                blocks.add(MarkdownBlock.CodeBlock(currentCodeLanguage, currentCode.toString().removeSuffix("\n")))
                inCodeBlock = false
                currentCode.setLength(0)
                currentCodeLanguage = null
            } else {
                currentCode.append(line).append("\n")
            }
            continue
        }

        if (line.trim().startsWith("```")) {
            flushBulletList()
            inCodeBlock = true
            currentCodeLanguage = line.trim().substring(3).trim().ifEmpty { null }
            continue
        }

        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("#")) {
            flushBulletList()
            val headerMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmedLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2]
                blocks.add(MarkdownBlock.Header(level, parseInlineMarkdown(content)))
            } else {
                blocks.add(MarkdownBlock.Paragraph(parseInlineMarkdown(line)))
            }
        } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
            val content = trimmedLine.substring(2)
            currentBulletList.add(parseInlineMarkdown(content))
        } else {
            flushBulletList()
            if (trimmedLine.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(parseInlineMarkdown(line)))
            }
        }
    }
    flushBulletList()
    
    if (inCodeBlock) {
        blocks.add(MarkdownBlock.CodeBlock(currentCodeLanguage, currentCode.toString()))
    }
    
    return blocks
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    }
                    Text(text = block.content, style = style)
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(text = "• ", style = MaterialTheme.typography.bodyMedium)
                                Text(text = item, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(text = block.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
