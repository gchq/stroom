package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestWeightedFreeRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new WeightedFreeRandomCapacitySelector();
    }

    @Test
    void test() {
        testMultipleTimes(ALL_PATHS);
    }
}
