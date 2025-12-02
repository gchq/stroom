package stroom.util.shared;

import stroom.docref.DocRef;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.StringUtil.plural;
import static stroom.util.shared.StringUtil.pluralSuffix;

class TestStringUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringUtil.class);

    @Test
    void testToString() {
        final DocRef docRef = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .type("Index")
                .name("my name")
                .build();

        final String str = StringUtil.toString(docRef);
        assertThat(str)
                .isEqualTo(docRef.toString());
    }

    @Test
    void testToString_null() {
        final DocRef docRef = null;

        final String str = StringUtil.toString(docRef);
        assertThat(str)
                .isEqualTo("");
    }


    @ParameterizedTest
    @CsvSource(nullValues = {"<<NULL>>"}, value = {
            "'abc',false",
            "' abc ',false",
            "'',true",
            "<<NULL>>,true",
            "' ',true",
            "'  ',true",
            "'\n',true",
            "'\t',true",
    })
    void testIsBlank1(final String input, final boolean expectedOutput) {

        final boolean isBlank = StringUtil.isBlank(input);

        LOGGER.info("input: [{}], expectedOutput: {}, isBlank: {}",
                input, expectedOutput, isBlank);

        assertThat(isBlank)
                .isEqualTo(expectedOutput);
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"<NULL>"}, value = {
            "<NULL>,<NULL>",
            "'abc','abc'",
            "' abc',' abc'",
            "' ',<NULL>",
            "'  ',<NULL>",
            "'\n',<NULL>",
            "'\t',<NULL>",
    })
    void testBlankAsNull(final String input, final String expectedOutput) {
        final String output = StringUtil.blankAsNull(input);
        assertThat(output)
                .isEqualTo(expectedOutput);
    }

    @TestFactory
    Stream<DynamicTest> testRemoveWhitespaceQuoting() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        StringUtil.removeWhitespaceQuoting(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase(" ", "")
                .addCase("      ", "")
                .addCase("   \\\"   ", "\"")
                .addCase("\t", "")
                .addCase("abc", "abc")
                .addCase(" abc", "abc")
                .addCase("abc ", "abc")
                .addCase(" abc ", "abc")
                .addCase("    abc    ", "abc")
                .addCase("\tabc\t", "abc")
                .addCase("\"abc\"", "abc")
                .addCase("\\\"abc\\\"", "\"abc\"")
                .addCase(" \"abc\" ", "abc")
                .addCase(" \\\"abc\\\" ", "\"abc\"")
                .addCase(" \" abc \" ", " abc ")
                .addCase(" \\\" abc \\\" ", "\" abc \"")
                .addCase(" \" abc  ", "\" abc")
                .addCase(" \" my name is \\\"Bob\\\". \" ", " my name is \"Bob\". ")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAddWhitespaceQuoting() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        StringUtil.addWhitespaceQuoting(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase(" ", "\" \"")
                .addCase("      ", "\"      \"")
                .addCase("abc", "abc")
                .addCase(" abc", "\" abc\"")
                .addCase("abc ", "\"abc \"")
                .addCase(" abc ", "\" abc \"")
                .addCase("    abc    ", "\"    abc    \"")
                .addCase("\tabc\t", "\"\tabc\t\"")
                .addCase("\"abc\"", "\\\"abc\\\"")
                .addCase(" \"abc\" ", "\" \\\"abc\\\" \"")
                .addCase(" \" abc \" ", "\" \\\" abc \\\" \"")
                .addCase(" \" abc  ", "\" \\\" abc  \"")
                .addCase("  a\"bc  ", "\"  a\\\"bc  \"")
                .addCase("a\"bc", "a\\\"bc")
                .addCase(" my name is \"Bob\". ", "\" my name is \\\"Bob\\\". \"")
                .build();
    }

    /**
     * Make sure the two functions are reversible
     */
    @TestFactory
    Stream<DynamicTest> testAddAndRemoveWhitespaceQuoting() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String quotedInput = StringUtil.addWhitespaceQuoting(testCase.getInput());
                    LOGGER.debug("quotedInput: '{}'", quotedInput);
                    return StringUtil.removeWhitespaceQuoting(quotedInput);
                })
                .withAssertions(testOutcome -> {
                    // Expected output ignored
                    assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getInput());
                })
                .addCase(null, null)
                .addCase("", null)
                .addCase(" ", null)
                .addCase("      ", null)
                .addCase("   \"   ", null)
                .addCase("abc", null)
                .addCase(" abc", null)
                .addCase("abc ", null)
                .addCase(" abc ", null)
                .addCase("    abc    ", null)
                .addCase("\tabc\t", null)
                .addCase("\"abc\"", null)
                .addCase(" \"abc\" ", null)
                .addCase(" \" abc \" ", null)
                .addCase(" \" abc  ", null)
                .addCase("  a\"bc  ", null)
                .addCase("a\"bc", null)
                .addCase(" my name is \"Bob\". ", null)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSingleQuote() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> StringUtil.singleQuote(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "''")
                .addCase("", "''")
                .addCase("x", "'x'")
                .addCase("o'neil", "'o'neil'")
                .build();
    }

    @Test
    void testPlural_1() {
        assertThat(plural("has", "have", 1))
                .isEqualTo("has");
        assertThat(plural("has", "have", 2))
                .isEqualTo("have");
    }

    @Test
    void testPlural_2() {
        assertThat(plural("has", "have", 1L))
                .isEqualTo("has");
        assertThat(plural("has", "have", 2L))
                .isEqualTo("have");
    }

    @Test
    void testPlural_3() {
        assertThat(plural("has", "have", List.of(1)))
                .isEqualTo("has");
        assertThat(plural("has", "have", List.of(1, 2)))
                .isEqualTo("have");
    }

    @Test
    void testPluralSuffix_1() {
        assertThat(StringUtil.plural("document", 1))
                .isEqualTo("document");
        assertThat(StringUtil.plural("document", 2))
                .isEqualTo("documents");
    }

    @Test
    void testPluralSuffix_2() {
        assertThat(StringUtil.plural("document", 1L))
                .isEqualTo("document");
        assertThat(StringUtil.plural("document", 2L))
                .isEqualTo("documents");
    }

    @Test
    void testPluralSuffix_3() {
        assertThat(StringUtil.plural("document", List.of(1)))
                .isEqualTo("document");
        assertThat(StringUtil.plural("document", List.of(1, 2)))
                .isEqualTo("documents");
    }

    @Test
    void testPluralSuffix_1b() {
        assertThat(pluralSuffix(1))
                .isEqualTo("");
        assertThat(pluralSuffix(2))
                .isEqualTo("s");
    }

    @Test
    void testPluralSuffix_2b() {
        assertThat(pluralSuffix(1L))
                .isEqualTo("");
        assertThat(pluralSuffix(2L))
                .isEqualTo("s");
    }

    @Test
    void testPluralSuffix_3b() {
        assertThat(pluralSuffix(List.of(1)))
                .isEqualTo("");
        assertThat(pluralSuffix(List.of(1, 2)))
                .isEqualTo("s");
    }

    @Test
    void testFormatDouble() {
        assertThat(StringUtil.formatDouble(0.00034))
                .isEqualTo("0");
        assertThat(StringUtil.formatDouble(123.00034))
                .isEqualTo("123");
        assertThat(StringUtil.formatDouble(123.12034))
                .isEqualTo("123.12");
        assertThat(StringUtil.formatDouble(123.10034))
                .isEqualTo("123.1");
    }
}
