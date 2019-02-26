/*
 * Copyright 2016 Crown Copyright
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

package stroom.volume;


import org.junit.jupiter.api.Test;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.node.shared.VolumeState;
import stroom.test.common.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVolumeSelector extends StroomUnitTest {
    @Test
    void testMostFree() {
        test(new MostFreeVolumeSelector());
    }

    @Test
    void testMostFreePercent() {
        test(new MostFreePercentVolumeSelector());
    }

    @Test
    void testRandom() {
        test(new RandomVolumeSelector());
    }

    @Test
    void testWeightedFreeRandom() {
        test(new WeightedFreeRandomVolumeSelector());
    }

    @Test
    void testWeightedFreePercentRandom() {
        test(new WeightedFreePercentRandomVolumeSelector());
    }

    @Test
    void testRoundRobin() {
        test(new RoundRobinVolumeSelector());
    }

    @Test
    void testRoundRobinIgnoreLeastFree() {
        test(new RoundRobinIgnoreLeastFreeVolumeSelector());
    }

    @Test
    void testRoundRobinIgnoreLeastFreePercent() {
        test(new RoundRobinIgnoreLeastFreePercentVolumeSelector());
    }

    private void test(final VolumeSelector volumeSelector) {
        final List<VolumeEntity> volumes = createVolumeList();
        for (int i = 0; i < 100; i++) {
            assertThat(volumeSelector.select(volumes)).isNotNull();
        }
    }

    private List<VolumeEntity> createVolumeList() {
        final Node node1 = Node.create("node1");
        final Node node2 = Node.create("node2");

        final VolumeEntity v1 = VolumeEntity.create(node1, "path1", VolumeType.PUBLIC, VolumeState.create(1000, 10000));
        final VolumeEntity v2 = VolumeEntity.create(node2, "path2", VolumeType.PUBLIC, VolumeState.create(5000, 10000));

        final List<VolumeEntity> volumes = new ArrayList<>();
        volumes.add(v1);
        volumes.add(v2);

        return volumes;
    }
}
