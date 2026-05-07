package org.sase.mobile.ui.launch

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptInputWire
import org.sase.mobile.data.api.dto.referenceText

data class ActiveXpromptArgHint(
    val entry: MobileXpromptCatalogEntryWire,
    val activeIndex: Int = 0,
    val referenceRange: TextRange,
)

data class PromptInsertion(
    val value: TextFieldValue,
    val insertedRange: TextRange,
)

data class PromptHintEdit(
    val value: TextFieldValue,
    val hint: ActiveXpromptArgHint,
)

data class ParsedPromptReferenceToken(
    val lookupName: String,
    val tokenRange: TextRange,
)

fun MobileXpromptCatalogEntryWire.visibleInputs(): List<MobileXpromptInputWire> {
    return inputs.sortedBy { it.position }
}

fun MobileXpromptCatalogEntryWire.requiredVisibleInputs(): List<MobileXpromptInputWire> {
    return visibleInputs().filter { it.required }
}

fun MobileXpromptCatalogEntryWire.optionalVisibleInputs(): List<MobileXpromptInputWire> {
    return visibleInputs().filterNot { it.required }
}

fun MobileXpromptCatalogEntryWire.hasRequiredVisibleInputs(): Boolean {
    return requiredVisibleInputs().isNotEmpty()
}

fun MobileXpromptInputWire.hintDisplayText(): String {
    val typeSuffix = type.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    val defaultSuffix = defaultDisplay?.let { " = $it" }.orEmpty()
    return "$name$typeSuffix$defaultSuffix"
}

internal fun hintForSelectedXprompt(
    entry: MobileXpromptCatalogEntryWire,
    referenceRange: TextRange,
): ActiveXpromptArgHint? {
    return if (entry.hasRequiredVisibleInputs()) {
        ActiveXpromptArgHint(
            entry = entry,
            activeIndex = 0,
            referenceRange = referenceRange,
        )
    } else {
        null
    }
}

internal fun activeXpromptArgHint(
    value: TextFieldValue,
    catalogByName: Map<String, MobileXpromptCatalogEntryWire>,
): ActiveXpromptArgHint? {
    if (value.selection.start != value.selection.end) {
        return null
    }
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val token = typedColonReferenceToken(value.text, cursor) ?: return null
    val parsed = parsePromptReferenceToken(token.text, token.range.start) ?: return null
    val entry = catalogByName[parsed.lookupName]
        ?: catalogByName[parsed.lookupName.replace("__", "/")]
        ?: return null
    return hintForSelectedXprompt(entry, parsed.tokenRange)
}

internal fun parsePromptReferenceToken(
    token: String,
    tokenStart: Int = 0,
): ParsedPromptReferenceToken? {
    if (!token.endsWith(":")) {
        return null
    }
    val body = token.dropLast(1)
    val nameWithSuffix = when {
        body.startsWith("#!") -> body.drop(2)
        body.startsWith("#") -> body.drop(1)
        else -> return null
    }
    if (nameWithSuffix.isEmpty() || nameWithSuffix.endsWith("+")) {
        return null
    }
    val lookupName = nameWithSuffix
        .removeSuffix("!!")
        .removeSuffix("??")
    if (lookupName.isEmpty() || !lookupName.all(::isPromptReferenceNameChar)) {
        return null
    }
    return ParsedPromptReferenceToken(
        lookupName = lookupName,
        tokenRange = TextRange(tokenStart, tokenStart + token.length),
    )
}

internal fun insertPromptSnippetWithRange(
    prompt: TextFieldValue,
    snippet: String,
): PromptInsertion {
    val range = prompt.selection
    val start = minOf(range.start, range.end).coerceIn(0, prompt.text.length)
    val end = maxOf(range.start, range.end).coerceIn(0, prompt.text.length)
    val text = prompt.text.replaceRange(start, end, snippet)
    val cursor = start + snippet.length
    val value = TextFieldValue(text = text, selection = TextRange(cursor))
    return PromptInsertion(
        value = value,
        insertedRange = TextRange(start, cursor),
    )
}

private data class PromptReferenceToken(
    val text: String,
    val range: TextRange,
)

private fun typedColonReferenceToken(text: String, cursor: Int): PromptReferenceToken? {
    if (cursor == 0 || text[cursor - 1] != ':') {
        return null
    }
    val markerIndex = text.lastIndexOf('#', startIndex = cursor - 1)
    if (markerIndex < 0 || !hasValidReferenceLeadingContext(text, markerIndex)) {
        return null
    }
    val token = text.substring(markerIndex, cursor)
    return PromptReferenceToken(token, TextRange(markerIndex, cursor))
}

private fun hasValidReferenceLeadingContext(text: String, markerIndex: Int): Boolean {
    if (markerIndex == 0) {
        return true
    }
    return when (text[markerIndex - 1]) {
        ' ', '\t', '\n', '\r', '(', '[', '{', '"', '\'' -> true
        else -> false
    }
}

private fun isPromptReferenceNameChar(char: Char): Boolean {
    return char.isLetterOrDigit() ||
        char == '_' ||
        char == '-' ||
        char == '/' ||
        char == '.'
}

internal fun rewriteActiveXpromptAsColon(
    prompt: TextFieldValue,
    hint: ActiveXpromptArgHint,
): PromptHintEdit {
    return replaceHintReference(prompt, hint, "${hint.entry.referenceText()}:")
}

internal fun rewriteActiveXpromptAsNamedArgs(
    prompt: TextFieldValue,
    hint: ActiveXpromptArgHint,
): PromptHintEdit {
    val inputs = hint.entry.visibleInputs()
    if (inputs.isEmpty()) {
        return replaceHintReference(prompt, hint, hint.entry.referenceText())
    }
    val reference = hint.entry.referenceText()
    val args = inputs.joinToString(", ") { "${it.name}=" }
    val snippet = "$reference($args)"
    val firstCursor = reference.length + 1 + inputs.first().name.length + 1
    return replaceHintReference(prompt, hint, snippet, cursorOffset = firstCursor)
}

private fun replaceHintReference(
    prompt: TextFieldValue,
    hint: ActiveXpromptArgHint,
    replacement: String,
    cursorOffset: Int = replacement.length,
): PromptHintEdit {
    val start = minOf(hint.referenceRange.start, hint.referenceRange.end).coerceIn(0, prompt.text.length)
    val end = maxOf(hint.referenceRange.start, hint.referenceRange.end).coerceIn(start, prompt.text.length)
    val text = prompt.text.replaceRange(start, end, replacement)
    val cursor = (start + cursorOffset).coerceIn(0, text.length)
    val newRange = TextRange(start, start + replacement.length)
    return PromptHintEdit(
        value = TextFieldValue(text = text, selection = TextRange(cursor)),
        hint = hint.copy(referenceRange = newRange),
    )
}
