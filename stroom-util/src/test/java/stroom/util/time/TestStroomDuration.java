package stroom.util.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;

class TestStroomDuration {

    @Test
    void testParseTests() {
        System.out.println(Duration.ZERO.toString());
        doParseTest("30d", "P30D", Duration.ofDays(30));
        doParseTest("12h", "PT12H", Duration.ofHours(12));
        doParseTest("5m", "PT5M", Duration.ofMinutes(5));
        doParseTest("10s", "PT10S", Duration.ofSeconds(10));
        doParseTest("100ms", "PT0.1S", Duration.ofMillis(100));
    }

    @Test
    void getValueAsStr() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);
        Assertions.assertThat(stroomDuration.getValueAsStr()).isEqualTo(input);
    }

    @Test
    void setValueAsStr() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);

        final String input2 = "PT12H";
        stroomDuration.setValueAsStr(input2);

        Assertions.assertThat(stroomDuration.getValueAsStr())
            .isEqualTo(input2);
    }

    @Test
    void getDuration() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);

        Assertions.assertThat(stroomDuration.getDuration())
            .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void setDuration() {
        final String input = "P30D";
        final StroomDuration stroomDuration = StroomDuration.parse(input);

        stroomDuration.setDuration(Duration.ofHours(12));

        Assertions.assertThat(stroomDuration.getDuration())
            .isEqualTo(Duration.parse("PT12H"));
    }

    @Test
    void testSerde() throws IOException {
            final StroomDuration stroomDuration = StroomDuration.parse("30d");

            final ObjectMapper objectMapper = new ObjectMapper();

            final StringWriter stringWriter = new StringWriter();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(stringWriter, stroomDuration);

            final String json = stringWriter.toString();

            System.out.println(json);

            final StroomDuration stroomDuration2 = objectMapper.readValue(json, StroomDuration.class);

            Assertions.assertThat(stroomDuration).isEqualTo(stroomDuration2);
    }

    void doParseTest(final String modelStringUtilInput, final String isoInput, final Duration expectedDuration) {
        StroomDuration stroomDuration = StroomDuration.parse(modelStringUtilInput);
        Assertions.assertThat(stroomDuration.getDuration()).isEqualTo(expectedDuration);
        Assertions.assertThat(stroomDuration.getValueAsStr()).isEqualTo(modelStringUtilInput);

        StroomDuration stroomDuration2 = StroomDuration.parse(isoInput);
        Assertions.assertThat(stroomDuration2.getDuration()).isEqualTo(expectedDuration);
        Assertions.assertThat(stroomDuration2.getValueAsStr()).isEqualTo(isoInput);

        Assertions.assertThat(stroomDuration).isEqualTo(stroomDuration2);
    }
}