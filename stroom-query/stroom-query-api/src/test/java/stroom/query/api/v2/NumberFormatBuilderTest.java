package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberFormatBuilderTest {
    @Test
    void doesBuild() {
        final Integer decimalPlaces = 5;
        final Boolean useSeperator = true;

        final NumberFormat numberFormat = new NumberFormat.Builder()
                .decimalPlaces(decimalPlaces)
                .useSeparator(useSeperator)
                .build();

        assertThat(numberFormat.getDecimalPlaces()).isEqualTo(decimalPlaces);
        assertThat(numberFormat.getUseSeparator()).isEqualTo(useSeperator);
    }
}
