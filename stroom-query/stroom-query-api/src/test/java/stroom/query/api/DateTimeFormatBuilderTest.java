package stroom.query.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeFormatBuilderTest {
    @Test
    void doesBuild() {
        final String pattern = "DAY MONTH YEAR";

        final String timeZoneId = "someId";
        final UserTimeZone.Use use = UserTimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final DateTimeFormatSettings dateTimeFormat = DateTimeFormatSettings
                .builder()
                .pattern(pattern)
                .timeZone(UserTimeZone
                        .builder()
                        .id(timeZoneId)
                        .use(use)
                        .offsetHours(offsetHours)
                        .offsetMinutes(offsetMinutes)
                        .build())
                .build();

        assertThat(dateTimeFormat.getPattern()).isEqualTo(pattern);

        assertThat(dateTimeFormat.getTimeZone().getId()).isEqualTo(timeZoneId);
        assertThat(dateTimeFormat.getTimeZone().getUse()).isEqualTo(use);
        assertThat(dateTimeFormat.getTimeZone().getOffsetHours()).isEqualTo(offsetHours);
        assertThat(dateTimeFormat.getTimeZone().getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
