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
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.util.shared.HasCapacityInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/// What the volumes tool says of Stroom's data and index volumes: in full, each volume's state, space and whether
/// it can be written to; or, for a user without the Manage Volumes permission, only whether each group can still
/// be written to, without paths or sizes. With either, how Stroom treats volumes ([#DATA_BEHAVIOUR],
/// [#INDEX_BEHAVIOUR]), so that what it does when one fills is never guessed.
final class VolumeReport {

    /// How Stroom writes to data volumes and what happens when they fill, as its code does it.
    static final List<String> DATA_BEHAVIOUR = List.of(
            "Each new stream is written to one volume of its feed's volume group (the default group if the feed "
            + "names none): an Active volume that isn't full, picked by the volume selector "
            + "(stroom.data.filesystemVolume.volumeSelector).",
            "A volume is full once its used space reaches its limit. With no limit set, it is full once its free "
            + "space falls to 1% of its size or 10 GiB, whichever is smaller. The default volumes Stroom creates "
            + "itself are given a limit of "
            + "stroom.data.filesystemVolume.defaultStreamVolumeFilesystemUtilisation (90%) of their filesystem, "
            + "and that filesystem's other contents count towards it.",
            "Space is as last measured (the File System Volume Status job); a volume not yet measured is never "
            + "treated as full.",
            "Inactive, Closed and full volumes are still read; they just get no new streams.",
            "When no volume of a group can be written to, writing a stream to it fails ('no writable volumes'): "
            + "data received for its feeds, and the output of processing, fails until space is freed, a limit "
            + "raised or a volume added. Nothing is deleted to make room.",
            "Stroom never deletes data because a volume is full: streams are only deleted by the data retention "
            + "rules (see " + ExplainRetentionTool.NAME + ") or by a user.");

    /// How Stroom writes to index volumes and what happens when they fill, as its code does it.
    static final List<String> INDEX_BEHAVIOUR = List.of(
            "A new index shard is created on a volume of the index's volume group on the node doing the "
            + "indexing: an Active volume on that node that isn't full. Volumes on other nodes don't count.",
            "Full is judged as for data volumes: its limit if set, else free space down to 1% of its size or 10 "
            + "GiB, whichever is smaller; space is as last measured (the Index Volume Status job).",
            "When a node has no such volume in the group, indexing there fails ('Unable to find any non-full "
            + "index volumes'). Nothing is deleted to make room.",
            "Index shards are only deleted by an index's own retention (the Index Shard Retention and Index Shard "
            + "Delete jobs) or by a user, never because a volume is full.");

    static final String REDUCED = "Reduced view: you don't have the Manage Volumes permission, so only whether "
                                  + "each group can still be written to is shown, not its volumes' paths or "
                                  + "sizes. Someone with Manage Volumes (the Volumes screen) can see them.";

    private VolumeReport() {
        // Static only.
    }

    /// @param byGroup      Each data volume group with its volumes.
    /// @param defaultGroup The default group, or null.
    /// @param onlyGroup    The only group to report, or null for all.
    /// @param full         Whether to give each volume (the user has Manage Volumes).
    /// @return The data volume groups: each `group`, whether it is the `default`, whether it is `writable`,
    /// and, in full, its `volumes` (else counts of them).
    static List<Map<String, Object>> dataGroups(final Map<String, List<FsVolume>> byGroup,
                                                final String defaultGroup,
                                                final String onlyGroup,
                                                final boolean full) {
        final List<Map<String, Object>> groups = new ArrayList<>();
        NullSafe.map(byGroup).forEach((name, volumes) -> {
            if (onlyGroup == null || onlyGroup.equalsIgnoreCase(name)) {
                final Map<String, Object> group = new LinkedHashMap<>();
                group.put("group", name);
                if (Objects.equals(name, defaultGroup)) {
                    group.put("default", true);
                }
                final long writable = volumes.stream().filter(VolumeReport::writable).count();
                group.put("writable", writable > 0);
                if (full) {
                    group.put("volumes", volumes.stream().map(VolumeReport::dataVolume).toList());
                } else {
                    group.put("volume_count", volumes.size());
                    group.put("writable_volume_count", writable);
                }
                groups.add(group);
            }
        });
        return groups;
    }

