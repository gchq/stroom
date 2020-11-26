package stroom.util.shared;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestLocation {

    @Test
    void testCompare() {
        doIsAfterTest("2:2", "2:2", false);
        doIsAfterTest("2:2", "2:3", true);
        doIsAfterTest("2:2", "3:2", true);
        doIsAfterTest("2:2", "3", true);
        doIsAfterTest("2", "3", true);
        doIsAfterTest("2", "2", false);
    }

    private void doIsAfterTest(final String fromStr, final String toStr, final boolean expectedResult) {
        final Location from = DefaultLocation.parse(fromStr).get();
        final Location to = DefaultLocation.parse(toStr).get();

        final boolean result = to.isAfter(from);

        Assertions.assertThat(result)
                .isEqualTo(expectedResult);

        if (!from.equals(to)) {
            final boolean result2 = to.isBefore(from);
            Assertions.assertThat(result2)
                    .isEqualTo(!expectedResult);
        }
    }
}