package stroom.util.logging;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestLogUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLogUtil.class);

    @Test
    void inSeparatorLine() {
        LOGGER.info(LogUtil.inSeparatorLine("Hello World"));
    }

    @Test
    void inBox() {
        final String box = LogUtil.inBox("Hello World");
        LOGGER.info("\n{}", box);

        Assertions.assertThat(box)
                .isEqualTo("""
                        ┌───────────────┐
                        │  Hello World  │
                        └───────────────┘""");
    }

    @Test
    void inBox_newLine() {
        final String box = LogUtil.inBoxOnNewLine("Hello World");
        LOGGER.info(box);

        Assertions.assertThat(box)
                .isEqualTo("""

                        ┌───────────────┐
                        │  Hello World  │
                        └───────────────┘""");
    }

    @Test
    void inBoxMultiLine() {
        final String box = LogUtil.inBoxOnNewLine("""
                This is
                an example showing
                multiple lines
                of differing length""");

        LOGGER.info(box);
        Assertions.assertThat(box)
                .isEqualTo("""

                        ┌───────────────────────┐
                        │  This is              │
                        │  an example showing   │
                        │  multiple lines       │
                        │  of differing length  │
                        └───────────────────────┘""");
    }
}
