package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small, dependency-free Markdown renderer for SI Agent replies.
 * Supports paragraphs, headings, emphasis, inline code, bullets, numbered lists,
 * fenced code and Markdown tables. It intentionally renders table rows as readable
 * labelled blocks so narrow phone screens never show raw pipe delimiters.
 */
@Composable
internal fun SiAgentMarkdown(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    Column(modifier = modifier.fillMaxWidth()) {
        var index = 0
        var inCode = false
        val codeLines = mutableListOf<String>()
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    Text(
                        codeLines.joinToString("\n"),
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(7.dp)).padding(9.dp),
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    codeLines.clear()
                    inCode = false
                } else inCode = true
                index++
                continue
            }
            if (inCode) {
                codeLines += line
                index++
                continue
            }

            if (line.trim().startsWith("|") && index + 1 < lines.size && isTableDivider(lines[index + 1])) {
                val headers = splitTableRow(line)
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    rows += splitTableRow(lines[index])
                    index++
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    rows.forEach { cells ->
                        val pairs = headers.zip(cells).filter { (header, value) -> header.isNotBlank() && value.isNotBlank() }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(Color.Black.copy(alpha = 0.14f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            pairs.forEach { (header, value) ->
                                Text(
                                    text = inlineMarkdown(header + ": ", color, true) + inlineMarkdown(value, color),
                                    color = color,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                continue
            }

            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> Unit
                trimmed.startsWith("#") -> {
                    val heading = trimmed.trimStart('#', ' ')
                    Text(inlineMarkdown(heading, color, true), color = color, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp, bottom = 3.dp))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    Text("• " + inlineMarkdown(trimmed.drop(2), color), color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
                }
                Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) -> {
                    Text(inlineMarkdown(trimmed, color), color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                trimmed.matches(Regex("([-*_])\\1{2,}")) -> Unit
                else -> Text(inlineMarkdown(trimmed, color), color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            index++
        }
        if (inCode && codeLines.isNotEmpty()) {
            Text(codeLines.joinToString("\n"), color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun isTableDivider(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { it.isBlank() || it.matches(Regex(":?-{3,}:?")) }
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun inlineMarkdown(value: String, color: Color, boldAll: Boolean = false) =
    buildAnnotatedString {
        var i = 0
        while (i < value.length) {
            val marker = when {
                value.startsWith("**", i) -> "**"
                value.startsWith("__", i) -> "__"
                value.startsWith("`", i) -> "`"
                value.startsWith("*", i) -> "*"
                value.startsWith("_", i) -> "_"
                else -> null
            }
            if (marker == null) {
                append(value[i])
                i++
                continue
            }
            val end = value.indexOf(marker, i + marker.length)
            if (end <= i + marker.length) {
                append(marker)
                i += marker.length
                continue
            }
            val content = value.substring(i + marker.length, end)
            pushStyle(
                SpanStyle(
                    fontWeight = if (boldAll || marker == "**" || marker == "__") FontWeight.Bold else FontWeight.Normal,
                    fontFamily = if (marker == "`") FontFamily.Monospace else FontFamily.Default,
                    color = color
                )
            )
            append(content)
            pop()
            i = end + marker.length
        }
    }
