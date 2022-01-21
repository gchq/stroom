package stroom.meta.api;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMapUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAttributeMapUtil.class);

    @Test
    void testDefaultLocale() {
        LOGGER.info("Default locale = " + Locale.getDefault(Locale.Category.FORMAT));
    }

    @Test
    void testDataFormatter1() {
        final String inputDateStr = "Sep  9 16:16:45 2018 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, AttributeMapUtil.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2018, 9, 9, 16, 16, 45);
        assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }

    @Test
    void testDataFormatter2() {
        final String inputDateStr = "Sep 10 06:39:20 2292 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, AttributeMapUtil.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2292, 9, 10, 6, 39, 20);
        assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }
}
