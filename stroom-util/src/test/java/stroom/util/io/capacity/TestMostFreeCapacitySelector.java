package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestMostFreeCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void testMultiple() {
        testMultipleTimes(new MostFreeCapacitySelector(), PATH_1, PATH_2);
    }
}
