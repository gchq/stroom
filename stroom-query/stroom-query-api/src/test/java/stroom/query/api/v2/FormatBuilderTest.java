package stroom.query.api.v2;

import stroom.query.api.v2.Format.Type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatBuilderTest {
    @Test
    void doesBuildNumber() {
        final Integer decimalPlaces = 5;
        final Boolean useSeperator = true;

        final Format format = new Format.Builder()
                .type(Type.NUMBER)
                .settings(new NumberFormatSettings.Builder()
                        .decimalPlaces(decimalPlaces)
                        .useSeparator(useSeperator)
                        .build())
                .build();

        assertThat(format.getType()).isEqualTo(Format.Type.NUMBER);

        final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) format.getSettings();
        assertThat(numberFormatSettings).isNotNull();
        assertThat(numberFormatSettings.getDecimalPlaces()).isEqualTo(decimalPlaces);
        assertThat(numberFormatSettings.getUseSeparator()).isEqualTo(useSeperator);
    }

    @Test
    void doesBuildDateTime() {
        final String pattern = "DAY MONTH YEAR";

        final String timeZoneId = "someId";
        final TimeZone.Use use = TimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final Format format = new Format.Builder()
                .type(Type.DATE_TIME)
                .settings(new DateTimeFormatSettings.Builder()
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

        final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) format.getSettings();
        assertThat(dateTimeFormatSettings).isNotNull();
        assertThat(dateTimeFormatSettings.getPattern()).isEqualTo(pattern);

        final TimeZone timeZone = dateTimeFormatSettings.getTimeZone();
        assertThat(timeZone.getId()).isEqualTo(timeZoneId);
        assertThat(timeZone.getUse()).isEqualTo(use);
        assertThat(timeZone.getOffsetHours()).isEqualTo(offsetHours);
        assertThat(timeZone.getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
