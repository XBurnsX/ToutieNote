package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.data.models.NoteAttachment
import com.toutieserver.toutienote.ui.theme.BgColor
import com.toutieserver.toutienote.ui.theme.BorderColor
import com.toutieserver.toutienote.ui.theme.DangerColor
import com.toutieserver.toutienote.ui.theme.GreenColor
import com.toutieserver.toutienote.ui.theme.LocalAccentColor
import com.toutieserver.toutienote.ui.theme.MutedColor
import com.toutieserver.toutienote.ui.theme.Surface2Color
import com.toutieserver.toutienote.ui.theme.SurfaceColor
import com.toutieserver.toutienote.ui.theme.TextColor
import com.toutieserver.toutienote.util.formatNoteDate
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder

private data class ActiveWikiLink(
    val query: String,
    val openIndex: Int,
)

private data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

private enum class InlineTokenType {
    IMAGE,
    WIKILINK,
    LINK,
    CODE,
    BOLD,
    STRIKE,
    ITALIC,
}

private data class InlineToken(
    val type: InlineTokenType,
    val match: MatchResult,
    val priority: Int,
)

private data class CodeStyleMatch(
    val start: Int,
    val endExclusive: Int,
    val style: SpanStyle,
)

private val SupportedCodeLanguages = listOf(
    "plaintext",
    "kotlin",
    "java",
    "python",
    "javascript",
    "typescript",
    "tsx",
    "jsx",
    "c",
    "cpp",
    "csharp",
    "go",
    "rust",
    "swift",
    "php",
    "ruby",
    "scala",
    "dart",
    "lua",
    "r",
    "sql",
    "html",
    "css",
    "scss",
    "json",
    "yaml",
    "xml",
    "markdown",
    "toml",
    "ini",
    "bash",
    "shell",
    "powershell",
    "dockerfile",
    "gradle",
    "groovy",
    "perl",
    "haskell",
    "elixir",
    "erlang",
    "ocaml",
    "zig",
)

private val CodeBlockBackground = Color(0xFF2D2A26)
private val CodeHeaderBackground = Color(0xFF38342F)
private val CodeBorderColor = Color(0xFF8B857B)
private val CodeDefaultText = Color(0xFFF5F5F5)
private val CodeKeywordColor = Color(0xFFE5C07B)
private val CodeStringColor = Color(0xFF98C379)
private val CodeNumberColor = Color(0xFFD19A66)
private val CodeCommentColor = Color(0xFF7F848E)
private val CodeTypeColor = Color(0xFF61AFEF)

private enum class TokenKind {
    IMAGE,
    WIKILINK,
    EXTERNAL_LINK,
    CODE,
    BOLD,
    ITALIC,
    STRIKE,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    BLOCKQUOTE,
    BULLET_LIST,
    ORDERED_LIST,
}

private data class RangeMap(
    val sourceToRendered: IntArray,
    val renderedToSource: IntArray,
) {
    fun originalToRendered(offset: Int): Int =
        sourceToRendered[offset.coerceIn(0, sourceToRendered.lastIndex)]

    fun transformedToOriginal(offset: Int): Int =
        renderedToSource[offset.coerceIn(0, renderedToSource.lastIndex)]
}

private data class HiddenSourceRange(
    val range: TextRange,
    val snapOffset: Int,
)

private data class RenderedToken(
    val kind: TokenKind,
    val sourceRange: TextRange,
    val renderedRange: TextRange,
    val payload: String? = null,
)

private data class RenderedDocument(
    val annotatedString: AnnotatedString,
    val rangeMap: RangeMap,
    val tokens: List<RenderedToken>,
    val hiddenRanges: List<HiddenSourceRange>,
) {
    fun sourceRangeForRendered(range: TextRange): TextRange =
        TextRange(
            start = rangeMap.transformedToOriginal(range.min),
            end = rangeMap.transformedToOriginal(range.max),
        )

    fun tokenAtRenderedOffset(offset: Int, kind: TokenKind? = null): RenderedToken? =
        tokens.firstOrNull { token ->
            offset in token.renderedRange.start until token.renderedRange.end &&
                (kind == null || token.kind == kind)
        }

    fun normalizeSourceOffset(offset: Int): Int {
        val safeOffset = offset.coerceIn(0, rangeMap.sourceToRendered.lastIndex)
        val hidden = hiddenRanges.firstOrNull { safeOffset in it.range.start until it.range.end }
        return hidden?.snapOffset?.coerceIn(0, rangeMap.sourceToRendered.lastIndex) ?: safeOffset
    }

    fun normalizeSelection(selection: TextRange): TextRange =
        TextRange(
            start = normalizeSourceOffset(selection.start),
            end = normalizeSourceOffset(selection.end),
        )
}

private class RenderedDocumentVisualTransformation(
    private val document: RenderedDocument,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                document.rangeMap.originalToRendered(offset)

            override fun transformedToOriginal(offset: Int): Int =
                document.rangeMap.transformedToOriginal(offset)
        }
        return TransformedText(document.annotatedString, offsetMapping)
    }
}

private fun attachmentMimeType(filename: String, providedMime: String?): String {
    val lower = filename.lowercase()
    return when {
        !providedMime.isNullOrBlank() -> providedMime
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".mov") -> "video/quicktime"
        lower.endsWith(".mkv") -> "video/x-matroska"
        else -> "application/octet-stream"
    }
}

private fun decodeHtmlEntities(value: String): String =
    value.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

