package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberFormatSettingsBuilderTest {
    @Test
    void doesBuild() {
        final Integer decimalPlaces = 5;
        final Boolean useSeperator = true;

        final NumberFormatSettings numberFormat = NumberFormatSettings
                .builder()
                .decimalPlaces(decimalPlaces)
                .useSeparator(useSeperator)
                .build();

        assertThat(numberFormat.getDecimalPlaces()).isEqualTo(decimalPlaces);
        assertThat(numberFormat.getUseSeparator()).isEqualTo(useSeperator);
    }
}
