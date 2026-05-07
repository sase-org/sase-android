package org.sase.mobile.ui.launch

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptInputWire

class XpromptArgHintsTest {
    @Test
    fun selectedHintOnlyExistsForRequiredInputs() {
        val range = TextRange(0, 4)

        assertThat(hintForSelectedXprompt(entry(inputs = emptyList()), range)).isNull()
        assertThat(
            hintForSelectedXprompt(
                entry(
                    inputs = listOf(input("mode", required = false)),
                ),
                range,
            ),
        ).isNull()
        assertThat(
            hintForSelectedXprompt(
                entry(
                    inputs = listOf(input("bead_id", required = true)),
                ),
                range,
            ),
        ).isNotNull()
    }

    @Test
    fun colonRewritePreservesSurroundingPromptAndMovesCursorAfterColon() {
        val prompt = TextFieldValue("before #foo after", selection = TextRange(17))
        val hint = ActiveXpromptArgHint(
            entry = entry(),
            referenceRange = TextRange(7, 11),
        )

        val edit = rewriteActiveXpromptAsColon(prompt, hint)

        assertThat(edit.value.text).isEqualTo("before #foo: after")
        assertThat(edit.value.selection.start).isEqualTo("before #foo:".length)
        assertThat(edit.hint.referenceRange).isEqualTo(TextRange(7, 12))
    }

    @Test
    fun namedArgsRewritePreservesSurroundingPromptAndMovesCursorAfterFirstEquals() {
        val prompt = TextFieldValue("before #foo after", selection = TextRange(17))
        val hint = ActiveXpromptArgHint(
            entry = entry(
                inputs = listOf(
                    input("bead_id", required = true, position = 0),
                    input("mode", required = false, position = 1),
                ),
            ),
            referenceRange = TextRange(7, 11),
        )

        val edit = rewriteActiveXpromptAsNamedArgs(prompt, hint)

        assertThat(edit.value.text).isEqualTo("before #foo(bead_id=, mode=) after")
        assertThat(edit.value.selection.start).isEqualTo("before #foo(bead_id=".length)
        assertThat(edit.hint.referenceRange).isEqualTo(TextRange(7, 28))
    }

    @Test
    fun visibleInputsAreSortedBeforeRenderingOrNamedArgs() {
        val catalogEntry = entry(
            inputs = listOf(
                input("second", required = false, position = 2),
                input("first", required = true, position = 1),
            ),
        )

        assertThat(catalogEntry.visibleInputs().map { it.name }).containsExactly("first", "second").inOrder()
        assertThat(catalogEntry.requiredVisibleInputs().map { it.name }).containsExactly("first")
        assertThat(catalogEntry.optionalVisibleInputs().map { it.name }).containsExactly("second")
    }

    @Test
    fun typedColonHintMatchesPlainAndStandaloneReferences() {
        val catalog = mapOf("foo" to entry(inputs = listOf(input("bead_id", required = true))))

        assertThat(activeXpromptArgHint(prompt("#foo:"), catalog)?.entry?.name).isEqualTo("foo")
        assertThat(activeXpromptArgHint(prompt("#!foo:"), catalog)?.entry?.name).isEqualTo("foo")
    }

    @Test
    fun typedColonHintMatchesNamespacedSlashAndDoubleUnderscoreAlias() {
        val catalog = mapOf(
            "bd/work_phase_bead" to entry(
                name = "bd/work_phase_bead",
                inputs = listOf(input("bead_id", required = true)),
            ),
        )

        assertThat(activeXpromptArgHint(prompt("#bd/work_phase_bead:"), catalog)?.entry?.name)
            .isEqualTo("bd/work_phase_bead")
        assertThat(activeXpromptArgHint(prompt("#bd__work_phase_bead:"), catalog)?.entry?.name)
            .isEqualTo("bd/work_phase_bead")
    }

    @Test
    fun typedColonHintStripsForceAndQuestionSuffixesBeforeLookup() {
        val catalog = mapOf("foo" to entry(inputs = listOf(input("bead_id", required = true))))

        assertThat(activeXpromptArgHint(prompt("#foo!!:"), catalog)?.entry?.name).isEqualTo("foo")
        assertThat(activeXpromptArgHint(prompt("#foo??:"), catalog)?.entry?.name).isEqualTo("foo")
    }

    @Test
    fun typedColonHintRequiresKnownRequiredInputEntryAtExactCursorColon() {
        val catalog = mapOf(
            "foo" to entry(inputs = listOf(input("bead_id", required = true))),
            "optional" to entry(name = "optional", inputs = listOf(input("mode", required = false))),
        )

        assertThat(activeXpromptArgHint(prompt("#missing:"), catalog)).isNull()
        assertThat(activeXpromptArgHint(prompt("#optional:"), catalog)).isNull()
        assertThat(activeXpromptArgHint(prompt("#foo+"), catalog)).isNull()
        assertThat(activeXpromptArgHint(prompt("#foo:done"), catalog)).isNull()
        assertThat(
            activeXpromptArgHint(
                TextFieldValue("#foo:", selection = TextRange(0, 5)),
                catalog,
            ),
        ).isNull()
    }

    @Test
    fun typedColonHintRejectsInvalidLeadingContext() {
        val catalog = mapOf("bar" to entry(name = "bar", inputs = listOf(input("bead_id", required = true))))

        assertThat(activeXpromptArgHint(prompt("foo#bar:"), catalog)).isNull()
        assertThat(activeXpromptArgHint(prompt("https://example.test/#bar:"), catalog)).isNull()
        assertThat(activeXpromptArgHint(prompt("(#bar:"), catalog)?.entry?.name).isEqualTo("bar")
        assertThat(activeXpromptArgHint(prompt("[#bar:"), catalog)?.entry?.name).isEqualTo("bar")
        assertThat(activeXpromptArgHint(prompt("{#bar:"), catalog)?.entry?.name).isEqualTo("bar")
        assertThat(activeXpromptArgHint(prompt("\"#bar:"), catalog)?.entry?.name).isEqualTo("bar")
        assertThat(activeXpromptArgHint(prompt("'#bar:"), catalog)?.entry?.name).isEqualTo("bar")
        assertThat(activeXpromptArgHint(prompt("before #bar:"), catalog)?.entry?.name).isEqualTo("bar")
    }

    @Test
    fun parsePromptReferenceTokenReturnsTokenRangeIncludingColon() {
        val parsed = parsePromptReferenceToken("#foo:", tokenStart = 7)

        assertThat(parsed?.lookupName).isEqualTo("foo")
        assertThat(parsed?.tokenRange).isEqualTo(TextRange(7, 12))
    }

    private fun prompt(text: String): TextFieldValue {
        return TextFieldValue(text, selection = TextRange(text.length))
    }

    private fun entry(
        name: String = "foo",
        inputs: List<MobileXpromptInputWire> = emptyList(),
    ): MobileXpromptCatalogEntryWire {
        return MobileXpromptCatalogEntryWire(
            name = name,
            displayLabel = name,
            sourceBucket = "project",
            project = "sase",
            tags = emptyList(),
            inputs = inputs,
            isSkill = false,
        )
    }

    private fun input(
        name: String,
        required: Boolean,
        position: Int = 0,
    ): MobileXpromptInputWire {
        return MobileXpromptInputWire(
            name = name,
            type = "word",
            required = required,
            position = position,
        )
    }
}
