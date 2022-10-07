package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestWeightedFreePercentRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void test() {
        testMultipleTimes(new WeightedFreePercentRandomCapacitySelector(), ALL_PATHS);
    }
}
