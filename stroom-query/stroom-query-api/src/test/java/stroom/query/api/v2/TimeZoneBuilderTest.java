package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneBuilderTest {
    @Test
    void doesBuild() {
        final String id = "someId";
        final TimeZone.Use use = TimeZone.Use.LOCAL;
        final Integer offsetHours = 3;
        final Integer offsetMinutes = 5;

        final TimeZone timeZone = new TimeZone.Builder()
                .id(id)
                .use(use)
                .offsetHours(offsetHours)
                .offsetMinutes(offsetMinutes)
                .build();

        assertThat(timeZone.getId()).isEqualTo(id);
        assertThat(timeZone.getUse()).isEqualTo(use);
        assertThat(timeZone.getOffsetHours()).isEqualTo(offsetHours);
        assertThat(timeZone.getOffsetMinutes()).isEqualTo(offsetMinutes);
    }
}
