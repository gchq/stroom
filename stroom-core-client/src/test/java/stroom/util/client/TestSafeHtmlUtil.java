package stroom.util.client;

import stroom.test.common.TestCase;
import stroom.test.common.TestUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

class TestSafeHtmlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSafeHtmlUtil.class);

    @TestFactory
    Stream<DynamicTest> testToParagraphs() {
        return TestUtil.createDynamicTestStream(
                List.of(
                        TestCase.of("hello\nworld", "<p>hello</p><p>world</p>"),
                        TestCase.of("\nhello\nworld\n", "<p>hello</p><p>world</p>"),
                        TestCase.of("hello\n", "<p>hello</p>"),
                        TestCase.of("\nhello", "<p>hello</p>"),
                        TestCase.of("", ""),
                        TestCase.of(null, ""),
                        TestCase.of(
                                "hello\nworld\ngoodbye",
                                "<p>hello</p><p>world</p><p>goodbye</p>")),
                this::doParaTest);
    }

    @TestFactory
    Stream<DynamicTest> testWithLineBreaks() {
        return TestUtil.createDynamicTestStream(
                List.of(
                        TestCase.of("hello\nworld", "hello<br>world"),
                        TestCase.of("\nhello\nworld\n", "hello<br>world"),
                        TestCase.of("hello\n", "hello"),
                        TestCase.of("\nhello", "hello"),
                        TestCase.of("", ""),
                        TestCase.of(null, ""),
                        TestCase.of("hello\nworld\ngoodbye", "hello<br>world<br>goodbye")),
                this::doLineBreakTest);
    }

    private void doParaTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.toParagraphs(input);

        LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                (input != null
                        ? input.replace("\n", "↵")
                        : null),
                output,
                expectedOutput);

        if (expectedOutput == null) {
            Assertions.assertThat(output)
                    .isNull();
        } else {
            Assertions.assertThat(output.asString())
                    .isEqualTo(expectedOutput);

            Assertions.assertThat(output.asString())
                    .doesNotContain("\n");
        }
    }

    private void doLineBreakTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.withLineBreaks(input);

        LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                (input != null
                        ? input.replace("\n", "↵")
                        : null),
                output,
                expectedOutput);

        if (expectedOutput == null) {
            Assertions.assertThat(output)
                    .isNull();
        } else {
            Assertions.assertThat(output.asString())
                    .isEqualTo(expectedOutput);

            Assertions.assertThat(output.asString())
                    .doesNotContain("\n");
        }
    }
}
