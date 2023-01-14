package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestWeightedFreePercentRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new WeightedFreePercentRandomCapacitySelector();
    }

    @Test
    void test() {
        testMultipleTimes(ALL_PATHS);
    }
}
