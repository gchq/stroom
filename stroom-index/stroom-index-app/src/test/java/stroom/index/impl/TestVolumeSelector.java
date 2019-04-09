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

package stroom.index.impl;


import org.junit.jupiter.api.Test;
import stroom.index.impl.selection.MostFreePercentVolumeSelector;
import stroom.index.impl.selection.MostFreeVolumeSelector;
import stroom.index.impl.selection.RandomVolumeSelector;
import stroom.index.impl.selection.RoundRobinIgnoreLeastFreePercentVolumeSelector;
import stroom.index.impl.selection.RoundRobinIgnoreLeastFreeVolumeSelector;
import stroom.index.impl.selection.RoundRobinVolumeSelector;
import stroom.index.impl.selection.VolumeSelector;
import stroom.index.impl.selection.WeightedFreePercentRandomVolumeSelector;
import stroom.index.impl.selection.WeightedFreeRandomVolumeSelector;
import stroom.index.shared.IndexVolume;
import stroom.node.shared.Node;
import stroom.test.common.util.test.StroomUnitTest;

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
        final List<IndexVolume> volumes = createVolumeList();
        for (int i = 0; i < 100; i++) {
            assertThat(volumeSelector.select(volumes)).isNotNull();
        }
    }

    private List<IndexVolume> createVolumeList() {
        final Node node1 = Node.create("node1");
        final Node node2 = Node.create("node2");

        final IndexVolume v1 = new IndexVolume.Builder()
                .nodeName(node1.getName()).path("path1").bytesUsed(1000L).bytesTotal(10000L).build();
        final IndexVolume v2 = new IndexVolume.Builder()
                .nodeName(node2.getName()).path("path2").bytesUsed(5000L).bytesTotal(10000L).build();

        return List.of(v1, v2);
    }
}