private fun storedContentToMarkdown(content: String): String {
    if (content.isBlank()) return ""
    if (!content.contains("<")) return content

    var result = content
    result = result.replace(Regex("(?is)<br\\s*/?>"), "\n")
    result = result.replace(Regex("(?is)</p>"), "\n\n")
    result = result.replace(Regex("(?is)<p[^>]*>"), "")
    result = result.replace(Regex("(?is)<strong>(.*?)</strong>")) { "**${decodeHtmlEntities(it.groupValues[1])}**" }
    result = result.replace(Regex("(?is)<b>(.*?)</b>")) { "**${decodeHtmlEntities(it.groupValues[1])}**" }
    result = result.replace(Regex("(?is)<em>(.*?)</em>")) { "*${decodeHtmlEntities(it.groupValues[1])}*" }
    result = result.replace(Regex("(?is)<i>(.*?)</i>")) { "*${decodeHtmlEntities(it.groupValues[1])}*" }
    result = result.replace(Regex("(?is)<s>(.*?)</s>")) { "~~${decodeHtmlEntities(it.groupValues[1])}~~" }
    result = result.replace(Regex("(?is)<strike>(.*?)</strike>")) { "~~${decodeHtmlEntities(it.groupValues[1])}~~" }
    result = result.replace(Regex("(?is)<code>(.*?)</code>")) { "`${decodeHtmlEntities(it.groupValues[1])}`" }
    result = result.replace(Regex("(?is)<img[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"[^>]*/?>")) {
        val url = decodeHtmlEntities(it.groupValues[1])
        val alt = decodeHtmlEntities(it.groupValues[2]).ifBlank { "image" }
        "![${alt}](${url})"
    }
    result = result.replace(Regex("(?is)<a[^>]*href=\"toutienote://note/([^\"]+)\"[^>]*>(.*?)</a>")) {
        val fallback = decodeHtmlEntities(it.groupValues[2]).trim()
        val decodedTarget = runCatching { URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault(fallback)
        "[[${decodedTarget.ifBlank { fallback }}]]"
    }
    result = result.replace(Regex("(?is)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")) {
        val url = decodeHtmlEntities(it.groupValues[1])
        val label = decodeHtmlEntities(it.groupValues[2]).ifBlank { url }
        "[${label}](${url})"
    }
    result = result.replace(Regex("(?is)<[^>]+>"), "")
    return decodeHtmlEntities(result)
        .replace("\r\n", "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun findWikiLinkAtOffset(text: String, offset: Int): String? {
    if (offset !in text.indices) return null
    val regex = Regex("""\[\[([^\[\]\r\n]+)]]""")
    return regex.findAll(text)
        .firstOrNull { match -> offset >= match.range.first && offset <= match.range.last }
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun findCompletedWikiLinkAtCursor(text: String, cursor: Int): String? {
    if (text.isEmpty()) return null
    val safeCursor = cursor.coerceIn(0, text.length)
    val regex = Regex("""\[\[([^\[\]\r\n]+)]]""")
    return regex.findAll(text)
        .firstOrNull { match -> safeCursor >= match.range.first && safeCursor <= match.range.last + 1 }
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun extractWikiLinkTitles(text: String): List<String> {
    val regex = Regex("""\[\[([^\[\]\r\n]+)]]""")
    return regex.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

private fun findActiveWikiLink(text: String, cursor: Int): ActiveWikiLink? {
    if (cursor <= 0 || cursor > text.length) return null
    if (findCompletedWikiLinkAtCursor(text, cursor) != null) return null
    val beforeCursor = text.substring(0, cursor)
    val openIndex = beforeCursor.lastIndexOf("[[")
    if (openIndex == -1) return null
    val query = beforeCursor.substring(openIndex + 2)
    if (query.contains("]") || query.contains("\n") || query.contains("\r")) return null
    return ActiveWikiLink(query = query.trimStart(), openIndex = openIndex)
}

private fun snapshotOf(value: TextFieldValue): EditorSnapshot =
    EditorSnapshot(
        text = value.text,
        selectionStart = value.selection.start,
        selectionEnd = value.selection.end,
    )

private fun EditorSnapshot.toTextFieldValue(): TextFieldValue =
    TextFieldValue(
        text = text,
        selection = TextRange(
            selectionStart.coerceIn(0, text.length),
            selectionEnd.coerceIn(0, text.length),
        )
    )

private fun replaceRange(
    value: TextFieldValue,
    start: Int,
    end: Int,
    replacement: String,
    selection: TextRange = TextRange(start + replacement.length),
): TextFieldValue {
    val safeStart = start.coerceIn(0, value.text.length)
    val safeEnd = end.coerceIn(safeStart, value.text.length)
    val updatedText = buildString {
        append(value.text.substring(0, safeStart))
        append(replacement)
        append(value.text.substring(safeEnd))
    }
    return value.copy(
        text = updatedText,
        selection = TextRange(
            selection.start.coerceIn(0, updatedText.length),
            selection.end.coerceIn(0, updatedText.length),
        )
    )
}

private fun insertAtSelection(value: TextFieldValue, inserted: String): TextFieldValue =
    replaceRange(value, value.selection.min, value.selection.max, inserted)

private fun wrapSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
    val selection = value.selection
    val selected = if (!selection.collapsed) value.text.substring(selection.min, selection.max) else placeholder
    val replacement = prefix + selected + suffix
    val newSelection = if (selection.collapsed) {
        TextRange(selection.min + prefix.length, selection.min + prefix.length + placeholder.length)
    } else {
        TextRange(selection.min + replacement.length)
    }
    return replaceRange(value, selection.min, selection.max, replacement, newSelection)
}

private fun RenderedToken.contentSourceRange(): TextRange =
    when (kind) {
        TokenKind.BOLD -> TextRange(sourceRange.start + 2, sourceRange.end - 2)
        TokenKind.STRIKE -> TextRange(sourceRange.start + 2, sourceRange.end - 2)
        TokenKind.ITALIC -> TextRange(sourceRange.start + 1, sourceRange.end - 1)
        TokenKind.CODE -> TextRange(sourceRange.start + 1, sourceRange.end - 1)
        TokenKind.WIKILINK -> TextRange(sourceRange.start + 2, sourceRange.end - 2)
        TokenKind.EXTERNAL_LINK -> TextRange(
            start = sourceRange.start + 1,
            end = (sourceRange.start + 1 + renderedRange.length).coerceAtMost(sourceRange.end)
        )
        TokenKind.HEADING_1 -> TextRange(sourceRange.start + 2, sourceRange.end)
        TokenKind.HEADING_2 -> TextRange(sourceRange.start + 3, sourceRange.end)
        TokenKind.HEADING_3 -> TextRange(sourceRange.start + 4, sourceRange.end)
        else -> sourceRange
    }

private fun findEnclosingToken(
    value: TextFieldValue,
    document: RenderedDocument,
    kind: TokenKind,
): RenderedToken? {
    val selectionStart = value.selection.min
    val selectionEnd = value.selection.max
    return document.tokens.firstOrNull { token ->
        token.kind == kind &&
            selectionStart >= token.contentSourceRange().start &&
            selectionEnd <= token.contentSourceRange().end
    }
}

private fun toggleInlineStyle(
    value: TextFieldValue,
    document: RenderedDocument,
    kind: TokenKind,
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
    val token = findEnclosingToken(value, document, kind) ?: return wrapSelection(value, prefix, suffix, placeholder)
    if (value.selection.collapsed) {
        return value.copy(selection = TextRange(token.sourceRange.end))
    }
    val contentRange = token.contentSourceRange()
    val content = value.text.substring(contentRange.start, contentRange.end)
    val selectionStart = (value.selection.start - contentRange.start).coerceIn(0, content.length)
    val selectionEnd = (value.selection.end - contentRange.start).coerceIn(0, content.length)
    return replaceRange(
        value = value,
        start = token.sourceRange.start,
        end = token.sourceRange.end,
        replacement = content,
        selection = TextRange(
            start = token.sourceRange.start + selectionStart,
            end = token.sourceRange.start + selectionEnd,
        )
    )
}

private fun prefixCurrentLine(value: TextFieldValue, prefix: String): TextFieldValue {
    val cursor = value.selection.min
    val lineStart = value.text.lastIndexOf('\n', startIndex = (cursor - 1).coerceAtLeast(0)).let {
        if (it == -1) 0 else it + 1
    }
    val existingPrefixes = listOf("# ", "## ", "### ", "- ", "> ")
    val matched = existingPrefixes.firstOrNull { value.text.startsWith(it, lineStart) }
    return if (matched == prefix) {
        replaceRange(
            value = value,
            start = lineStart,
            end = lineStart + matched.length,
            replacement = "",
            selection = TextRange((cursor - matched.length).coerceAtLeast(lineStart))
        )
    } else {
        val withoutExisting = if (matched != null) {
            replaceRange(
                value = value,
                start = lineStart,
                end = lineStart + matched.length,
                replacement = "",
                selection = TextRange((cursor - matched.length).coerceAtLeast(lineStart))
            )
        } else {
            value
        }
        replaceRange(
            value = withoutExisting,
            start = lineStart,
            end = lineStart,
            replacement = prefix,
            selection = TextRange((cursor + prefix.length - (matched?.length ?: 0)).coerceAtLeast(lineStart))
        )
    }
}

private fun currentLineBlockRange(value: TextFieldValue): TextRange {
    val selection = value.selection
    val safeStart = selection.min.coerceAtLeast(0)
    val safeEnd = selection.max.coerceAtMost(value.text.length)
    val lineStart = value.text.lastIndexOf('\n', startIndex = (safeStart - 1).coerceAtLeast(0)).let {
        if (it == -1) 0 else it + 1
    }
    val lineEnd = value.text.indexOf('\n', startIndex = safeEnd).let {
        if (it == -1) value.text.length else it
    }
    return TextRange(lineStart, lineEnd)
}

private fun toggleBulletList(value: TextFieldValue): TextFieldValue {
    val blockRange = currentLineBlockRange(value)
    val block = value.text.substring(blockRange.start, blockRange.end)
    val lines = block.split('\n')
    val nonBlankLines = lines.filter { it.isNotBlank() }
    val bulletRegex = Regex("""^[-*]\s+""")
    val orderedRegex = Regex("""^\d+\.\s+""")
    if (nonBlankLines.isEmpty()) {
        return replaceRange(
            value = value,
            start = blockRange.start,
            end = blockRange.end,
            replacement = "- ",
            selection = TextRange(blockRange.start + 2)
        )
    }
    val updatedLines = when {
        nonBlankLines.isNotEmpty() && nonBlankLines.all { bulletRegex.containsMatchIn(it) } ->
            lines.map { line -> line.replaceFirst(bulletRegex, "") }
        else ->
            lines.map { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val stripped = line.replaceFirst(bulletRegex, "").replaceFirst(orderedRegex, "")
                    "- $stripped"
                }
            }
    }
    val replacement = updatedLines.joinToString("\n")
    return replaceRange(
        value = value,
        start = blockRange.start,
        end = blockRange.end,
        replacement = replacement,
        selection = TextRange(blockRange.start, blockRange.start + replacement.length)
    )
}

private fun toggleNumberedList(value: TextFieldValue): TextFieldValue {
    val blockRange = currentLineBlockRange(value)
    val block = value.text.substring(blockRange.start, blockRange.end)
    val lines = block.split('\n')
    val nonBlankLines = lines.filter { it.isNotBlank() }
    val bulletRegex = Regex("""^[-*]\s+""")
    val orderedRegex = Regex("""^\d+\.\s+""")
    if (nonBlankLines.isEmpty()) {
        return replaceRange(
            value = value,
            start = blockRange.start,
            end = blockRange.end,
            replacement = "1. ",
            selection = TextRange(blockRange.start + 3)
        )
    }
    val updatedLines = when {
        nonBlankLines.isNotEmpty() && nonBlankLines.all { orderedRegex.containsMatchIn(it) } ->
            lines.map { line -> line.replaceFirst(orderedRegex, "") }
        else ->
            lines.mapIndexed { index, line ->
                if (line.isBlank()) {
                    line
                } else {
                    val stripped = line.replaceFirst(bulletRegex, "").replaceFirst(orderedRegex, "")
                    "${index + 1}. $stripped"
                }
            }
    }
    val replacement = updatedLines.joinToString("\n")
    return replaceRange(
        value = value,
        start = blockRange.start,
        end = blockRange.end,
        replacement = replacement,
        selection = TextRange(blockRange.start, blockRange.start + replacement.length)
    )
}

private fun prefixSelectedLines(value: TextFieldValue, prefixFactory: (Int) -> String): TextFieldValue {
    val selection = value.selection
    val safeStart = selection.min.coerceAtLeast(0)
    val safeEnd = selection.max.coerceAtMost(value.text.length)
    val lineStart = value.text.lastIndexOf('\n', startIndex = (safeStart - 1).coerceAtLeast(0)).let {
        if (it == -1) 0 else it + 1
    }
    val lineEnd = value.text.indexOf('\n', startIndex = safeEnd).let {
        if (it == -1) value.text.length else it
    }
    val block = value.text.substring(lineStart, lineEnd)
    val replaced = block.split('\n').mapIndexed { index, line ->
        if (line.isBlank()) line else prefixFactory(index) + line
    }.joinToString("\n")
    return replaceRange(
        value = value,
        start = lineStart,
        end = lineEnd,
        replacement = replaced,
        selection = TextRange(lineStart, lineStart + replaced.length)
    )
}

private fun selectedText(value: TextFieldValue): String {
    if (value.selection.collapsed) return ""
    return value.text.substring(value.selection.min, value.selection.max)
}

private fun countMatches(text: String, query: String): Int {
    val normalized = query.trim()
    if (normalized.isEmpty()) return 0
    return Regex(Regex.escape(normalized), RegexOption.IGNORE_CASE).findAll(text).count()
}

private fun codeKeywordsFor(language: String): Set<String> =
    when (language.lowercase()) {
        "kotlin" -> setOf("val", "var", "fun", "class", "object", "data", "sealed", "when", "if", "else", "return", "null", "true", "false", "private", "public", "internal", "suspend", "override", "import", "package")
        "java" -> setOf("class", "interface", "public", "private", "protected", "static", "void", "return", "new", "if", "else", "switch", "case", "null", "true", "false", "import", "package")
        "javascript", "typescript", "jsx", "tsx" -> setOf("const", "let", "var", "function", "return", "if", "else", "switch", "case", "import", "export", "from", "async", "await", "true", "false", "null", "undefined", "class", "new")
        "python" -> setOf("def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as", "True", "False", "None", "async", "await", "lambda", "pass")
        "c", "cpp", "csharp", "go", "rust", "swift", "scala", "dart", "php" -> setOf("if", "else", "return", "class", "struct", "enum", "public", "private", "static", "fn", "func", "let", "var", "const", "new", "true", "false", "null")
        "sql" -> setOf("select", "from", "where", "join", "left", "right", "inner", "outer", "insert", "into", "update", "delete", "group", "by", "order", "limit", "and", "or", "as")
        "html", "xml" -> setOf("div", "span", "body", "head", "meta", "script", "style", "html")
        "json", "yaml", "toml", "ini" -> setOf("true", "false", "null")
        "bash", "shell", "powershell" -> setOf("if", "then", "else", "fi", "for", "do", "done", "function", "return", "true", "false")
        else -> emptySet()
    }

private fun commentPatternFor(language: String): Regex? =
    when (language.lowercase()) {
        "python", "yaml", "toml", "ini", "bash", "shell", "powershell", "ruby", "perl" -> Regex("""#.*$""")
        "sql" -> Regex("""--.*$""")
        "html", "xml" -> Regex("""<!--.*?-->""")
        "plaintext", "json" -> null
        else -> Regex("""//.*$""")
    }

private fun stringPatternsFor(language: String): List<Regex> =
    when (language.lowercase()) {
        "json" -> listOf(Regex(""""([^"\\\\]|\\\\.)*""""))
        else -> listOf(
            Regex(""""([^"\\\\]|\\\\.)*""""),
            Regex("""'([^'\\\\]|\\\\.)*'""")
        )
    }

private fun collectCodeStyleMatches(codeLine: String, language: String): List<CodeStyleMatch> {
    if (codeLine.isEmpty()) return emptyList()
    val matches = mutableListOf<CodeStyleMatch>()
    val occupied = BooleanArray(codeLine.length)

    fun canPlace(start: Int, endExclusive: Int): Boolean =
        start in 0..codeLine.length && endExclusive in 0..codeLine.length &&
            start < endExclusive &&
            (start until endExclusive).none { occupied[it] }

    fun addMatch(start: Int, endExclusive: Int, style: SpanStyle) {
        if (!canPlace(start, endExclusive)) return
        for (index in start until endExclusive) occupied[index] = true
        matches.add(CodeStyleMatch(start, endExclusive, style))
    }

    stringPatternsFor(language).forEach { regex ->
        regex.findAll(codeLine).forEach { match ->
            addMatch(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                style = SpanStyle(color = CodeStringColor)
            )
        }
    }

    commentPatternFor(language)?.findAll(codeLine)?.forEach { match ->
        addMatch(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            style = SpanStyle(color = CodeCommentColor, fontStyle = FontStyle.Italic)
        )
    }

    Regex("""\b\d+(?:\.\d+)?\b""").findAll(codeLine).forEach { match ->
        addMatch(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            style = SpanStyle(color = CodeNumberColor)
        )
    }

    val keywords = codeKeywordsFor(language)
    if (keywords.isNotEmpty()) {
        val keywordPattern = Regex(
            keywords.joinToString(prefix = """\b(""", postfix = """)\b""", separator = "|") { Regex.escape(it) },
            setOf(RegexOption.IGNORE_CASE)
        )
        keywordPattern.findAll(codeLine).forEach { match ->
            addMatch(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                style = SpanStyle(color = CodeKeywordColor, fontWeight = FontWeight.SemiBold)
            )
        }
    }

    Regex("""\b[A-Z][A-Za-z0-9_]*\b""").findAll(codeLine).forEach { match ->
        addMatch(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            style = SpanStyle(color = CodeTypeColor)
        )
    }

    return matches.sortedBy { it.start }
}

private fun buildCodeBlockInsertion(value: TextFieldValue, language: String, code: String): TextFieldValue {
    val normalizedCode = code.replace("\r\n", "\n").trimEnd('\n')
    val languageLabel = language.trim().ifBlank { "plaintext" }
    val before = value.text.take(value.selection.min)
    val after = value.text.drop(value.selection.max)
    val needsLeadingNewline = before.isNotEmpty() && !before.endsWith('\n')
    val needsTrailingNewline = after.isNotEmpty() && !after.startsWith('\n')
    val replacement = buildString {
        if (needsLeadingNewline) append('\n')
        append("```")
        append(languageLabel)
        append('\n')
        append(normalizedCode.ifBlank { "// code" })
        append('\n')
        append("```")
        if (needsTrailingNewline) append('\n')
    }
    val selectionStart = value.selection.min + replacement.length
    return replaceRange(value, value.selection.min, value.selection.max, replacement, TextRange(selectionStart))
}

private fun findClosingCodeFenceStart(markdown: String, searchFrom: Int): Int? {
    var cursor = searchFrom.coerceIn(0, markdown.length)
    while (cursor <= markdown.length) {
        val lineEnd = markdown.indexOf('\n', startIndex = cursor).let { if (it == -1) markdown.length else it }
        val line = markdown.substring(cursor, lineEnd)
        if (line.trimStart().startsWith("```")) return cursor
        if (lineEnd >= markdown.length) break
        cursor = lineEnd + 1
    }
    return null
}

private fun findNextOccurrence(value: TextFieldValue, query: String): TextRange? {
    val normalized = query.trim()
    if (normalized.isEmpty()) return null
    val regex = Regex(Regex.escape(normalized), RegexOption.IGNORE_CASE)
    val matches = regex.findAll(value.text).toList()
    if (matches.isEmpty()) return null
    val next = matches.firstOrNull { it.range.first > value.selection.min } ?: matches.first()
    return TextRange(next.range.first, next.range.last + 1)
}

private fun nextInlineToken(source: String, startIndex: Int): InlineToken? {
    val patterns = listOf(
        InlineTokenType.IMAGE to Regex("""!\[([^\]]*)]\(([^)]+)\)"""),
        InlineTokenType.WIKILINK to Regex("""\[\[([^\[\]\r\n]+)]]"""),
        InlineTokenType.LINK to Regex("""\[([^\]]+)]\(([^)]+)\)"""),
        InlineTokenType.CODE to Regex("""`([^`\n]+)`"""),
        InlineTokenType.BOLD to Regex("""\*\*([^\n]+?)\*\*"""),
        InlineTokenType.STRIKE to Regex("""~~([^\n]+?)~~"""),
        InlineTokenType.ITALIC to Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)"""),
    )
    return patterns.mapIndexedNotNull { index, (type, regex) ->
        regex.find(source, startIndex)?.let { InlineToken(type, it, index) }
    }.minWithOrNull(compareBy<InlineToken>({ it.match.range.first }, { it.priority }))
}

private fun buildRenderedDocument(markdown: String, accentColor: Color, linkColor: Color): RenderedDocument {
    val normalized = markdown.replace("\r\n", "\n")
    val sourceToRendered = IntArray(normalized.length + 1)
    val renderedToSource = mutableListOf(0)
    val tokens = mutableListOf<RenderedToken>()
    val hiddenRanges = mutableListOf<HiddenSourceRange>()
    val builder = AnnotatedString.Builder()
    var renderedLength = 0

    fun effectiveStyle(baseStyle: SpanStyle): SpanStyle =
        if (baseStyle.color == Color.Unspecified) {
            baseStyle.merge(SpanStyle(color = accentColor))
        } else {
            baseStyle
        }

    fun mapHiddenRange(start: Int, endExclusive: Int, snapOffset: Int = endExclusive) {
        val safeStart = start.coerceIn(0, normalized.length)
        val safeEnd = endExclusive.coerceIn(safeStart, normalized.length)
        for (offset in safeStart..safeEnd) {
            sourceToRendered[offset] = renderedLength
        }
        if (safeStart < safeEnd) {
            hiddenRanges.add(
                HiddenSourceRange(
                    range = TextRange(safeStart, safeEnd),
                    snapOffset = snapOffset.coerceIn(safeStart, safeEnd),
                )
            )
        }
    }

    fun appendRendered(
        renderedText: String,
        sourceStart: Int,
        sourceEndExclusive: Int,
        style: SpanStyle,
        annotationTag: String? = null,
        annotationValue: String? = null,
        tokenKind: TokenKind? = null,
        mappingSourceStart: Int = sourceStart,
        mappingSourceEndExclusive: Int = sourceEndExclusive,
    ) {
        val safeStart = sourceStart.coerceIn(0, normalized.length)
        val safeEnd = sourceEndExclusive.coerceIn(safeStart, normalized.length)
        val safeMappingStart = mappingSourceStart.coerceIn(0, normalized.length)
        val safeMappingEnd = mappingSourceEndExclusive.coerceIn(safeMappingStart, normalized.length)
        val renderedStart = renderedLength
        val sourceSpan = safeMappingEnd - safeMappingStart
        val renderedSpan = renderedText.length

        for (offset in safeMappingStart..safeMappingEnd) {
            val progress = if (sourceSpan == 0) 0 else ((offset - safeMappingStart) * renderedSpan) / sourceSpan
            sourceToRendered[offset] = renderedStart + progress.coerceIn(0, renderedSpan)
        }

        if (annotationTag != null && annotationValue != null) {
            builder.pushStringAnnotation(annotationTag, annotationValue)
        }
        builder.withStyle(effectiveStyle(style)) {
            append(renderedText)
        }
        if (annotationTag != null && annotationValue != null) {
            builder.pop()
        }

        if (renderedSpan > 0) {
            for (step in 1..renderedSpan) {
                val sourceOffset = safeMappingStart + if (renderedSpan == 0) 0 else (step * sourceSpan) / renderedSpan
                renderedToSource.add(sourceOffset.coerceIn(safeMappingStart, safeMappingEnd))
            }
        }

        renderedLength += renderedSpan
        if (tokenKind != null && renderedSpan > 0) {
            tokens.add(
                RenderedToken(
                    kind = tokenKind,
                    sourceRange = TextRange(safeStart, safeEnd),
                    renderedRange = TextRange(renderedStart, renderedLength),
                    payload = annotationValue,
                )
            )
        }
    }

    fun findInlineTokenInRange(start: Int, endExclusive: Int): InlineToken? =
        nextInlineToken(normalized, start)?.takeIf { it.match.range.first < endExclusive && it.match.range.last < endExclusive }

    fun renderInlineRange(start: Int, endExclusive: Int, baseStyle: SpanStyle) {
        var cursor = start
        while (cursor < endExclusive) {
            val next = findInlineTokenInRange(cursor, endExclusive)
            if (next == null) {
                appendRendered(
                    renderedText = normalized.substring(cursor, endExclusive),
                    sourceStart = cursor,
                    sourceEndExclusive = endExclusive,
                    style = baseStyle,
                )
                break
            }

            if (next.match.range.first > cursor) {
                appendRendered(
                    renderedText = normalized.substring(cursor, next.match.range.first),
                    sourceStart = cursor,
                    sourceEndExclusive = next.match.range.first,
                    style = baseStyle,
                )
            }

            val fullStart = next.match.range.first
            val fullEndExclusive = next.match.range.last + 1
            when (next.type) {
                InlineTokenType.IMAGE -> {
                    val alt = next.match.groupValues[1].ifBlank { "image" }
                    val url = next.match.groupValues[2]
                    val altStart = (normalized.indexOf('[', fullStart) + 1).coerceAtLeast(fullStart)
                    val altEnd = (normalized.indexOf(']', altStart)).takeIf { it >= altStart } ?: altStart
                    mapHiddenRange(fullStart, altStart, altStart)
                    mapHiddenRange(altEnd, fullEndExclusive, altEnd)
                    appendRendered(
                        renderedText = "Image: $alt",
                        sourceStart = fullStart,
                        sourceEndExclusive = fullEndExclusive,
                        style = baseStyle.merge(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)),
                        annotationTag = "URL",
                        annotationValue = url,
                        tokenKind = TokenKind.IMAGE,
                        mappingSourceStart = altStart,
                        mappingSourceEndExclusive = altEnd,
                    )
                }

                InlineTokenType.WIKILINK -> {
                    val title = next.match.groupValues[1].trim()
                    mapHiddenRange(fullStart, fullStart + 2, fullStart + 2)
                    mapHiddenRange(fullEndExclusive - 2, fullEndExclusive, fullEndExclusive - 2)
                    appendRendered(
                        renderedText = title,
                        sourceStart = fullStart,
                        sourceEndExclusive = fullEndExclusive,
                        style = baseStyle.merge(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)),
                        annotationTag = "WIKILINK",
                        annotationValue = title,
                        tokenKind = TokenKind.WIKILINK,
                        mappingSourceStart = fullStart + 2,
                        mappingSourceEndExclusive = fullEndExclusive - 2,
                    )
                }

                InlineTokenType.LINK -> {
                    val label = next.match.groupValues[1]
                    val url = next.match.groupValues[2]
                    val labelStart = fullStart + 1
                    val labelEnd = labelStart + label.length
                    mapHiddenRange(fullStart, labelStart, labelStart)
                    mapHiddenRange(labelEnd, fullEndExclusive, labelEnd)
                    appendRendered(
                        renderedText = label,
                        sourceStart = fullStart,
                        sourceEndExclusive = fullEndExclusive,
                        style = baseStyle.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        annotationTag = "URL",
                        annotationValue = url,
                        tokenKind = TokenKind.EXTERNAL_LINK,
                        mappingSourceStart = labelStart,
                        mappingSourceEndExclusive = labelEnd,
                    )
                }

                InlineTokenType.CODE -> {
                    mapHiddenRange(fullStart, fullStart + 1, fullStart + 1)
                    mapHiddenRange(fullEndExclusive - 1, fullEndExclusive, fullEndExclusive - 1)
                    appendRendered(
                        renderedText = next.match.groupValues[1],
                        sourceStart = fullStart,
                        sourceEndExclusive = fullEndExclusive,
                        style = baseStyle.merge(
                            SpanStyle(
                                background = Surface2Color,
                                fontFamily = FontFamily.Monospace,
                            )
                        ),
                        tokenKind = TokenKind.CODE,
                        mappingSourceStart = fullStart + 1,
                        mappingSourceEndExclusive = fullEndExclusive - 1,
                    )
                }

                InlineTokenType.BOLD -> {
                    val tokenRenderedStart = renderedLength
                    mapHiddenRange(fullStart, fullStart + 2, fullStart + 2)
                    renderInlineRange(fullStart + 2, fullEndExclusive - 2, baseStyle.merge(SpanStyle(fontWeight = FontWeight.Bold)))
                    mapHiddenRange(fullEndExclusive - 2, fullEndExclusive, fullEndExclusive - 2)
                    if (renderedLength > tokenRenderedStart) {
                        tokens.add(
                            RenderedToken(
                                kind = TokenKind.BOLD,
                                sourceRange = TextRange(fullStart, fullEndExclusive),
                                renderedRange = TextRange(tokenRenderedStart, renderedLength),
                            )
                        )
                    }
                }

                InlineTokenType.STRIKE -> {
                    val tokenRenderedStart = renderedLength
                    mapHiddenRange(fullStart, fullStart + 2, fullStart + 2)
                    renderInlineRange(
                        fullStart + 2,
                        fullEndExclusive - 2,
                        baseStyle.merge(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    )
                    mapHiddenRange(fullEndExclusive - 2, fullEndExclusive, fullEndExclusive - 2)
                    if (renderedLength > tokenRenderedStart) {
                        tokens.add(
                            RenderedToken(
                                kind = TokenKind.STRIKE,
                                sourceRange = TextRange(fullStart, fullEndExclusive),
                                renderedRange = TextRange(tokenRenderedStart, renderedLength),
                            )
                        )
                    }
                }

                InlineTokenType.ITALIC -> {
                    val tokenRenderedStart = renderedLength
                    mapHiddenRange(fullStart, fullStart + 1, fullStart + 1)
                    renderInlineRange(
                        fullStart + 1,
                        fullEndExclusive - 1,
                        baseStyle.merge(SpanStyle(fontStyle = FontStyle.Italic))
                    )
                    mapHiddenRange(fullEndExclusive - 1, fullEndExclusive, fullEndExclusive - 1)
                    if (renderedLength > tokenRenderedStart) {
                        tokens.add(
                            RenderedToken(
                                kind = TokenKind.ITALIC,
                                sourceRange = TextRange(fullStart, fullEndExclusive),
                                renderedRange = TextRange(tokenRenderedStart, renderedLength),
                            )
                        )
                    }
                }
            }
            cursor = fullEndExclusive
        }
    }

    var lineStart = 0
    var codeBlockLanguage = "plaintext"

    fun appendCodeLine(sourceStart: Int, sourceEndExclusive: Int, lineText: String) {
        appendRendered(
            renderedText = "│ ",
            sourceStart = sourceStart,
            sourceEndExclusive = sourceStart,
            style = SpanStyle(
                color = CodeBorderColor,
                background = CodeBlockBackground,
                fontFamily = FontFamily.Monospace,
            ),
            mappingSourceStart = sourceStart,
            mappingSourceEndExclusive = sourceStart,
        )
        val baseCodeStyle = SpanStyle(
            color = CodeDefaultText,
            background = CodeBlockBackground,
            fontFamily = FontFamily.Monospace,
        )
        val matches = collectCodeStyleMatches(lineText, codeBlockLanguage)
        var cursor = 0
        if (matches.isEmpty()) {
            appendRendered(
                renderedText = lineText,
                sourceStart = sourceStart,
                sourceEndExclusive = sourceEndExclusive,
                style = baseCodeStyle,
            )
            return
        }
        matches.forEach { match ->
            if (match.start > cursor) {
                appendRendered(
                    renderedText = lineText.substring(cursor, match.start),
                    sourceStart = sourceStart + cursor,
                    sourceEndExclusive = sourceStart + match.start,
                    style = baseCodeStyle,
                )
            }
            appendRendered(
                renderedText = lineText.substring(match.start, match.endExclusive),
                sourceStart = sourceStart + match.start,
                sourceEndExclusive = sourceStart + match.endExclusive,
                style = baseCodeStyle.merge(match.style),
            )
            cursor = match.endExclusive
        }
        if (cursor < lineText.length) {
            appendRendered(
                renderedText = lineText.substring(cursor),
                sourceStart = sourceStart + cursor,
                sourceEndExclusive = sourceEndExclusive,
                style = baseCodeStyle,
            )
        }
    }

    while (lineStart <= normalized.length) {
        val lineEnd = normalized.indexOf('\n', startIndex = lineStart).let { if (it == -1) normalized.length else it }
        val line = normalized.substring(lineStart, lineEnd)
        if (line.trimStart().startsWith("```")) {
            val closingFenceStart = findClosingCodeFenceStart(normalized, (lineEnd + 1).coerceAtMost(normalized.length))
            if (closingFenceStart != null) {
                val closingFenceEnd = normalized.indexOf('\n', startIndex = closingFenceStart).let {
                    if (it == -1) normalized.length else it
                }
                val language = line.trim().removePrefix("```").trim().ifBlank { "plaintext" }
                codeBlockLanguage = language
                val codeStart = (lineEnd + 1).coerceAtMost(normalized.length)
                val codeEndExclusive = closingFenceStart.coerceAtLeast(codeStart)

                mapHiddenRange(lineStart, lineEnd, lineEnd)
                appendRendered(
                    renderedText = "╭ $language",
                    sourceStart = lineStart,
                    sourceEndExclusive = lineEnd,
                    style = SpanStyle(
                        color = CodeDefaultText,
                        background = CodeHeaderBackground,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    mappingSourceStart = (lineStart + 3).coerceAtMost(lineEnd),
                    mappingSourceEndExclusive = lineEnd,
                )

                if (codeStart <= codeEndExclusive) {
                    appendRendered(
                        renderedText = "\n",
                        sourceStart = lineEnd.coerceAtMost(normalized.length),
                        sourceEndExclusive = (lineEnd + 1).coerceAtMost(normalized.length),
                        style = SpanStyle(
                            color = CodeDefaultText,
                            background = CodeHeaderBackground,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )

                    var codeCursor = codeStart
                    while (codeCursor <= codeEndExclusive) {
                        val codeLineEnd = normalized.indexOf('\n', startIndex = codeCursor).let {
                            if (it == -1 || it > codeEndExclusive) codeEndExclusive else it
                        }
                        val codeLine = normalized.substring(codeCursor, codeLineEnd)
                        appendCodeLine(codeCursor, codeLineEnd, codeLine)
                        if (codeLineEnd < codeEndExclusive) {
                            appendRendered(
                                renderedText = "\n",
                                sourceStart = codeLineEnd,
                                sourceEndExclusive = codeLineEnd + 1,
                                style = SpanStyle(
                                    color = CodeDefaultText,
                                    background = CodeBlockBackground,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                            codeCursor = codeLineEnd + 1
                        } else {
                            break
                        }
                    }
                    appendRendered(
                        renderedText = "\n",
                        sourceStart = closingFenceStart,
                        sourceEndExclusive = closingFenceStart,
                        style = SpanStyle(
                            color = CodeDefaultText,
                            background = CodeBlockBackground,
                            fontFamily = FontFamily.Monospace,
                        ),
                        mappingSourceStart = closingFenceStart,
                        mappingSourceEndExclusive = closingFenceStart,
                    )
                }

                appendRendered(
                    renderedText = "╰",
                    sourceStart = closingFenceStart,
                    sourceEndExclusive = closingFenceEnd,
                    style = SpanStyle(
                        color = CodeBorderColor,
                        background = CodeBlockBackground,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    mappingSourceStart = closingFenceStart,
                    mappingSourceEndExclusive = closingFenceStart,
                )
                mapHiddenRange(closingFenceStart, closingFenceEnd, closingFenceStart)

                if (closingFenceEnd < normalized.length) {
                    appendRendered(
                        renderedText = "\n",
                        sourceStart = closingFenceEnd,
                        sourceEndExclusive = closingFenceEnd + 1,
                        style = SpanStyle(),
                    )
                    lineStart = closingFenceEnd + 1
                } else {
                    break
                }
                continue
            }
        }

        when {
            line.startsWith("### ") -> {
                mapHiddenRange(lineStart, lineStart + 4, lineStart + 4)
                val renderedStart = renderedLength
                renderInlineRange(lineStart + 4, lineEnd, SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                if (renderedLength > renderedStart) {
                    tokens.add(
                        RenderedToken(
                            kind = TokenKind.HEADING_3,
                            sourceRange = TextRange(lineStart, lineEnd),
                            renderedRange = TextRange(renderedStart, renderedLength),
                        )
                    )
                }
            }

            line.startsWith("## ") -> {
                mapHiddenRange(lineStart, lineStart + 3, lineStart + 3)
                val renderedStart = renderedLength
                renderInlineRange(lineStart + 3, lineEnd, SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                if (renderedLength > renderedStart) {
                    tokens.add(
                        RenderedToken(
                            kind = TokenKind.HEADING_2,
                            sourceRange = TextRange(lineStart, lineEnd),
                            renderedRange = TextRange(renderedStart, renderedLength),
                        )
                    )
                }
            }

            line.startsWith("# ") -> {
                mapHiddenRange(lineStart, lineStart + 2, lineStart + 2)
                val renderedStart = renderedLength
                renderInlineRange(lineStart + 2, lineEnd, SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold))
                if (renderedLength > renderedStart) {
                    tokens.add(
                        RenderedToken(
                            kind = TokenKind.HEADING_1,
                            sourceRange = TextRange(lineStart, lineEnd),
                            renderedRange = TextRange(renderedStart, renderedLength),
                        )
                    )
                }
            }

            line.startsWith("> ") -> {
                appendRendered(
                    renderedText = "| ",
                    sourceStart = lineStart,
                    sourceEndExclusive = lineStart + 2,
                    style = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold),
                    tokenKind = TokenKind.BLOCKQUOTE,
                )
                renderInlineRange(lineStart + 2, lineEnd, SpanStyle(fontStyle = FontStyle.Italic))
            }

            line.startsWith("- ") || line.startsWith("* ") -> {
                appendRendered(
                    renderedText = "• ",
                    sourceStart = lineStart,
                    sourceEndExclusive = lineStart + 2,
                    style = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold),
                    tokenKind = TokenKind.BULLET_LIST,
                )
                renderInlineRange(lineStart + 2, lineEnd, SpanStyle())
            }

            Regex("""^\d+\. """).containsMatchIn(line) -> {
                val prefix = Regex("""^\d+\. """).find(line)?.value.orEmpty()
                appendRendered(
                    renderedText = prefix,
                    sourceStart = lineStart,
                    sourceEndExclusive = lineStart + prefix.length,
                    style = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold),
                    tokenKind = TokenKind.ORDERED_LIST,
                )
                renderInlineRange(lineStart + prefix.length, lineEnd, SpanStyle())
            }

            else -> renderInlineRange(lineStart, lineEnd, SpanStyle())
        }

        if (lineEnd < normalized.length) {
            appendRendered(
                renderedText = "\n",
                sourceStart = lineEnd,
                sourceEndExclusive = lineEnd + 1,
                style = SpanStyle(),
            )
            lineStart = lineEnd + 1
        } else {
            break
        }
    }

    if (renderedToSource.isNotEmpty()) {
        renderedToSource[renderedToSource.lastIndex] = normalized.length
    }

    return RenderedDocument(
        annotatedString = builder.toAnnotatedString(),
        rangeMap = RangeMap(
            sourceToRendered = sourceToRendered,
            renderedToSource = renderedToSource.toIntArray(),
        ),
        tokens = tokens,
        hiddenRanges = hiddenRanges,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: Note?,
    vm: NotesViewModel = viewModel(),
    onBack: () -> Unit,
    onLinkClick: (String, Note?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val systemUriHandler = LocalUriHandler.current
    val accentColor = LocalAccentColor.current
    val editorScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val initialMarkdown = remember(note?.id, note?.content) {
        storedContentToMarkdown(note?.content ?: "")
    }

    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var editorValue by remember(note?.id) { mutableStateOf(TextFieldValue(initialMarkdown)) }
    var savedId by remember(note?.id) { mutableStateOf(note?.id) }
    var syncing by remember(note?.id) { mutableStateOf(false) }
    var synced by remember(note?.id) { mutableStateOf(note != null) }
    var isLocked by remember(note?.id) { mutableStateOf(note?.isLocked ?: false) }
    var isPinned by remember(note?.id) { mutableStateOf(note?.isPinned ?: false) }
    var noteColorTag by remember(note?.id) { mutableStateOf(note?.colorTag) }
    var isUnlocked by remember(note?.id) { mutableStateOf(false) }
    var readMode by remember(note?.id) { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showRemoveLock by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var showLinkPopup by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(title) }
    var searchInput by remember { mutableStateOf("") }
    var replaceFromInput by remember { mutableStateOf("") }
    var replaceToInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    var selectedCodeLanguage by remember { mutableStateOf("plaintext") }
    var codeInput by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableStateOf(0) }

    var backlinks by remember { mutableStateOf<List<Note>>(emptyList()) }
    var wikiSuggestions by remember { mutableStateOf<List<Note>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<NoteAttachment>>(emptyList()) }
    var attachmentsBusy by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }
    var historyReady by remember { mutableStateOf(false) }
    var isHistoryAction by remember { mutableStateOf(false) }
    var lastSnapshot by remember(note?.id) { mutableStateOf(snapshotOf(TextFieldValue(initialMarkdown))) }
    var suppressNextLinkActivation by remember { mutableStateOf(false) }
    var editorLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var readLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val editorInteractionSource = remember { MutableInteractionSource() }

    val syncSuccess by vm.syncSuccess.collectAsState()
    val activeWikiLink = remember(editorValue.text, editorValue.selection) {
        findActiveWikiLink(editorValue.text, editorValue.selection.max)
    }
    val renderedDocument = remember(editorValue.text, accentColor) {
        buildRenderedDocument(editorValue.text, accentColor = accentColor, linkColor = TextColor)
    }
    val editorVisualTransformation = remember(renderedDocument) {
        RenderedDocumentVisualTransformation(renderedDocument)
    }
    val noteBackgroundColor: Color = remember(noteColorTag) { editorNoteBackgroundColor(noteColorTag) }

    fun scheduleSyncIfNeeded() {
        val currentId = savedId ?: return
        synced = false
        syncing = true
        vm.scheduleSync(currentId, title, editorValue.text)
    }

    fun ensureDraft(onReady: (String) -> Unit) {
        val currentId = savedId
        if (currentId != null) {
            onReady(currentId)
            return
        }
        val draftTitle = title.trim().ifBlank { "Sans titre" }
        syncing = true
        vm.createNote(draftTitle, editorValue.text) { newNote ->
            savedId = newNote.id
            syncing = false
            synced = true
            onReady(newNote.id)
        }
    }

    fun createInternalLink(rawTitle: String) {
        val normalized = rawTitle.trim().removePrefix("[[").removeSuffix("]]" ).trim()
        if (normalized.isBlank()) return
        editorValue = insertAtSelection(editorValue, "[[${normalized}]] ")
        vm.ensureHiddenNote(
            normalized,
            onError = { error ->
                editorScope.launch {
                    snackbarHostState.showSnackbar(error?.message ?: "Impossible de creer la note liee")
                }
            }
        )
        suppressNextLinkActivation = true
        showLinkPopup = false
        linkInput = ""
    }

    fun currentNoteSnapshot(noteId: String = savedId.orEmpty()): Note =
        Note(
            id = noteId,
            title = title,
            content = editorValue.text,
            updatedAt = note?.updatedAt ?: "",
            createdAt = note?.createdAt ?: (note?.updatedAt ?: ""),
            isHidden = note?.isHidden ?: false,
            isPinned = isPinned,
            isFavorite = note?.isFavorite ?: false,
            isLocked = isLocked,
            colorTag = noteColorTag,
            tags = note?.tags ?: emptyList(),
        )

    fun openLinkedNote(linkTitle: String) {
        val targetTitle = linkTitle.trim()
        if (targetTitle.isBlank()) return
        val hasDraftContent = title.isNotBlank() || editorValue.text.isNotBlank()
        when {
            savedId != null -> onLinkClick(targetTitle, currentNoteSnapshot(savedId!!))
            hasDraftContent -> ensureDraft { currentId ->
                onLinkClick(targetTitle, currentNoteSnapshot(currentId))
            }
            else -> onLinkClick(targetTitle, null)
        }
    }

    fun applySnapshot(snapshot: EditorSnapshot) {
        isHistoryAction = true
        editorValue = snapshot.toTextFieldValue()
    }

    fun undoEditor() {
        if (undoStack.isEmpty()) return
        redoStack.add(snapshotOf(editorValue))
        applySnapshot(undoStack.removeAt(undoStack.lastIndex))
    }

    fun redoEditor() {
        if (redoStack.isEmpty()) return
        undoStack.add(snapshotOf(editorValue))
        applySnapshot(redoStack.removeAt(redoStack.lastIndex))
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val pickedUri = uri ?: return@rememberLauncherForActivityResult
        ensureDraft { currentId ->
            editorScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { attachmentsBusy = true }
                try {
                    val resolver = context.contentResolver
                    val filename = resolver.query(
                        pickedUri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                    } ?: "attachment_${System.currentTimeMillis()}"
                    val tempFile = File(context.cacheDir, filename)
                    resolver.openInputStream(pickedUri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    val attachment = ApiService.uploadNoteAttachment(
                        noteId = currentId,
                        file = tempFile,
                        filename = filename,
                        mimeType = attachmentMimeType(filename, resolver.getType(pickedUri))
                    )
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        attachments = attachments + attachment
                        editorValue = insertAtSelection(editorValue, "[${attachment.filename}](${attachment.url})\n")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(e.message ?: "Impossible de joindre le fichier")
                    }
                } finally {
                    withContext(Dispatchers.Main) { attachmentsBusy = false }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        historyReady = true
        lastSnapshot = snapshotOf(editorValue)
        if (isLocked && !isUnlocked) showUnlockDialog = true
    }

    LaunchedEffect(syncSuccess) {
        if (syncSuccess) {
            syncing = false
            synced = true
            vm.clearSyncSuccess()
        }
    }

    LaunchedEffect(savedId) {
        val currentId = savedId
        if (currentId == null) {
            backlinks = emptyList()
            attachments = emptyList()
        } else {
            vm.loadBacklinks(currentId) { backlinks = it }
            editorScope.launch(Dispatchers.IO) {
                val loaded = runCatching { ApiService.getNoteAttachments(currentId) }.getOrDefault(emptyList())
                withContext(Dispatchers.Main) { attachments = loaded }
            }
        }
    }

    LaunchedEffect(editorValue.text) {
        extractWikiLinkTitles(editorValue.text).forEach { titleText ->
            vm.ensureHiddenNote(titleText)
        }
    }

    LaunchedEffect(editorValue.text, editorValue.selection) {
        val active = activeWikiLink
        if (active == null) {
            wikiSuggestions = emptyList()
        } else {
            delay(100)
            val query = active.query
            if (query.isBlank()) {
                wikiSuggestions = vm.notes.value.filterNot { it.isHidden }.take(6)
            } else {
                vm.searchNotes(query, includeHidden = false) { results ->
                    wikiSuggestions = results
                        .sortedByDescending { it.title.startsWith(query, ignoreCase = true) }
                        .filter { it.title.isNotBlank() }
                        .take(6)
                }
            }
        }
    }

    LaunchedEffect(editorValue.text, editorValue.selection, title, savedId) {
        if (!historyReady) return@LaunchedEffect
        val current = snapshotOf(editorValue)
        if (isHistoryAction) {
            isHistoryAction = false
        } else if (current != lastSnapshot) {
            undoStack.add(lastSnapshot)
            if (undoStack.size > 80) undoStack.removeAt(0)
            redoStack.clear()
        }
        lastSnapshot = current
        if (savedId != null) scheduleSyncIfNeeded()
    }

    LaunchedEffect(renderedDocument, readMode) {
        if (readMode) return@LaunchedEffect
        val normalizedSelection = renderedDocument.normalizeSelection(editorValue.selection)
        if (normalizedSelection != editorValue.selection) {
            editorValue = editorValue.copy(selection = normalizedSelection)
        }
    }

    LaunchedEffect(editorInteractionSource, editorLayoutResult, readMode, renderedDocument) {
        if (readMode) return@LaunchedEffect
        editorInteractionSource.interactions.collect { interaction ->
            if (interaction !is PressInteraction.Press) return@collect
            if (suppressNextLinkActivation) {
                suppressNextLinkActivation = false
                return@collect
            }
            val layout = editorLayoutResult ?: return@collect
            val text = renderedDocument.annotatedString.text
            if (text.isEmpty()) return@collect
            val offset = layout.getOffsetForPosition(interaction.pressPosition).coerceIn(0, text.lastIndex)
            renderedDocument.tokenAtRenderedOffset(offset, TokenKind.WIKILINK)?.let {
                openLinkedNote(it.payload.orEmpty())
            }
        }
    }

    val showContent = !isLocked || isUnlocked
    val isNewNote = savedId == null

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Supprimer cette note ?", fontWeight = FontWeight.Medium) },
            text = { Text("Cette action est irreversible.", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    savedId?.let { vm.deleteNote(it) }
                    showDeleteDialog = false
                    onBack()
                }) { Text("Supprimer", color = DangerColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler", color = MutedColor) }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Renommer", fontWeight = FontWeight.Medium) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        cursorColor = accentColor,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        title = renameInput
                        savedId?.let { vm.renameNote(it, renameInput) }
                    }
                    showRenameDialog = false
                }) { Text("Renommer", color = accentColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Annuler", color = MutedColor) }
            }
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Rechercher", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            searchMatchCount = countMatches(renderedDocument.annotatedString.text, it)
                        },
                        singleLine = true,
                        placeholder = { Text("Mot a rechercher", color = MutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            cursorColor = accentColor,
                        )
                    )
                    if (searchInput.isNotBlank()) {
                        Text(
                            "$searchMatchCount occurrence(s)",
                            color = if (searchMatchCount > 0) accentColor else MutedColor,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = findNextOccurrence(
                        value = editorValue.copy(
                            text = renderedDocument.annotatedString.text,
                            selection = TextRange(
                                renderedDocument.rangeMap.originalToRendered(editorValue.selection.min),
                                renderedDocument.rangeMap.originalToRendered(editorValue.selection.max)
                            )
                        ),
                        query = searchInput,
                    )?.let { renderedDocument.sourceRangeForRendered(it) }
                    if (next != null) {
                        editorValue = editorValue.copy(selection = next)
                        showSearchDialog = false
                    }
                }) { Text("Suivant", color = accentColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Fermer", color = MutedColor) }
            }
        )
    }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Remplacer", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = replaceFromInput,
                        onValueChange = { replaceFromInput = it },
                        singleLine = true,
                        label = { Text("Rechercher", color = MutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            cursorColor = accentColor,
                        )
                    )
                    OutlinedTextField(
                        value = replaceToInput,
                        onValueChange = { replaceToInput = it },
                        singleLine = true,
                        label = { Text("Remplacer par", color = MutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            cursorColor = accentColor,
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val from = replaceFromInput.trim()
                    if (from.isNotEmpty()) {
                        val matches = Regex(Regex.escape(from), RegexOption.IGNORE_CASE)
                            .findAll(renderedDocument.annotatedString.text)
                            .toList()
                        if (matches.isNotEmpty()) {
                            var updatedMarkdown = editorValue.text
                            matches.asReversed().forEach { match ->
                                val sourceRange = renderedDocument.sourceRangeForRendered(
                                    TextRange(match.range.first, match.range.last + 1)
                                )
                                updatedMarkdown = updatedMarkdown.replaceRange(
                                    sourceRange.min,
                                    sourceRange.max,
                                    replaceToInput
                                )
                            }
                            editorValue = TextFieldValue(
                                text = updatedMarkdown,
                                selection = TextRange(updatedMarkdown.length)
                            )
                        }
                    }
                    showReplaceDialog = false
                }) { Text("Remplacer", color = accentColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = false }) { Text("Annuler", color = MutedColor) }
            }
        )
    }

    if (showLinkPopup) {
        AlertDialog(
            onDismissRequest = { showLinkPopup = false; linkInput = "" },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Lien vers une note", fontWeight = FontWeight.Medium) },
            text = {
                Column {
                    Text("Nom de la note liee", color = MutedColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        singleLine = true,
                        placeholder = { Text("Nom de la note...", color = MutedColor) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { createInternalLink(linkInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            cursorColor = accentColor,
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { createInternalLink(linkInput) }) {
                    Text("Creer le lien", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkPopup = false; linkInput = "" }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    if (showCodeDialog) {
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Bloc de code", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Langage: $selectedCodeLanguage", color = accentColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        placeholder = { Text("Entre le code...", color = MutedColor) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = CodeDefaultText,
                            fontFamily = FontFamily.Monospace,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = CodeDefaultText,
                            unfocusedTextColor = CodeDefaultText,
                            cursorColor = accentColor,
                            focusedContainerColor = CodeBlockBackground,
                            unfocusedContainerColor = CodeBlockBackground,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editorValue = buildCodeBlockInsertion(editorValue, selectedCodeLanguage, codeInput)
                    showCodeDialog = false
                    codeInput = ""
                }) {
                    Text("Inserer", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCodeDialog = false
                    codeInput = ""
                }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    if (showLockDialog) {
        PinInputDialog(
            title = "Verrouiller la note",
            subtitle = "Choisir un PIN a 4 chiffres",
            error = pinError,
            value = pinInput,
            accentColor = accentColor,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    pinInput = it
                    pinError = false
                }
            },
            onConfirm = {
                if (pinInput.length == 4) {
                    savedId?.let { id ->
                        vm.lockNote(id, pinInput, onSuccess = {
                            isLocked = true
                            showLockDialog = false
                            pinInput = ""
                        }, onError = {
                            pinError = true
                            pinInput = ""
                        })
                    }
                } else pinError = true
            },
            onDismiss = {
                showLockDialog = false
                pinInput = ""
                pinError = false
            }
        )
    }

    if (showUnlockDialog) {
        PinInputDialog(
            title = "Note verrouillee",
            subtitle = "Entrer le PIN pour lire",
            error = pinError,
            value = pinInput,
            accentColor = accentColor,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    pinInput = it
                    pinError = false
                }
            },
            onConfirm = {
                if (pinInput.length == 4) {
                    savedId?.let { id ->
                        vm.unlockNote(id, pinInput, onSuccess = {
                            isUnlocked = true
                            showUnlockDialog = false
                            pinInput = ""
                        }, onError = {
                            pinError = true
                            pinInput = ""
                        })
                    }
                } else pinError = true
            },
            onDismiss = {
                showUnlockDialog = false
                pinInput = ""
                pinError = false
                onBack()
            }
        )
    }

    if (showRemoveLock) {
        PinInputDialog(
            title = "Retirer le verrou",
            subtitle = "Entrer le PIN actuel",
            error = pinError,
            value = pinInput,
            accentColor = accentColor,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    pinInput = it
                    pinError = false
                }
            },
            onConfirm = {
                if (pinInput.length == 4) {
                    savedId?.let { id ->
                        vm.removeNoteLock(id, pinInput, onSuccess = {
                            isLocked = false
                            isUnlocked = false
                            showRemoveLock = false
                            pinInput = ""
                        }, onError = {
                            pinError = true
                            pinInput = ""
                        })
                    }
                } else pinError = true
            },
            onDismiss = {
                showRemoveLock = false
                pinInput = ""
                pinError = false
            }
        )
    }

    fun saveNewNoteIfNeeded() {
        val draftTitle = title.trim().ifBlank { "Sans titre" }
        if (draftTitle.isBlank() && editorValue.text.trim().isBlank()) {
            onBack()
            return
        }
        if (savedId != null) return
        syncing = true
        vm.createNote(draftTitle, editorValue.text) { newNote ->
            savedId = newNote.id
            syncing = false
            synced = true
            isPinned = newNote.isPinned
            noteColorTag = newNote.colorTag
        }
    }

    Scaffold(
        containerColor = noteBackgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(noteBackgroundColor)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }

                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (savedId != null) scheduleSyncIfNeeded()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = showContent,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (title.isBlank()) {
                            Text("Sans titre", color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        innerTextField()
                    }
                )

                when {
                    syncing -> Text("···", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                    synced -> Text("✓", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }

                if (isNewNote) {
                    IconButton(onClick = { saveNewNoteIfNeeded() }, enabled = !syncing) {
                        Icon(Icons.Default.Check, contentDescription = "Enregistrer", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = {
                        when {
                            savedId == null -> ensureDraft {
                                if (isLocked) showRemoveLock = true else showLockDialog = true
                            }
                            isLocked -> showRemoveLock = true
                            else -> showLockDialog = true
                        }
                    }
                ) {
                    Icon(
                        if (isLocked) Icons.Outlined.LockOpen else Icons.Default.Lock,
                        contentDescription = "Verrou",
                        tint = if (isLocked) Color(0xFFFFD86B) else Color.White.copy(alpha = 0.62f),
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color(0xFF252525),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (readMode) "Mode écriture" else "Mode lecture", color = TextColor) },
                            onClick = { readMode = !readMode; showMenu = false },
                        )
                        HorizontalDivider(color = BorderColor)
                        DropdownMenuItem(
                            text = { Text(if (isPinned) "Désépingler" else "Épingler", color = TextColor) },
                            onClick = {
                                if (savedId == null) {
                                    ensureDraft { createdId ->
                                        vm.togglePin(createdId)
                                        isPinned = !isPinned
                                    }
                                } else {
                                    savedId?.let { vm.togglePin(it) }
                                    isPinned = !isPinned
                                }
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Renommer", color = TextColor) },
                            onClick = { renameInput = title; showRenameDialog = true; showMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Rechercher", color = TextColor) },
                            onClick = { showSearchDialog = true; showMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Remplacer", color = TextColor) },
                            onClick = { showReplaceDialog = true; showMenu = false },
                        )
                        HorizontalDivider(color = BorderColor)
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = DangerColor) },
                            onClick = { showDeleteDialog = true; showMenu = false },
                        )
                    }
                }
            }

            if (showContent) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (readMode) {
                        Text(
                            text = renderedDocument.annotatedString,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .pointerInput(renderedDocument) {
                                    detectTapGestures { position ->
                                        val offset = readLayoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                                        renderedDocument.tokenAtRenderedOffset(offset, TokenKind.WIKILINK)?.let {
                                            openLinkedNote(it.payload.orEmpty())
                                            return@detectTapGestures
                                        }
                                        renderedDocument.tokenAtRenderedOffset(offset, TokenKind.EXTERNAL_LINK)?.let {
                                            systemUriHandler.openUri(it.payload.orEmpty())
                                            return@detectTapGestures
                                        }
                                        renderedDocument.tokenAtRenderedOffset(offset, TokenKind.IMAGE)?.let {
                                            systemUriHandler.openUri(it.payload.orEmpty())
                                        }
                                    }
                                 }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE0E0E0), lineHeight = 26.sp),
                            onTextLayout = { readLayoutResult = it },
                        )
                    } else {
                        if (editorValue.text.isBlank()) {
                            Text(
                                "Commencez à écrire...",
                                color = Color.White.copy(alpha = 0.22f),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                        BasicTextField(
                            value = editorValue,
                            onValueChange = { editorValue = it },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE0E0E0), lineHeight = 26.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                            interactionSource = editorInteractionSource,
                            visualTransformation = editorVisualTransformation,
                            onTextLayout = { editorLayoutResult = it },
                            decorationBox = { innerTextField -> innerTextField() }
                        )
                    }
                }

                if (!readMode && activeWikiLink != null && (wikiSuggestions.isNotEmpty() || activeWikiLink.query.isNotBlank())) {
                    WikiSuggestionsCard(
                        suggestions = wikiSuggestions,
                        accentColor = accentColor,
                        allowCreate = activeWikiLink.query.isNotBlank(),
                        onSelect = { selected ->
                            val replacement = "[[${selected.title}]]"
                            editorValue = replaceRange(editorValue, activeWikiLink.openIndex, editorValue.selection.max, replacement, TextRange(activeWikiLink.openIndex + replacement.length))
                            wikiSuggestions = emptyList()
                            vm.ensureHiddenNote(selected.title)
                        },
                        onCreate = {
                            val query = activeWikiLink.query.trim()
                            if (query.isNotBlank()) {
                                val replacement = "[[${query}]]"
                                editorValue = replaceRange(editorValue, activeWikiLink.openIndex, editorValue.selection.max, replacement, TextRange(activeWikiLink.openIndex + replacement.length))
                                wikiSuggestions = emptyList()
                                vm.ensureHiddenNote(query)
                            }
                        }
                    )
                }

                if (savedId != null && backlinks.isNotEmpty()) {
                    BacklinksSection(backlinks = backlinks, accentColor = accentColor, onOpen = { linkedNote -> openLinkedNote(linkedNote.title) })
                }
                if (savedId != null && attachments.isNotEmpty()) {
                    AttachmentsSection(
                        attachments = attachments,
                        accentColor = accentColor,
                        onOpen = { attachment -> systemUriHandler.openUri(attachment.url) },
                        onDelete = { attachment ->
                            editorScope.launch(Dispatchers.IO) {
                                runCatching { ApiService.deleteNoteAttachment(savedId!!, attachment.id) }
                                    .onSuccess { withContext(Dispatchers.Main) { attachments = attachments.filterNot { it.id == attachment.id } } }
                                    .onFailure { withContext(Dispatchers.Main) { snackbarHostState.showSnackbar(it.message ?: "Suppression impossible") } }
                            }
                        }
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(0x1AFFD600), CircleShape)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xB3FFD84D), modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Note verrouillée", color = Color.White.copy(alpha = 0.72f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Entrez votre PIN pour accéder", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { showUnlockDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFD600), contentColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Déverrouiller")
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF171717))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${editorValue.text.length} caractères", color = Color.White.copy(alpha = 0.38f), fontSize = 12.sp)
                when {
                    readMode -> Text("Mode lecture", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    attachmentsBusy -> Text("Ajout piece jointe...", color = accentColor, fontSize = 12.sp)
                    syncing || !synced -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (syncing) "Sauvegarde..." else "Synchronisation...", color = MutedColor, fontSize = 12.sp)
                    }
                }
            }

            if (showContent && !readMode) {
                FormatToolbar(
                    onUndo = ::undoEditor,
                    onRedo = ::redoEditor,
                    onLink = {
                        linkInput = selectedText(editorValue).trim().removePrefix("[[").removeSuffix("]]" )
                        showLinkPopup = true
                    },
                    onTag = { editorValue = insertAtSelection(editorValue, "#tag ") },
                    onAttach = { attachmentPicker.launch("*/*") },
                    onHeading = { editorValue = prefixCurrentLine(editorValue, "# ") },
                    onBold = { editorValue = toggleInlineStyle(editorValue, renderedDocument, TokenKind.BOLD, "**", "**", "gras") },
                    onItalic = { editorValue = toggleInlineStyle(editorValue, renderedDocument, TokenKind.ITALIC, "*", "*", "italique") },
                    onStrike = { editorValue = toggleInlineStyle(editorValue, renderedDocument, TokenKind.STRIKE, "~~", "~~", "barre") },
                    codeLanguages = SupportedCodeLanguages,
                    onCodeLanguageSelected = { language ->
                        selectedCodeLanguage = language
                        codeInput = selectedText(editorValue)
                        showCodeDialog = true
                    },
                    onBulletList = { editorValue = toggleBulletList(editorValue) },
                    onNumberList = { editorValue = toggleNumberedList(editorValue) },
                    onTab = { editorValue = insertAtSelection(editorValue, "    ") },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        }
    }
}

@Composable
private fun FormatToolbar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLink: () -> Unit,
    onTag: () -> Unit,
    onAttach: () -> Unit,
    onHeading: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    codeLanguages: List<String>,
    onCodeLanguageSelected: (String) -> Unit,
    onBulletList: () -> Unit,
    onNumberList: () -> Unit,
    onTab: () -> Unit,
) {
    var showCodeMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().background(SurfaceColor).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TbIcon(Icons.AutoMirrored.Filled.Undo, "Annuler", onUndo)
        TbIcon(Icons.AutoMirrored.Filled.Redo, "Refaire", onRedo)
        TbSep()
        TbText("H", onHeading, bold = true)
        TbText("B", onBold, bold = true)
        TbText("I", onItalic, italic = true)
        TbText("S", onStrike)
        Box {
            TbIcon(Icons.Default.Code, "Code", onClick = { showCodeMenu = true })
            DropdownMenu(
                expanded = showCodeMenu,
                onDismissRequest = { showCodeMenu = false },
                containerColor = SurfaceColor,
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                codeLanguages.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                language,
                                color = TextColor,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        onClick = {
                            showCodeMenu = false
                            onCodeLanguageSelected(language)
                        }
                    )
                }
            }
        }
        TbSep()
        TbIcon(Icons.Default.Link, "Lien", onLink)
        TbText("#", onTag)
        TbIcon(Icons.Default.AttachFile, "Joindre", onAttach)
        TbSep()
        TbText("•", onBulletList, bold = true)
        TbIcon(Icons.Default.FormatListNumbered, "Liste 1.", onNumberList)
        TbSep()
        TbText("->", onTab)
    }
}

@Composable
private fun TbIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = desc, tint = TextColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TbText(label: String, onClick: () -> Unit, bold: Boolean = false, italic: Boolean = false) {
    Box(modifier = Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = TextColor, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
    }
}

@Composable
private fun TbSep() {
    Box(modifier = Modifier.width(1.dp).height(22.dp).background(BorderColor))
}

private fun editorNoteBackgroundColor(colorTag: String?): Color {
    return when (colorTag?.trim()?.lowercase()) {
        null, "", "default" -> Color(0xFF1F1F1F)
        "forest", "foret" -> Color(0xFF1A3A1F)
        "amber", "ambre" -> Color(0xFF3A2F1A)
        "ocean", "océan" -> Color(0xFF1A2F3A)
        "violet" -> Color(0xFF2A1A3A)
        "bordeaux" -> Color(0xFF3A1A2F)
        "slate", "ardoise" -> Color(0xFF1E2A2A)
        "rust", "rouille" -> Color(0xFF3A1F1A)
        else -> Color(0xFF1F1F1F)
    }
}

@Composable
private fun WikiSuggestionsCard(
    suggestions: List<Note>,
    accentColor: Color,
    allowCreate: Boolean,
    onSelect: (Note) -> Unit,
    onCreate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Wikilinks", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            suggestions.forEach { suggestion ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(suggestion) }.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(suggestion.title.ifBlank { "Sans titre" }, color = TextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (suggestion.isPinned || suggestion.isFavorite) {
                        Text("Existant", color = MutedColor, fontSize = 11.sp)
                    }
                }
            }
            if (allowCreate) {
                if (suggestions.isNotEmpty()) {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(top = 4.dp))
                }
                TextButton(onClick = onCreate, modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Creer une note liee", color = accentColor)
                }
            }
        }
    }
}

@Composable
private fun BacklinksSection(
    backlinks: List<Note>,
    accentColor: Color,
    onOpen: (Note) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Notes qui pointent ici", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(backlinks, key = { it.id }) { linked ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onOpen(linked) }.background(Surface2Color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(linked.title.ifBlank { "Sans titre" }, color = TextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("Ouvrir", color = MutedColor, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentsSection(
    attachments: List<NoteAttachment>,
    accentColor: Color,
    onOpen: (NoteAttachment) -> Unit,
    onDelete: (NoteAttachment) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pieces jointes", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            attachments.forEach { attachment ->
                Row(modifier = Modifier.fillMaxWidth().background(Surface2Color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(imageVector = when (attachment.mediaType) {
                        "image" -> Icons.Default.Image
                        "video" -> Icons.Default.VideoFile
                        else -> Icons.Default.AttachFile
                    }, contentDescription = null, tint = accentColor)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(attachment.filename, color = TextColor, fontSize = 14.sp, maxLines = 1)
                        Text("${attachment.mediaType} - ${attachment.size / 1024} Ko", color = MutedColor, fontSize = 11.sp)
                    }
                    TextButton(onClick = { onOpen(attachment) }) { Text("Ouvrir", color = accentColor) }
                    TextButton(onClick = { onDelete(attachment) }) { Text("Supprimer", color = DangerColor) }
                }
            }
        }
    }
}

@Composable
private fun PinInputDialog(
    title: String,
    subtitle: String,
    error: Boolean,
    value: String,
    accentColor: Color,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shakeIt by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val shakeOffset by animateFloatAsState(
        targetValue = if (shakeIt) 10f else 0f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 800f),
        finishedListener = { shakeIt = false },
        label = "notePinShake",
    )

    LaunchedEffect(error) {
        if (error) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeIt = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MutedColor,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.sp, color = MutedColor, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.graphicsLayer { translationX = shakeOffset },
                ) {
                    repeat(4) { index ->
                        val filled = index < value.length
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        shakeIt -> DangerColor
                                        filled -> accentColor
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    2.dp,
                                    when {
                                        shakeIt -> DangerColor
                                        filled -> accentColor
                                        else -> BorderColor
                                    },
                                    CircleShape,
                                )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            NotePinKey(
                                label = key,
                                enabled = key.isNotEmpty(),
                                isDelete = key == "⌫",
                                onClick = {
                                    when (key) {
                                        "⌫" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                        "" -> Unit
                                        else -> {
                                            if (value.length < 4) {
                                                val newValue = value + key
                                                onValueChange(newValue)
                                                if (newValue.length == 4) {
                                                    onConfirm()
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "PIN incorrect",
                        color = DangerColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss) {
                    Text("Annuler", color = MutedColor)
                }
            }
        }
    }
}

@Composable
private fun NotePinKey(
    label: String,
    enabled: Boolean,
    isDelete: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(74.dp, 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Surface2Color else Color.Transparent)
            .border(
                width = if (enabled) 1.dp else 0.dp,
                color = if (enabled) BorderColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            color = if (isDelete) DangerColor else TextColor,
            textAlign = TextAlign.Center,
        )
    }
}
