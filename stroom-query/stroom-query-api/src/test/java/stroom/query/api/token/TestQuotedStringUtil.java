package stroom.query.api.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QuotedStringUtil}, in particular that {@code escape} really is
 * the inverse of {@code unescape}.
 */
class TestQuotedStringUtil {

    /**
     * The property that matters: escaping a value and then unescaping the resulting
     * literal must give back exactly the original, whatever it contained.
     *
     * <p>Asserted as a round trip rather than against expected literal text so the
     * two halves cannot drift apart — the reason {@code escape} lives beside
     * {@code unescape} rather than at its call sites.</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "plain",
            "",
            " ",
            "with space",
            "quote\" in middle",
            "\"leading quote",
            "trailing quote\"",
            "\"",
            "\"\"",
            "back\\slash",
            "\\",
            "\\\\",
            "both \\\" together",
            "\\\"",
            "\"\\",
            "/entry[@type=\"gate\"]",
            "tm-world-to-map",
            ".\"tm-world-to-map\"",
            "a\tb\nc",
            "unicode éü",
    })
    void testEscapeThenUnescapeRoundTrips(final String original) {
        final String literal = '"' + QuotedStringUtil.escapeDoubleQuoted(original) + '"';
        final char[] chars = literal.toCharArray();

        final String unescaped =
                QuotedStringUtil.unescape(chars, 0, chars.length - 1, '\\');

        assertThat(unescaped)
                .as("round trip through the literal " + literal)
                .isEqualTo(original);
    }

    /** A value with nothing special in it is passed through untouched. */
    @Test
    void testEscapeLeavesOrdinaryTextAlone() {
        assertThat(QuotedStringUtil.escapeDoubleQuoted("FactStore")).isEqualTo("FactStore");
        assertThat(QuotedStringUtil.escapeDoubleQuoted(".coords")).isEqualTo(".coords");
    }

    /** Backslashes are doubled before quotes are escaped, not after. */
    @Test
    void testEscapeOrdersBackslashBeforeQuote() {
        // A lone backslash must become two, not four.
        assertThat(QuotedStringUtil.escapeDoubleQuoted("\\")).isEqualTo("\\\\");
        // A quote becomes backslash-quote.
        assertThat(QuotedStringUtil.escapeDoubleQuoted("\"")).isEqualTo("\\\"");
        // Backslash followed by quote: each escaped once.
        assertThat(QuotedStringUtil.escapeDoubleQuoted("\\\"")).isEqualTo("\\\\\\\"");
    }

    /** Null yields an empty string, so callers can append unconditionally. */
    @Test
    void testEscapeNullYieldsEmpty() {
        assertThat(QuotedStringUtil.escapeDoubleQuoted(null)).isEmpty();
    }

    /** A non-default quote character is honoured, and the other quote is left alone. */
    @Test
    void testEscapeHonoursTheGivenQuoteChar() {
        assertThat(QuotedStringUtil.escape("it's", '\'', '\\')).isEqualTo("it\\'s");
        assertThat(QuotedStringUtil.escape("say \"hi\"", '\'', '\\')).isEqualTo("say \"hi\"");
    }
}
