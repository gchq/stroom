package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatBuilderTest {
    @Test
    void doesBuildNumber() {
        final Integer decimalPlaces = 5;
        final Boolean useSeperator = true;

        final Format format = new Format.Builder()
                .number(new NumberFormat.Builder()
                        .decimalPlaces(decimalPlaces)
                        .useSeparator(useSeperator)
                        .build())
                .build();

        assertThat(format.getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(format.getNumberFormat()).isNotNull();
        assertThat(format.getDateTimeFormat()).isNull();
        assertThat(format.getNumberFormat().getDecimalPlaces()).isEqualTo(decimalPlaces);
        assertThat(format.getNumberFormat().getUseSeparator()).isEqualTo(useSeperator);
    }

    @Test
    void doesBuildDateTime() {
        final String pattern = "DAY MONTH YEAR";

        final String timeZoneId = "someId";
        final TimeZone.Use use = TimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final Format format = new Format.Builder()
                .dateTime(new DateTimeFormat.Builder()
                        .pattern(pattern)
                        .timeZone(new TimeZone.Builder()
                                .id(timeZoneId)
                                .use(use)
                                .offsetHours(offsetHours)
                                .offsetMinutes(offsetMinutes)
                                .build())
                        .build())
                .build();

        assertThat(format.getType()).isEqualTo(Format.Type.DATE_TIME);
        assertThat(format.getDateTimeFormat()).isNotNull();
        assertThat(format.getNumberFormat()).isNull();
        assertThat(format.getDateTimeFormat().getPattern()).isEqualTo(pattern);

        assertThat(format.getDateTimeFormat().getTimeZone().getId()).isEqualTo(timeZoneId);
        assertThat(format.getDateTimeFormat().getTimeZone().getUse()).isEqualTo(use);
        assertThat(format.getDateTimeFormat().getTimeZone().getOffsetHours()).isEqualTo(offsetHours);
        assertThat(format.getDateTimeFormat().getTimeZone().getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
