package stroom.util.io.capacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRoundRobinCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Test
    void testLooping() {
        final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();
        NoddyVolume noddyVolume;

        // Do it all twice, so we can check it cycles back round
        for (int i = 0; i < 2; i++) {
            // Should get each path in turn
            for (NoddyVolume volume : VOLUME_LIST) {
                noddyVolume = testOnce(roundRobinCapacitySelector, ALL_PATHS);
                Assertions.assertThat(noddyVolume.getPath())
                        .isEqualTo(volume.getPath());
            }
        }
    }

    @Test
    void test() {
        final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();
        testMultipleTimes(roundRobinCapacitySelector, ALL_PATHS);
    }
}
