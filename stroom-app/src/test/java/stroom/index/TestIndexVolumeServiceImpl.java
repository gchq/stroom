/*
 * Copyright 2022 Crown Copyright
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
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.ValidationResult;
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
    void testMixedGroupCreatesShardsOnSharedVolumes() {
        clearVolumes();
        final String groupName = "MixedGroup";
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate(groupName);
        NODE_NAMES.forEach(nodeName ->
                indexVolumeService.create(buildVolume(volumeGroup, "mixed/" + nodeName)
                        .nodeName(nodeName)
                        .build()));

        // With only local volumes each node uses its own, as before.
        NODE_NAMES.forEach(nodeName ->
                Assertions.assertThat(indexVolumeService.selectVolume(groupName, nodeName).getNodeName())
                        .isEqualTo(nodeName));

        final IndexVolume sharedVolume = indexVolumeService.create(buildVolume(volumeGroup, "mixed/shared")
                .volumeType(VolumeType.SHARED)
                // A node given for a shared volume is ignored.
                .nodeName(NODE_NAMES.getFirst())
                .build());
        Assertions.assertThat(sharedVolume.getNodeName())
                .isNull();

        // Once the group has a shared volume every node creates its new shards there, including a node
        // that has no volumes of its own.
        final List<String> nodeNames = new ArrayList<>(NODE_NAMES);
        nodeNames.add("nodeWithNoVolumes");
        nodeNames.forEach(nodeName -> {
            for (int i = 0; i < 3; i++) {
                Assertions.assertThat(indexVolumeService.selectVolume(groupName, nodeName).getId())
                        .isEqualTo(sharedVolume.getId());
            }
        });

        // If the shared volume stops taking new shards the nodes go back to their local volumes.
        indexVolumeService.update(sharedVolume.copy().state(VolumeUseState.INACTIVE).build());
        NODE_NAMES.forEach(nodeName ->
                Assertions.assertThat(indexVolumeService.selectVolume(groupName, nodeName).getNodeName())
                        .isEqualTo(nodeName));
    }

    @Test
    void testMarkVolumeShared_mergesVolumesWithSamePath() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("MergeGroup");
        final IndexVolumeGroup otherGroup = indexVolumeGroupService.getOrCreate("OtherGroup");
        final List<IndexVolume> samePathVolumes = NODE_NAMES.stream()
                .map(nodeName -> indexVolumeService.create(buildVolume(volumeGroup, "merge/share")
                        .nodeName(nodeName)
                        .build()))
                .toList();
        final IndexVolume otherPathVolume = indexVolumeService.create(buildVolume(volumeGroup, "merge/local")
                .nodeName(NODE_NAMES.getFirst())
                .build());
        // The same path in another group is left alone.
        final IndexVolume otherGroupVolume = indexVolumeService.create(buildVolume(otherGroup, "merge/share")
                .nodeName(NODE_NAMES.getFirst())
                .build());

        final IndexVolume toMarkShared = samePathVolumes.getFirst()
                .copy()
                .volumeType(VolumeType.SHARED)
                .build();

        // The user is told about the merge before it happens.
        final ValidationResult validationResult = indexVolumeService.validate(toMarkShared);
        Assertions.assertThat(validationResult.isWarning())
                .isTrue();
        Assertions.assertThat(validationResult.getMessage())
                .contains("merge")
                .contains("'" + NODE_NAMES.get(1) + "'")
                .contains("'" + NODE_NAMES.get(2) + "'");

        final IndexVolume sharedVolume = indexVolumeService.update(toMarkShared);

        Assertions.assertThat(sharedVolume.getId())
                .isEqualTo(samePathVolumes.getFirst().getId());
        Assertions.assertThat(sharedVolume.getVolumeType())
                .isEqualTo(VolumeType.SHARED);
        Assertions.assertThat(sharedVolume.getNodeName())
                .isNull();
        Assertions.assertThat(indexVolumeService.find(new ExpressionCriteria()).getValues())
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(
                        sharedVolume.getId(),
                        otherPathVolume.getId(),
                        otherGroupVolume.getId());

        // Marking it shared again changes nothing and needs no warning about a merge.
        Assertions.assertThat(indexVolumeService.validate(sharedVolume).isOk())
                .isTrue();
    }

    @Test
    void testMarkVolumeShared_warnsAboutMergeAndOtherGroupsTogether() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("MergeGroup");
        final IndexVolumeGroup otherGroup = indexVolumeGroupService.getOrCreate("OtherGroup");
        final IndexVolume volume = indexVolumeService.create(buildVolume(volumeGroup, "both/share")
                .nodeName(NODE_NAMES.get(0))
                .build());
        indexVolumeService.create(buildVolume(volumeGroup, "both/share")
                .nodeName(NODE_NAMES.get(1))
                .build());
        indexVolumeService.create(buildVolume(otherGroup, "both/share")
                .nodeName(NODE_NAMES.get(2))
                .build());

        final ValidationResult validationResult = indexVolumeService.validate(
                volume.copy().volumeType(VolumeType.SHARED).build());

        // Neither warning hides the other.
        Assertions.assertThat(validationResult.isWarning())
                .isTrue();
        Assertions.assertThat(validationResult.getMessage())
                .contains("merge")
                .contains("'OtherGroup'");
    }

    @Test
    void testEditingSharedVolumeDoesNotMerge() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("NoMergeGroup");
        final IndexVolume localVolume = indexVolumeService.create(buildVolume(volumeGroup, "nomerge/a")
                .nodeName(NODE_NAMES.getFirst())
                .build());
        final IndexVolume sharedVolume = indexVolumeService.create(buildVolume(volumeGroup, "nomerge/b")
                .volumeType(VolumeType.SHARED)
                .build());
        // Move the local volume onto the shared volume's path behind the service's back.
        indexVolumeService.update(localVolume.copy().path(sharedVolume.getPath()).build());

        // An edit of the shared volume that is not the change from local to shared leaves it alone.
        indexVolumeService.update(sharedVolume.copy().bytesLimit(ByteSizeUnit.GIBIBYTE.longBytes(2)).build());

        Assertions.assertThat(indexVolumeService.find(new ExpressionCriteria()).getValues())
                .extracting(IndexVolume::getId)
                .containsExactlyInAnyOrder(localVolume.getId(), sharedVolume.getId());
    }

    @Test
    void testDuplicateSharedVolumeRefusedOnSave() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("DupGroup");
        indexVolumeService.create(buildVolume(volumeGroup, "dup/share")
                .volumeType(VolumeType.SHARED)
                .build());

        // Saving without validating first must not make a second shared volume for the path.
        Assertions.assertThatThrownBy(() ->
                        indexVolumeService.create(buildVolume(volumeGroup, "dup/share")
                                .volumeType(VolumeType.SHARED)
                                .build()))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("already exists");

        final IndexVolume otherShared = indexVolumeService.create(buildVolume(volumeGroup, "dup/other")
                .volumeType(VolumeType.SHARED)
                .build());
        Assertions.assertThatThrownBy(() ->
                        indexVolumeService.update(otherShared.copy()
                                .path(pathCreator.toAppPath("dup/share").toString())
                                .build()))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("already exists");
        Assertions.assertThat(indexVolumeService.find(new ExpressionCriteria()).getValues())
                .hasSize(2);
    }

    @Test
    void testSharedVolumeCannotBeMadeLocal() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("SharedGroup");
        final IndexVolume sharedVolume = indexVolumeService.create(buildVolume(volumeGroup, "shared/vol")
                .volumeType(VolumeType.SHARED)
                .build());
        final IndexVolume madeLocal = sharedVolume
                .copy()
                .volumeType(VolumeType.LOCAL)
                .nodeName(NODE_NAMES.getFirst())
                .build();

        final ValidationResult validationResult = indexVolumeService.validate(madeLocal);
        Assertions.assertThat(validationResult.isError())
                .isTrue();
        Assertions.assertThat(validationResult.getMessage())
                .contains("cannot be made local");
        Assertions.assertThatThrownBy(() -> indexVolumeService.update(madeLocal))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("cannot be made local");
        Assertions.assertThat(indexVolumeService.read(sharedVolume.getId()).getVolumeType())
                .isEqualTo(VolumeType.SHARED);
    }

    @Test
    void testValidate_nodeAndPathRules() {
        clearVolumes();
        final IndexVolumeGroup volumeGroup = indexVolumeGroupService.getOrCreate("ValidateGroup");

        // A local volume needs a node, a shared volume does not.
        Assertions.assertThat(indexVolumeService.validate(buildVolume(volumeGroup, "validate/a").build()).isError())
                .isTrue();
        Assertions.assertThat(indexVolumeService.validate(buildVolume(volumeGroup, "validate/a")
                        .volumeType(VolumeType.SHARED)
                        .build()).isOk())
                .isTrue();

        // A new shared volume cannot be added for a path that local volumes already have, as marking one
        // of those shared is what merges them.
        indexVolumeService.create(buildVolume(volumeGroup, "validate/b")
                .nodeName(NODE_NAMES.getFirst())
                .build());
        final ValidationResult newSharedOnLocalPath = indexVolumeService.validate(
                buildVolume(volumeGroup, "validate/b")
                        .volumeType(VolumeType.SHARED)
                        .build());
        Assertions.assertThat(newSharedOnLocalPath.isError())
                .isTrue();
        Assertions.assertThat(newSharedOnLocalPath.getMessage())
                .contains("mark it as shared");

        // No other volume in the group may have the path of a shared volume.
        indexVolumeService.create(buildVolume(volumeGroup, "validate/c")
                .volumeType(VolumeType.SHARED)
                .build());
        Assertions.assertThat(indexVolumeService.validate(buildVolume(volumeGroup, "validate/c")
                        .volumeType(VolumeType.SHARED)
                        .build()).isError())
                .isTrue();
        Assertions.assertThat(indexVolumeService.validate(buildVolume(volumeGroup, "validate/c")
                        .nodeName(NODE_NAMES.getFirst())
                        .build()).isError())
                .isTrue();
    }

    private void clearVolumes() {
        indexVolumeService.find(new ExpressionCriteria())
                .getValues()
                .forEach(indexVolume -> indexVolumeService.delete(indexVolume.getId()));
    }

    private IndexVolume.Builder buildVolume(final IndexVolumeGroup volumeGroup, final String relativePath) {
        return IndexVolume
                .builder()
                .indexVolumeGroupId(volumeGroup.getId())
                .path(pathCreator.toAppPath(relativePath).toString())
                .bytesTotal(ByteSizeUnit.GIBIBYTE.longBytes(5))
                .bytesUsed(ByteSizeUnit.GIBIBYTE.longBytes(1))
                .bytesFree(ByteSizeUnit.GIBIBYTE.longBytes(4));
    }

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

                    final IndexVolume indexVolume = IndexVolume
                            .builder()
                            .indexVolumeGroupId(volumeGroup.getId())
                            .path(path)
                            .nodeName(nodeName)
                            .state(i == 2
                                    ? VolumeUseState.CLOSED
                                    : VolumeUseState.ACTIVE)
                            .bytesTotal(ByteSizeUnit.GIBIBYTE.longBytes(5))
                            .bytesUsed(ByteSizeUnit.GIBIBYTE.longBytes(1))
                            .bytesFree(ByteSizeUnit.GIBIBYTE.longBytes(4))
                            .build();

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