    /// @param byGroup      Each index volume group with its volumes on every node.
    /// @param defaultGroup The default group, or null.
    /// @param onlyGroup    The only group to report, or null for all.
    /// @param full         Whether to give each volume (the user has Manage Volumes).
    /// @return The index volume groups: each `group`, whether it is the `default`, the nodes it can and can't
    /// be written to on (in full; else how many), and, in full, its `volumes`.
    static List<Map<String, Object>> indexGroups(final Map<String, List<IndexVolume>> byGroup,
                                                 final String defaultGroup,
                                                 final String onlyGroup,
                                                 final boolean full) {
        final List<Map<String, Object>> groups = new ArrayList<>();
        NullSafe.map(byGroup).forEach((name, volumes) -> {
            if (onlyGroup == null || onlyGroup.equalsIgnoreCase(name)) {
                final Set<String> nodes = new TreeSet<>();
                final Set<String> writableOn = new TreeSet<>();
                volumes.forEach(volume -> {
                    nodes.add(String.valueOf(volume.getNodeName()));
                    if (writable(volume)) {
                        writableOn.add(String.valueOf(volume.getNodeName()));
                    }
                });
                final Set<String> notWritableOn = new TreeSet<>(nodes);
                notWritableOn.removeAll(writableOn);
                final Map<String, Object> group = new LinkedHashMap<>();
                group.put("group", name);
                if (Objects.equals(name, defaultGroup)) {
                    group.put("default", true);
                }
                group.put("writable", !writableOn.isEmpty());
                if (full) {
                    group.put("writable_on", List.copyOf(writableOn));
                    group.put("not_writable_on", List.copyOf(notWritableOn));
                    group.put("volumes", volumes.stream().map(VolumeReport::indexVolume).toList());
                } else {
                    group.put("nodes_writable", writableOn.size());
                    group.put("nodes_not_writable", notWritableOn.size());
                }
                groups.add(group);
            }
        });
        return groups;
    }

    /// @param volume A data volume.
    /// @return Whether a new stream could be written to it: Active and not full.
    static boolean writable(final FsVolume volume) {
        return volume.getStatus() == VolumeUseStatus.ACTIVE && !volume.getCapacityInfo().isFull();
    }

    /// @param volume An index volume.
    /// @return Whether a new shard could be created on it: Active and not full.
    static boolean writable(final IndexVolume volume) {
        return volume.getState() == VolumeUseState.ACTIVE && !volume.getCapacityInfo().isFull();
    }

    private static Map<String, Object> dataVolume(final FsVolume volume) {
        final Map<String, Object> out = new LinkedHashMap<>();
        final FsVolumeType type = volume.getVolumeType();
        if (type != null && type != FsVolumeType.STANDARD) {
            out.put("type", type.getDisplayValue());
            out.put("bucket", NullSafe.get(volume.getS3ClientConfig(), S3ClientConfig::getBucketName));
        } else {
            out.put("path", volume.getPath());
        }
        out.put("status", NullSafe.get(volume.getStatus(), VolumeUseStatus::getDisplayValue));
        out.putAll(space(volume.getCapacityInfo()));
        out.put("writable", writable(volume));
        return out;
    }

    private static Map<String, Object> indexVolume(final IndexVolume volume) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("node", volume.getNodeName());
        out.put("path", volume.getPath());
        out.put("state", NullSafe.get(volume.getState(), VolumeUseState::getDisplayValue));
        out.putAll(space(volume.getCapacityInfo()));
        out.put("writable", writable(volume));
        return out;
    }

    private static Map<String, Object> space(final HasCapacityInfo capacity) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (!capacity.hasValidState()) {
            out.put("space", "not measured yet");
            return out;
        }
        putSize(out, "limit", capacity.getCapacityLimitBytes());
        putSize(out, "total", capacity.getTotalCapacityBytes());
        putSize(out, "used", capacity.getCapacityUsedBytes());
        putSize(out, "free", capacity.getFreeCapacityBytes());
        final OptionalDouble percent = capacity.getUsedCapacityPercent();
        if (percent.isPresent()) {
            out.put("percent_used", Math.round(percent.getAsDouble() * 10) / 10.0);
        }
        out.put("full", capacity.isFull());
        return out;
    }

    private static void putSize(final Map<String, Object> out, final String name, final OptionalLong bytes) {
        if (bytes.isPresent()) {
            out.put(name, ModelStringUtil.formatIECByteSizeString(bytes.getAsLong()));
        }
    }
}
