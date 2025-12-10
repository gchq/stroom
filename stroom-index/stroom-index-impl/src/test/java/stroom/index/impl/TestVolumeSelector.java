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

package stroom.index.impl;


import stroom.index.shared.IndexVolume;
import stroom.node.shared.Node;
import stroom.util.io.capacity.HasCapacitySelector;
import stroom.util.io.capacity.MostFreeCapacitySelector;
import stroom.util.io.capacity.MostFreePercentCapacitySelector;
import stroom.util.io.capacity.RandomCapacitySelector;
import stroom.util.io.capacity.RoundRobinCapacitySelector;
import stroom.util.io.capacity.RoundRobinIgnoreLeastFreeCapacitySelector;
import stroom.util.io.capacity.RoundRobinIgnoreLeastFreePercentCapacitySelector;
import stroom.util.io.capacity.WeightedFreePercentRandomCapacitySelector;
import stroom.util.io.capacity.WeightedFreeRandomCapacitySelector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVolumeSelector {

    private static final String PATH_1 = "path1";
    private static final String PATH_2 = "path2";
    private static final String PATH_3 = "path3";
    private static final String PATH_4 = "path4";
    private static final String PATH_5 = "path5";

    private List<IndexVolume> createVolumeList() {
        final Node node1 = Node.create("node1");
        final Node node2 = Node.create("node2");

        // 4k free, 80% free
        final IndexVolume v1 = IndexVolume.builder()
                .nodeName(node1.getName())
                .path(PATH_1)
                .bytesUsed(1_000L)
                .bytesFree(4_000L)
                .bytesTotal(5_000L)
                .build();
        // 4k free, 40% free
        final IndexVolume v2 = IndexVolume.builder()
                .nodeName(node2.getName())
                .path(PATH_2)
                .bytesUsed(6_000L)
                .bytesFree(4_000L)
                .bytesTotal(10_000L)
                .build();
        // 2k free, 20% free
        final IndexVolume v3 = IndexVolume.builder()
                .nodeName(node2.getName())
                .path(PATH_3)
                .bytesUsed(8_000L)
                .bytesFree(2_000L)
                .bytesTotal(10_000L)
                .build();
        // 0k free, 0% free
        final IndexVolume v4 = IndexVolume.builder()
                .nodeName(node2.getName())
                .path(PATH_4)
                .bytesUsed(10_000L)
                .bytesFree(0L)
                .bytesTotal(10_000L)
                .build();
        // 1k free, 10% free
        final IndexVolume v5 = IndexVolume.builder()
                .nodeName(node2.getName())
                .path(PATH_5)
                .bytesUsed(9_000L)
                .bytesFree(1_000L)
                .bytesTotal(100_000L)
                .bytesLimit(10_000L)
                .build();

        return List.of(v1, v2, v3, v4, v5);
    }

    @Test
    void testMostFree() {
        test(new MostFreeCapacitySelector(), PATH_1, PATH_2);
    }

    @Test
    void testMostFreePercent() {
        test(new MostFreePercentCapacitySelector(), PATH_1);
    }

    @Test
    void testRandom() {
        test(new RandomCapacitySelector());
    }

    @Test
    void testWeightedFreeRandom() {
        test(new WeightedFreeRandomCapacitySelector());
    }

    @Test
    void testWeightedFreePercentRandom() {
        test(new WeightedFreePercentRandomCapacitySelector());
    }

    @Test
    void testRoundRobin() {
        test(new RoundRobinCapacitySelector());
    }

    @Test
    void testRoundRobinIgnoreLeastFree() {
        test(new RoundRobinIgnoreLeastFreeCapacitySelector(), PATH_1, PATH_2, PATH_3, PATH_5);
    }

    @Test
    void testRoundRobinIgnoreLeastFreePercent() {
        test(new RoundRobinIgnoreLeastFreePercentCapacitySelector(), PATH_1, PATH_2, PATH_3, PATH_5);
    }

    private void test(final HasCapacitySelector volumeSelector, final String... validExpectedVolPaths) {
        final List<IndexVolume> volumes = createVolumeList();
        for (int i = 0; i < 100; i++) {
            final IndexVolume selectedVolume = volumeSelector.select(volumes);
            assertThat(selectedVolume).isNotNull();
            if (validExpectedVolPaths != null && validExpectedVolPaths.length > 0) {
                assertThat(selectedVolume.getPath())
                        .isIn((Object[]) validExpectedVolPaths);
            }
        }
    }
}
