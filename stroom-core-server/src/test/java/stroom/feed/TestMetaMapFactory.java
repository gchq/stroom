package stroom.feed;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.time.LocalDateTime;


public class TestMetaMapFactory {

    @Test
    public void testDataFormatter1() {
        final String inputDateStr = "Sep  9 16:16:45 2018 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, MetaMapFactory.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2018, 9, 9, 16, 16, 45);
        Assertions.assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }

    @Test
    public void testDataFormatter2() {
        final String inputDateStr = "Sep 10 06:39:20 2292 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, MetaMapFactory.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2292, 9, 10, 6, 39, 20);
        Assertions.assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }
}