package stroom.util.io.capacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

class TestRoundRobinIgnoreLeastFreePercentCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new RoundRobinIgnoreLeastFreePercentCapacitySelector();
    }

    @Test
    void testMultiple() {
        testMultipleTimes(PATH_1, PATH_2, PATH_3, PATH_5);
    }

    @Test
    void testLooping() {
        final HasCapacitySelector selector = getSelector();
        NoddyVolume noddyVolume;

        final List<NoddyVolume> validVolumes = VOLUME_LIST.stream()
                .filter(vol -> !vol.getPath().equals(PATH_4))
                .collect(Collectors.toList());

        // Do it all twice, so we can check it cycles back round
        for (int i = 0; i < 2; i++) {
            // Should get each path in turn
            for (final NoddyVolume volume : validVolumes) {
                noddyVolume = testOnce(selector, PATH_1, PATH_2, PATH_3, PATH_5);
                Assertions.assertThat(noddyVolume.getPath())
                        .isEqualTo(volume.getPath());
            }
        }
    }
}
