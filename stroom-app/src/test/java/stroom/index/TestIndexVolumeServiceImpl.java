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

package stroom.index;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeGroup;
import stroom.node.api.NodeInfo;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.ByteSizeUnit;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestIndexVolumeServiceImpl extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIndexVolumeServiceImpl.class);

    private static final List<String> GROUP_NAMES = List.of(
            "Group1",
            "Group2",
            "Group3");

    private static final List<String> NODE_NAMES = List.of(
            "node1a",
            "node2a",
            "node3a");

    @Inject
    private IndexVolumeService indexVolumeService;
    @Inject
    private IndexVolumeGroupService indexVolumeGroupService;
    @Inject
    private Provider<VolumeConfig> volumeConfigProvider;
    @Inject
    PathCreator pathCreator;
    @Inject
    NodeInfo nodeInfo;

    @Test
    void testVolumeSelection() {

        // Clear out existing vols
        indexVolumeService.find(new ExpressionCriteria())
                .getValues()
                .forEach(indexVolume -> indexVolumeService.delete(indexVolume.getId()));

        // Behaviour is different for this node compared to others
        Assertions.assertThat(NODE_NAMES)
                .contains(nodeInfo.getThisNodeName());

        final Map<String, Map<String, List<String>>> grpToNodeToPathMap = new HashMap<>();
        final Map<String, Map<String, List<IndexVolume>>> grpToNodeToVolMap = new HashMap<>();

        GROUP_NAMES.forEach(groupName -> {
            final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate(groupName);

            NODE_NAMES.forEach(nodeName -> {
                for (int i = 0; i < 3; i++) {
                    final String path = pathCreator.toAppPath(
                                    Paths.get(groupName, nodeName, "vol_" + i).toString())
                            .toString();

                    grpToNodeToPathMap.computeIfAbsent(groupName, k -> new HashMap<>())
                            .computeIfAbsent(nodeName, k -> new ArrayList<>())
                            .add(path);

                    final IndexVolume indexVolume = new IndexVolume();
                    indexVolume.setIndexVolumeGroupId(volumeGroup.getId());
                    indexVolume.setPath(path);
                    indexVolume.setNodeName(nodeName);
                    indexVolume.setState(i == 2
                            ? VolumeUseState.CLOSED
                            : VolumeUseState.ACTIVE);
                    indexVolume.setBytesTotal(ByteSizeUnit.GIBIBYTE.longBytes(5));
                    indexVolume.setBytesUsed(ByteSizeUnit.GIBIBYTE.longBytes(1));
                    indexVolume.setBytesFree(ByteSizeUnit.GIBIBYTE.longBytes(4));

                    final IndexVolume dbIndexVolume = indexVolumeService.create(indexVolume);
                    grpToNodeToVolMap.computeIfAbsent(groupName, k -> new HashMap<>())
                            .computeIfAbsent(nodeName, k -> new ArrayList<>())
                            .add(dbIndexVolume);
                }
            });
        });

        Assertions.assertThat(indexVolumeService.find(new ExpressionCriteria()).getValues())
                .hasSize(NODE_NAMES.size() * GROUP_NAMES.size() * 3);

        final String groupName = GROUP_NAMES.get(0);
        NODE_NAMES.forEach(nodeName -> {
            final List<IndexVolume> expectedVolumes = grpToNodeToVolMap.get(groupName)
                    .get(nodeName);
            for (int i = 0; i < 3; i++) {
                final IndexVolume indexVolume = indexVolumeService.selectVolume(groupName, nodeName);

                Assertions.assertThat(indexVolume)
                        .isNotNull();
                Assertions.assertThat(indexVolume.getIndexVolumeGroupId())
                        .isEqualTo(indexVolumeGroupService.get(groupName).getId());
                Assertions.assertThat(indexVolume.getNodeName())
                        .isEqualTo(nodeName);

                // Last one is not active so won't be selected so it loops back round
                final IndexVolume expectedVolume = i == 2
                        ? expectedVolumes.get(0)
                        : expectedVolumes.get(i);

                Assertions.assertThat(indexVolume.getNodeName())
                        .isEqualTo(expectedVolume.getNodeName());
                Assertions.assertThat(indexVolume.getIndexVolumeGroupId())
                        .isEqualTo(expectedVolume.getIndexVolumeGroupId());
                Assertions.assertThat(indexVolume.getPath())
                        .isEqualTo(expectedVolume.getPath());
            }
        });
    }
}
