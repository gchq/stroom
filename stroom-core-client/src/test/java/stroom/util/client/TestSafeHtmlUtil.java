package stroom.util.client;

import stroom.test.common.TestCase;
import stroom.test.common.TestUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

class TestSafeHtmlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSafeHtmlUtil.class);

    @TestFactory
    Stream<DynamicTest> testToParagraphs() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        SafeHtmlUtil.toParagraphs(testCase.getInput()).asString())
                .withAssertions(testOutcome -> {
                    LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                            (testOutcome.getInput() != null
                                    ? testOutcome.getInput().replace("\n", "↵")
                                    : null),
                            testOutcome.getActualOutput(),
                            testOutcome.getExpectedOutput());

                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());

                    if (testOutcome.getExpectedOutput() != null) {
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .doesNotContain("\n");
                    }
                })
                .addCase("hello\nworld", "<p>hello</p><p>world</p>")
                .addCase("\nhello\nworld\n", "<p>hello</p><p>world</p>")
                .addCase("hello\n", "<p>hello</p>")
                .addCase("\nhello", "<p>hello</p>")
                .addCase("", "")
                .addCase(null, "")
                .addCase("hello\nworld\ngoodbye", "<p>hello</p><p>world</p><p>goodbye</p>")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWithLineBreaks() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        SafeHtmlUtil.withLineBreaks(testCase.getInput()).asString())
                .withAssertions(testOutcome -> {
                    LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                            (testOutcome.getInput() != null
                                    ? testOutcome.getInput().replace("\n", "↵")
                                    : null),
                            testOutcome.getActualOutput(),
                            testOutcome.getExpectedOutput());

                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());

                    if (testOutcome.getExpectedOutput() != null) {
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .doesNotContain("\n");
                    }
                })
                .addCase("hello\nworld", "hello<br>world")
                .addCase("\nhello\nworld\n", "hello<br>world")
                .addCase("hello\n", "hello")
                .addCase("\nhello", "hello")
                .addCase("", "")
                .addCase(null, "")
                .addCase("hello\nworld\ngoodbye", "hello<br>world<br>goodbye")
                .build();
    }

    private void doParaTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.toParagraphs(input);

    }

    private void doLineBreakTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.withLineBreaks(input);

    }
}
