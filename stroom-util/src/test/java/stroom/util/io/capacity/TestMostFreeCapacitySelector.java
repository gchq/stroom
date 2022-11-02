package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestMostFreeCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new MostFreeCapacitySelector();
    }

    @Test
    void testMultiple() {
        testMultipleTimes(PATH_1, PATH_2);
    }
}
