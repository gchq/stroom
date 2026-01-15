/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.io.capacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRoundRobinCapacitySelector extends AbstractHasCapacitySelectorTest {

    @Override
    HasCapacitySelector getSelector() {
        return new RoundRobinCapacitySelector();
    }

    @Test
    void testLooping() {
        final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();
        NoddyVolume noddyVolume;

        // Do it all twice, so we can check it cycles back round
        for (int i = 0; i < 2; i++) {
            // Should get each path in turn
            for (final NoddyVolume volume : VOLUME_LIST) {
                noddyVolume = testOnce(roundRobinCapacitySelector, ALL_PATHS);
                Assertions.assertThat(noddyVolume.getPath())
                        .isEqualTo(volume.getPath());
            }
        }
    }

    @Test
    void test() {
        final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();
        testMultipleTimes(ALL_PATHS);
    }
}
