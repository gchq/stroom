package stroom.util.io.capacity;

import org.junit.jupiter.api.Test;

class TestRandomCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void test() {
        testMultipleTimes(new RandomCapacitySelector(), ALL_PATHS);
    }
}
