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
        return TestUtil.buildDynamicTestStream(String.class)
                .withTest(this::doParaTest)
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
        return TestUtil.buildDynamicTestStream(String.class)
                .withTest(this::doLineBreakTest)
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
