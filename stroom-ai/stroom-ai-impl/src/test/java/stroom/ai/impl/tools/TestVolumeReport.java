/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestVolumeReport {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    void aGroupCanBeWrittenToWhileOneOfItsVolumesIsActiveAndNotFull() {
        final Map<String, List<FsVolume>> byGroup = new LinkedHashMap<>();
        byGroup.put("Default", List.of(
                data("/data/1", VolumeUseStatus.ACTIVE, 100L, 100L, 1_000L),
                data("/data/2", VolumeUseStatus.ACTIVE, 100L, 40L, 1_000L)));
        byGroup.put("Archive", List.of(
                data("/archive/1", VolumeUseStatus.ACTIVE, 100L, 100L, 1_000L),
                data("/archive/2", VolumeUseStatus.INACTIVE, 100L, 10L, 1_000L)));
        byGroup.put("Empty", List.of());

        final List<Map<String, Object>> groups = VolumeReport.dataGroups(byGroup, "Default", null, true);

        assertThat(groups).extracting(group -> group.get("group")).containsExactly("Default", "Archive", "Empty");
        assertThat(groups.get(0)).containsEntry("default", true).containsEntry("writable", true);
        // Full and inactive volumes both take no new streams, so the group can't be written to.
        assertThat(groups.get(1)).doesNotContainKey("default").containsEntry("writable", false);
        assertThat(groups.get(2)).containsEntry("writable", false).containsEntry("volumes", List.of());
        assertThat(volumes(groups.get(1))).satisfiesExactly(
                full -> assertThat(full).containsEntry("path", "/archive/1").containsEntry("status", "Active")
                        .containsEntry("full", true).containsEntry("percent_used", 100.0)
                        .containsEntry("writable", false),
                inactive -> assertThat(inactive).containsEntry("status", "Inactive").containsEntry("full", false)
                        .containsEntry("writable", false));
    }

    @Test
    void aVolumeWithNoLimitIsFullAtOnePercentOrTenGibFreeWhicheverIsSmaller() {
        // 100 GiB: 1% (1 GiB) is the smaller.
        assertThat(VolumeReport.writable(data("/small", VolumeUseStatus.ACTIVE, null, 99 * GIB, 100 * GIB)))
                .isFalse();
        assertThat(VolumeReport.writable(data("/small", VolumeUseStatus.ACTIVE, null, 98 * GIB, 100 * GIB)))
                .isTrue();
        // 2,000 GiB: 10 GiB is the smaller.
        assertThat(VolumeReport.writable(data("/large", VolumeUseStatus.ACTIVE, null, 1_990 * GIB, 2_000 * GIB)))
                .isFalse();
        assertThat(VolumeReport.writable(data("/large", VolumeUseStatus.ACTIVE, null, 1_980 * GIB, 2_000 * GIB)))
                .isTrue();
    }

    @Test
    void aVolumeNotYetMeasuredIsNeverFull() {
        final FsVolume unmeasured = FsVolume.builder()
                .path("/data/new")
                .status(VolumeUseStatus.ACTIVE)
                .byteLimit(100L)
                .volumeType(FsVolumeType.STANDARD)
                .volumeState(new FsVolumeState(1, 1, null, null, null, null))
                .build();

        final Map<String, Object> volume = volumes(VolumeReport.dataGroups(Map.of("Default",
                List.of(unmeasured)), null, null, true).getFirst()).getFirst();

        assertThat(volume).containsEntry("space", "not measured yet").containsEntry("writable", true)
                .doesNotContainKeys("used", "full");
    }

    @Test
    void anS3VolumeIsGivenByItsBucket() {
        final FsVolume s3 = data("/unused", VolumeUseStatus.ACTIVE, null, null, null).copy()
                .volumeType(FsVolumeType.S3_V2)
                .s3ClientConfig(S3ClientConfig.builder().bucketName("stroom-data").build())
                .build();

        final Map<String, Object> volume = volumes(VolumeReport.dataGroups(Map.of("S3", List.of(s3)), null, null,
                true).getFirst()).getFirst();

        assertThat(volume).containsEntry("type", FsVolumeType.S3_V2.getDisplayValue())
                .containsEntry("bucket", "stroom-data")
                .doesNotContainKey("path");
    }

    @Test
    void theReducedViewSaysOnlyWhetherEachGroupCanBeWrittenTo() {
        final List<Map<String, Object>> groups = VolumeReport.dataGroups(Map.of("Default", List.of(
                data("/data/1", VolumeUseStatus.ACTIVE, 100L, 100L, 1_000L),
                data("/data/2", VolumeUseStatus.ACTIVE, 100L, 10L, 1_000L))), "Default", null, false);

        assertThat(groups).singleElement().satisfies(group -> assertThat(group)
                .containsEntry("writable", true)
                .containsEntry("volume_count", 2)
                .containsEntry("writable_volume_count", 1L)
                .doesNotContainKey("volumes"));
        assertThat(groups.toString()).doesNotContain("/data/");
    }

    @Test
    void onlyTheGroupAskedForIsReported() {
        final Map<String, List<FsVolume>> byGroup = new LinkedHashMap<>();
        byGroup.put("Default", List.of());
        byGroup.put("Archive", List.of());

        assertThat(VolumeReport.dataGroups(byGroup, null, "archive", true))
                .extracting(group -> group.get("group"))
                .containsExactly("Archive");
    }

    @Test
    void anIndexGroupIsWrittenToOnEachNodeFromThatNodesOwnVolumes() {
        final List<IndexVolume> volumes = List.of(
                index("node1", "/idx/a", VolumeUseState.ACTIVE, 100L, 100L),
                index("node1", "/idx/b", VolumeUseState.ACTIVE, 100L, 20L),
                index("node2", "/idx/a", VolumeUseState.ACTIVE, 100L, 100L),
                index("node3", "/idx/a", VolumeUseState.CLOSED, 100L, 0L));

        final Map<String, Object> full = VolumeReport.indexGroups(Map.of("Group1", volumes), "Group1", null, true)
                .getFirst();
        final Map<String, Object> reduced = VolumeReport.indexGroups(Map.of("Group1", volumes), "Group1", null,
                false).getFirst();

        assertThat(full).containsEntry("default", true)
                .containsEntry("writable", true)
                .containsEntry("writable_on", List.of("node1"))
                .containsEntry("not_writable_on", List.of("node2", "node3"));
        assertThat(volumes(full)).first().satisfies(volume -> assertThat(volume)
                .containsEntry("node", "node1").containsEntry("path", "/idx/a").containsEntry("state", "Active")
                .containsEntry("full", true));
        assertThat(reduced).containsEntry("writable", true)
                .containsEntry("nodes_writable", 1)
                .containsEntry("nodes_not_writable", 2)
                .doesNotContainKeys("volumes", "writable_on", "not_writable_on");
    }

    @Test
    void theBehaviourSaysNothingIsDeletedToMakeRoom() {
        // Seen: asked what happens when the data volumes fill, the agent said Stroom deletes the oldest streams.
        assertThat(String.join(" ", VolumeReport.DATA_BEHAVIOUR))
                .contains("Nothing is deleted to make room")
                .contains("never deletes data because a volume is full");
        assertThat(String.join(" ", VolumeReport.INDEX_BEHAVIOUR)).contains("Nothing is deleted to make room");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> volumes(final Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("volumes");
    }

    private static FsVolume data(final String path,
                                 final VolumeUseStatus status,
                                 final Long limit,
                                 final Long used,
                                 final Long total) {
        return FsVolume.builder()
                .path(path)
                .status(status)
                .byteLimit(limit)
                .volumeType(FsVolumeType.STANDARD)
                .volumeState(new FsVolumeState(1, 1, used, used == null || total == null
                        ? null
                        : total - used, total, 0L))
                .volumeGroup(FsVolumeGroup.builder().name("Default").build())
                .build();
    }

    private static IndexVolume index(final String node,
                                     final String path,
                                     final VolumeUseState state,
                                     final Long limit,
                                     final Long used) {
        return IndexVolume.builder()
                .nodeName(node)
                .path(path)
                .state(state)
                .bytesLimit(limit)
                .bytesUsed(used)
                .bytesTotal(1_000L)
                .bytesFree(1_000L - used)
                .build();
    }
}
