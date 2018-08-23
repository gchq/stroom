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

import org.junit.Assert;
import org.junit.Test;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.node.shared.VolumeState;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.List;

public class TestVolumeSelector extends StroomUnitTest {
    @Test
    public void testMostFree() {
        test(new MostFreeVolumeSelector());
    }

    @Test
    public void testMostFreePercent() {
        test(new MostFreePercentVolumeSelector());
    }

    @Test
    public void testRandom() {
        test(new RandomVolumeSelector());
    }

    @Test
    public void testWeightedFreeRandom() {
        test(new WeightedFreeRandomVolumeSelector());
    }

    @Test
    public void testWeightedFreePercentRandom() {
        test(new WeightedFreePercentRandomVolumeSelector());
    }

    @Test
    public void testRoundRobin() {
        test(new RoundRobinVolumeSelector());
    }

    @Test
    public void testRoundRobinIgnoreLeastFree() {
        test(new RoundRobinIgnoreLeastFreeVolumeSelector());
    }

    @Test
    public void testRoundRobinIgnoreLeastFreePercent() {
        test(new RoundRobinIgnoreLeastFreePercentVolumeSelector());
    }

    private void test(final VolumeSelector volumeSelector) {
        final List<VolumeEntity> volumes = createVolumeList();
        for (int i = 0; i < 100; i++) {
            Assert.assertNotNull(volumeSelector.select(volumes));
        }
    }

    private List<VolumeEntity> createVolumeList() {
        final Rack rack1 = Rack.create("rack1");
        final Rack rack2 = Rack.create("rack2");

        final Node node1 = Node.create(rack1, "node1");
        final Node node2 = Node.create(rack2, "node2");

        final VolumeEntity v1 = VolumeEntity.create(node1, "path1", VolumeType.PUBLIC, VolumeState.create(1000, 10000));
        final VolumeEntity v2 = VolumeEntity.create(node2, "path2", VolumeType.PUBLIC, VolumeState.create(5000, 10000));

        final List<VolumeEntity> volumes = new ArrayList<>();
        volumes.add(v1);
        volumes.add(v2);

        return volumes;
    }
}
