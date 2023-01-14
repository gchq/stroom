package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestMostFreePercentCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new MostFreePercentCapacitySelector();
    }

    @Test
    void test() {
        testMultipleTimes(PATH_1);
    }
}
