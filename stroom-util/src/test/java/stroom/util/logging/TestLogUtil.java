package stroom.util.logging;

import stroom.util.concurrent.DurationAdder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(box)
                .isEqualTo("""
                        ┌───────────────┐
                        │  Hello World  │
                        └───────────────┘""");
    }

    @Test
    void inBox_newLine() {
        final String box = LogUtil.inBoxOnNewLine("Hello World");
        LOGGER.info(box);

        assertThat(box)
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
        assertThat(box)
                .isEqualTo("""

                        ┌───────────────────────┐
                        │  This is              │
                        │  an example showing   │
                        │  multiple lines       │
                        │  of differing length  │
                        └───────────────────────┘""");
    }

    @Test
    void withPercentage_double() {
        assertThat(LogUtil.withPercentage(10.5, 100.0))
                .isEqualTo("10.5 (10%)");
    }

    @Test
    void withPercentage_int() {
        assertThat(LogUtil.withPercentage((int) 10, (int) 100))
                .isEqualTo("10 (10%)");

    }

    @Test
    void withPercentage_long() {
        assertThat(LogUtil.withPercentage(10L, 100L))
                .isEqualTo("10 (10%)");
    }

    @Test
    void withPercentage_duration() {
        assertThat(LogUtil.withPercentage(Duration.ofMinutes(12), Duration.ofHours(1)))
                .isEqualTo("PT12M (20%)");
    }

    @Test
    void withPercentage_durationAdder() {
        DurationAdder durationAdderVal = new DurationAdder(Duration.ofSeconds(30));
        DurationAdder durationAdderTotal = new DurationAdder(Duration.ofMinutes(1));

        assertThat(LogUtil.withPercentage(durationAdderVal, durationAdderTotal))
                .isEqualTo("PT30S (50%)");
    }

    @Test
    void withPercentage_null() {

        assertThat(LogUtil.withPercentage(null, (int) 100))
                .isNull();
    }
}
