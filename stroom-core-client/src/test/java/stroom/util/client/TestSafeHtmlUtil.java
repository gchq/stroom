package stroom.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestSafeHtmlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSafeHtmlUtil.class);

    @Test
    void toParagraphs_1() {
        doParaTest("hello\nworld", "<p>hello</p><p>world</p>");

    }

    @Test
    void toParagraphs_2() {
        doParaTest("\nhello\nworld\n", "<p>hello</p><p>world</p>");
    }

    @Test
    void toParagraphs_3() {
        doParaTest("hello\n", "<p>hello</p>");
    }

    @Test
    void toParagraphs_4() {
        doParaTest("\nhello", "<p>hello</p>");
    }

    @Test
    void toParagraphs_5() {
        doParaTest("", "");
    }

    @Test
    void toParagraphs_6() {
        doParaTest(null, null);
    }

    @Test
    void toParagraphs_7() {
        doParaTest(
                "hello\nworld\ngoodbye",
                "<p>hello</p><p>world</p><p>goodbye</p>");
    }

    private void doParaTest(final String input, final String expectedOutput) {
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
}
