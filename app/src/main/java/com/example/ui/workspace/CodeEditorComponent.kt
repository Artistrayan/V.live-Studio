package com.example.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

// Syntax highlighting colors for dark developer theme
object SyntaxTheme {
    val Background = Color(0xFF1E1E2E)
    val LineNumbersBg = Color(0xFF181825)
    val LineNumbersText = Color(0xFF6C7086)
    val CursorColor = Color(0xFFF5E0DC)
    val DefaultText = Color(0xFFCDD6F4)
    val Keyword = Color(0xFFCBA6F7)
    val StringColor = Color(0xFFA6E3A1)
    val NumberColor = Color(0xFFFAB387)
    val FunctionColor = Color(0xFF89B4FA)
    val PropertyColor = Color(0xFFF9E2AF)
    val TagColor = Color(0xFFF38BA8)
    val AttributeColor = Color(0xFFF9E2AF)
    val CommentColor = Color(0xFF6C7086)
    val OperatorColor = Color(0xFF89DCEB)
    val BooleanColor = Color(0xFFEBA0AC)
    val SearchMatchBg = Color(0xFF585B70)
}

object SyntaxHighlighter {
    private val JS_KEYWORDS = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while",
        "do", "switch", "case", "break", "continue", "default", "class", "import",
        "export", "from", "as", "async", "await", "yield", "try", "catch", "finally",
        "throw", "new", "this", "super", "extends", "typeof", "instanceof", "void",
        "delete", "in", "of", "null", "undefined"
    )

    private val JS_BUILTINS = setOf(
        "console", "window", "document", "Math", "JSON", "Promise", "Array", "Object",
        "String", "Number", "Boolean", "fetch", "setTimeout", "setInterval", "addEventListener",
        "removeEventListener", "querySelector", "querySelectorAll", "getElementById"
    )

    private val CSS_PROPERTIES = setOf(
        "color", "background", "background-color", "font-size", "font-family", "font-weight",
        "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
        "padding", "padding-top", "padding-bottom", "padding-left", "padding-right",
        "display", "flex", "flex-direction", "justify-content", "align-items", "grid",
        "width", "height", "max-width", "max-height", "min-width", "min-height",
        "border", "border-radius", "box-shadow", "position", "top", "bottom", "left", "right",
        "z-index", "overflow", "opacity", "transform", "transition", "animation", "cursor"
    )

    fun highlight(code: String, extension: String): AnnotatedString {
        val ext = extension.lowercase()
        return when (ext) {
            "html", "htm", "xml" -> highlightHtml(code)
            "css" -> highlightCss(code)
            "js", "jsx", "ts", "tsx", "json" -> highlightJs(code)
            else -> highlightGeneric(code)
        }
    }

    private fun highlightHtml(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        // Default text style
        builder.addStyle(SpanStyle(color = SyntaxTheme.DefaultText), 0, code.length)

        // 1. HTML Comments: <!-- ... -->
        val commentMatcher = Pattern.compile("<!--[\\s\\S]*?-->").matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxTheme.CommentColor, fontStyle = FontStyle.Italic),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        // 2. HTML Tags: <tag ...> or </tag>
        val tagMatcher = Pattern.compile("<(/?[a-zA-Z0-9\\-]+)([^>]*)>").matcher(code)
        while (tagMatcher.find()) {
            val fullStart = tagMatcher.start()
            val fullEnd = tagMatcher.end()

            // Tag brackets & name
            val tagNameStart = tagMatcher.start(1)
            val tagNameEnd = tagMatcher.end(1)

            builder.addStyle(SpanStyle(color = SyntaxTheme.OperatorColor), fullStart, tagNameStart)
            builder.addStyle(SpanStyle(color = SyntaxTheme.TagColor, fontWeight = FontWeight.Bold), tagNameStart, tagNameEnd)
            builder.addStyle(SpanStyle(color = SyntaxTheme.OperatorColor), fullEnd - 1, fullEnd)

            // Attributes inside tag
            val attrs = tagMatcher.group(2)
            if (!attrs.isNullOrEmpty()) {
                val attrStartOffset = tagMatcher.start(2)
                val attrMatcher = Pattern.compile("([a-zA-Z0-9\\-_:@.]+)(?:=(\"[^\"]*\"|'[^']*'|[^\\s>]+))?").matcher(attrs)
                while (attrMatcher.find()) {
                    val attrNameStart = attrStartOffset + attrMatcher.start(1)
                    val attrNameEnd = attrStartOffset + attrMatcher.end(1)
                    builder.addStyle(SpanStyle(color = SyntaxTheme.AttributeColor), attrNameStart, attrNameEnd)

                    if (attrMatcher.group(2) != null) {
                        val valStart = attrStartOffset + attrMatcher.start(2)
                        val valEnd = attrStartOffset + attrMatcher.end(2)
                        builder.addStyle(SpanStyle(color = SyntaxTheme.StringColor), valStart, valEnd)
                    }
                }
            }
        }

        return builder.toAnnotatedString()
    }

    private fun highlightCss(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        builder.addStyle(SpanStyle(color = SyntaxTheme.DefaultText), 0, code.length)

        // Comments: /* ... */
        val commentMatcher = Pattern.compile("/\\*[\\s\\S]*?\\*/").matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxTheme.CommentColor, fontStyle = FontStyle.Italic),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        // CSS Selectors and declarations inside {...}
        val blockMatcher = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(code)
        while (blockMatcher.find()) {
            val selectorStart = blockMatcher.start(1)
            val selectorEnd = blockMatcher.end(1)
            builder.addStyle(SpanStyle(color = SyntaxTheme.TagColor, fontWeight = FontWeight.SemiBold), selectorStart, selectorEnd)

            val body = blockMatcher.group(2)
            if (!body.isNullOrEmpty()) {
                val bodyOffset = blockMatcher.start(2)
                val declMatcher = Pattern.compile("([a-zA-Z\\-]+)\\s*:\\s*([^;]+);?").matcher(body)
                while (declMatcher.find()) {
                    val propStart = bodyOffset + declMatcher.start(1)
                    val propEnd = bodyOffset + declMatcher.end(1)
                    val propName = declMatcher.group(1).lowercase()
                    if (CSS_PROPERTIES.contains(propName)) {
                        builder.addStyle(SpanStyle(color = SyntaxTheme.PropertyColor), propStart, propEnd)
                    } else {
                        builder.addStyle(SpanStyle(color = SyntaxTheme.AttributeColor), propStart, propEnd)
                    }

                    val valStart = bodyOffset + declMatcher.start(2)
                    val valEnd = bodyOffset + declMatcher.end(2)
                    builder.addStyle(SpanStyle(color = SyntaxTheme.StringColor), valStart, valEnd)
                }
            }
        }

        // Numbers with units: 10px, 2rem, 100%, 0.5s
        val numMatcher = Pattern.compile("\\b(\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|s|ms|deg)?)\\b").matcher(code)
        while (numMatcher.find()) {
            builder.addStyle(SpanStyle(color = SyntaxTheme.NumberColor), numMatcher.start(), numMatcher.end())
        }

        return builder.toAnnotatedString()
    }

    private fun highlightJs(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        builder.addStyle(SpanStyle(color = SyntaxTheme.DefaultText), 0, code.length)

        // 1. Strings ("...", '...', `...`)
        val stringMatcher = Pattern.compile("(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'|`[^`\\\\]*(?:\\\\.[^`\\\\]*)*`)").matcher(code)
        while (stringMatcher.find()) {
            builder.addStyle(SpanStyle(color = SyntaxTheme.StringColor), stringMatcher.start(), stringMatcher.end())
        }

        // 2. Numbers (123, 45.67, 0xFF)
        val numMatcher = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b").matcher(code)
        while (numMatcher.find()) {
            builder.addStyle(SpanStyle(color = SyntaxTheme.NumberColor), numMatcher.start(), numMatcher.end())
        }

        // 3. Identifiers / Keywords / Built-ins / Function names
        val wordMatcher = Pattern.compile("\\b([a-zA-Z_$][a-zA-Z0-9_$]*)\\b").matcher(code)
        while (wordMatcher.find()) {
            val word = wordMatcher.group(1)
            val start = wordMatcher.start()
            val end = wordMatcher.end()

            when {
                word == "true" || word == "false" -> {
                    builder.addStyle(SpanStyle(color = SyntaxTheme.BooleanColor, fontWeight = FontWeight.Bold), start, end)
                }
                JS_KEYWORDS.contains(word) -> {
                    builder.addStyle(SpanStyle(color = SyntaxTheme.Keyword, fontWeight = FontWeight.Bold), start, end)
                }
                JS_BUILTINS.contains(word) -> {
                    builder.addStyle(SpanStyle(color = SyntaxTheme.FunctionColor, fontWeight = FontWeight.SemiBold), start, end)
                }
                end < code.length && code[end] == '(' -> {
                    // Function invocation: funcName(...)
                    builder.addStyle(SpanStyle(color = SyntaxTheme.FunctionColor), start, end)
                }
            }
        }

        // 4. Single-line and Multi-line Comments (applied last to override any inside strings)
        val commentMatcher = Pattern.compile("(//[^\n]*|/\\*[\\s\\S]*?\\*/)").matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxTheme.CommentColor, fontStyle = FontStyle.Italic),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        return builder.toAnnotatedString()
    }

    private fun highlightGeneric(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        builder.addStyle(SpanStyle(color = SyntaxTheme.DefaultText), 0, code.length)

        // Strings
        val stringMatcher = Pattern.compile("(\"[^\"]*\"|'[^']*')").matcher(code)
        while (stringMatcher.find()) {
            builder.addStyle(SpanStyle(color = SyntaxTheme.StringColor), stringMatcher.start(), stringMatcher.end())
        }

        // Comments
        val commentMatcher = Pattern.compile("(//[^\n]*|/\\*[\\s\\S]*?\\*|#[^\n]*)").matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxTheme.CommentColor, fontStyle = FontStyle.Italic),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        // Numbers
        val numMatcher = Pattern.compile("\\b(\\d+(\\.\\d+)?)\\b").matcher(code)
        while (numMatcher.find()) {
            builder.addStyle(SpanStyle(color = SyntaxTheme.NumberColor), numMatcher.start(), numMatcher.end())
        }

        return builder.toAnnotatedString()
    }
}

