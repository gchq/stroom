package stroom.util.shared;

import stroom.docref.DocRef;
import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.stream.Stream;

class TestStringUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringUtil.class);

    @Test
    void testToString() {
        DocRef docRef = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .type("Index")
                .name("my name")
                .build();

        final String str = StringUtil.toString(docRef);
        Assertions.assertThat(str)
                .isEqualTo(docRef.toString());
    }

    @Test
    void testToString_null() {
        DocRef docRef = null;

        final String str = StringUtil.toString(docRef);
        Assertions.assertThat(str)
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

        Assertions.assertThat(isBlank)
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
        Assertions.assertThat(output)
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
                    var quotedInput = StringUtil.addWhitespaceQuoting(testCase.getInput());
                    LOGGER.debug("quotedInput: '{}'", quotedInput);
                    return StringUtil.removeWhitespaceQuoting(quotedInput);
                })
                .withAssertions(testOutcome -> {
                    // Expected output ignored
                    Assertions.assertThat(testOutcome.getActualOutput())
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
                .addCase("  a\"bc  ", null)
                .addCase("a\"bc", null)
                .addCase(" my name is \"Bob\". ", null)
                .build();
    }
}
