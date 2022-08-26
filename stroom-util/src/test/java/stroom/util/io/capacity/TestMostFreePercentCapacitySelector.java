package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestMostFreePercentCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void test() {
        testMultipleTimes(new MostFreePercentCapacitySelector(), PATH_1);
    }
}