class SyntaxHighlightTransformation(
    private val extension: String,
    private val searchQuery: String = ""
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val baseHighlighted = SyntaxHighlighter.highlight(text.text, extension)
        if (searchQuery.isBlank()) {
            return TransformedText(baseHighlighted, OffsetMapping.Identity)
        }

        // Add search query highlighting
        val builder = AnnotatedString.Builder(baseHighlighted)
        var index = text.text.indexOf(searchQuery, ignoreCase = true)
        while (index >= 0) {
            builder.addStyle(
                SpanStyle(
                    background = SyntaxTheme.SearchMatchBg,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                index,
                index + searchQuery.length
            )
            index = text.text.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun SyntaxHighlightedCodeEditor(
    content: String,
    fileName: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fileExtension = remember(fileName) { fileName.substringAfterLast('.', "js") }
    var textFieldValue by remember(content) {
        mutableStateOf(
            TextFieldValue(
                text = content,
                selection = TextRange(content.length)
            )
        )
    }

    // Keep textFieldValue text synchronized when external changes arrive (e.g. AI Apply)
    LaunchedEffect(content) {
        if (content != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = content)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val lines = remember(textFieldValue.text) {
        val list = textFieldValue.text.lines()
        if (list.isEmpty()) listOf("") else list
    }
    val lineCount = lines.size

    val visualTransformation = remember(fileExtension, searchQuery) {
        SyntaxHighlightTransformation(fileExtension, searchQuery)
    }

    val editorHorizontalScroll = rememberScrollState()
    val editorVerticalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SyntaxTheme.Background)
    ) {
        // Search bar (collapsible)
        if (showSearchBar) {
            Surface(
                color = SyntaxTheme.LineNumbersBg,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = SyntaxTheme.Keyword,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Find in $fileName...", fontSize = 12.sp, color = SyntaxTheme.LineNumbersText) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("code_search_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SyntaxTheme.Keyword,
                            unfocusedBorderColor = SyntaxTheme.LineNumbersText.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            searchQuery = ""
                            showSearchBar = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.LightGray)
                    }
                }
            }
        }

        // Main Editor Canvas with Line Numbers
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(SyntaxTheme.LineNumbersBg)
                    .verticalScroll(editorVerticalScroll)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SyntaxTheme.LineNumbersText,
                        lineHeight = 22.sp
                    )
                }
            }

            // Editable Code Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(editorVerticalScroll)
                    .horizontalScroll(editorHorizontalScroll)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = "// Write $fileExtension code for $fileName...",
                        color = SyntaxTheme.LineNumbersText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChange(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("syntax_code_editor_input"),
                    visualTransformation = visualTransformation,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = SyntaxTheme.DefaultText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(SyntaxTheme.CursorColor),
                    keyboardOptions = KeyboardOptions.Default
                )
            }
        }

        // Quick Code Insertion Symbols & Action Bar
        Surface(
            color = SyntaxTheme.LineNumbersBg,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Quick insertion chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val codeSymbols = listOf(
                        "Tab" to "  ",
                        "{" to "{}",
                        "}" to "}",
                        "(" to "()",
                        ")" to ")",
                        "[" to "[]",
                        "]" to "]",
                        "<" to "<>",
                        ">" to ">",
                        "\"" to "\"\"",
                        "'" to "''",
                        "`" to "``",
                        "=" to " = ",
                        "=>" to " => ",
                        ";" to ";",
                        ":" to ": ",
                        "." to ".",
                        "/" to "/",
                        "!" to "!",
                        "&" to "&&",
                        "|" to "||"
                    )

                    codeSymbols.forEach { (label, insertValue) ->
                        Surface(
                            onClick = {
                                val currentText = textFieldValue.text
                                val selStart = textFieldValue.selection.start
                                val selEnd = textFieldValue.selection.end
                                val newText = currentText.replaceRange(selStart, selEnd, insertValue)
                                val newCursorPos = selStart + insertValue.length
                                textFieldValue = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newCursorPos)
                                )
                                onContentChange(newText)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = SyntaxTheme.Background,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SyntaxTheme.Keyword
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF313244), thickness = 0.5.dp)

                // Editor Bottom Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Language badge & File stats
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SyntaxTheme.Keyword.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = fileExtension.uppercase(),
                                color = SyntaxTheme.Keyword,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "Lines: $lineCount | Chars: ${textFieldValue.text.length}",
                            color = SyntaxTheme.LineNumbersText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Right: Actions (Find, Copy, Clear)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.size(28.dp).testTag("editor_search_btn")
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showSearchBar) SyntaxTheme.Keyword else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(textFieldValue.text))
                            },
                            modifier = Modifier.size(28.dp).testTag("editor_copy_btn")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy All",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                textFieldValue = TextFieldValue("")
                                onContentChange("")
                            },
                            modifier = Modifier.size(28.dp).testTag("editor_clear_btn")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear Code",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
