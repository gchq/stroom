package stroom.preferences.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDateTimeFormatter {
    @Test
    void test() {
        final DateTimeFormatter dateTimeFormatter = new DateTimeFormatter(null);
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"))
                .isEqualTo("YYYY-MM-DDTHH:mm:ss.SSS[Z]");
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX"))
                .isEqualTo("YYYY-MM-DDTHH#mm#ss,SSS[Z]");
        assertThat(dateTimeFormatter
                .convertJavaDateTimePattern("E, dd MMM yyyy HH:mm:ss Z"))
                .isEqualTo("ddd, DD MMM YYYY HH:mm:ss Z");
    }
}
