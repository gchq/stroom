package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestWeightedFreeRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void test() {
        testMultipleTimes(new WeightedFreeRandomCapacitySelector(), ALL_PATHS);
    }
}
