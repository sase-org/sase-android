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
