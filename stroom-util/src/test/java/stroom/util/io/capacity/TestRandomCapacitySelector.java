package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new RandomCapacitySelector();
    }

    @Test
    void test() {
        testMultipleTimes(ALL_PATHS);
    }
}
