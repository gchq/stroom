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

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IndexVolumeDao {

    IndexVolume create(IndexVolume indexVolume);

    IndexVolume update(IndexVolume indexVolume);

    Optional<IndexVolume> fetch(int id);

    boolean delete(int id);

    List<IndexVolume> getAll();

//    /**
//     * Retrieve all the volumes in a specific group.
//     * @param groupName The name of the group to filter on.
//     * @return The list of index volumes in that specific group.
//     */
//    List<IndexVolume> getVolumesInGroup(String groupName);
//
//    /**
//     * Retrieve the list of groups a volume belongs to.
//     * @param id The ID of the volume to filter on.
//     * @return The list of groups
//     */
//    List<IndexVolumeGroup> getGroupsForVolume(int id);
//

    /**
     * Retrieve all the volumes in a specific group, on a specific node.
     * Used to retrieve a volume on which to put a new shard.
     *
     * @param groupName The name of the group to filter on.
     * @param nodeName  The node on which the volume must belong.
     * @return A list of candidate Index Volumes.
     */
    List<IndexVolume> getVolumesInGroupOnNode(String groupName, String nodeName);

    List<IndexVolume> getVolumesInGroup(String groupName);

    List<IndexVolume> getVolumesInGroup(final int groupid);

    Map<String, List<IndexVolume>> getVolumesOnNodeGrouped(final String nodeName);

    ResultPage<IndexVolume> find(ExpressionCriteria criteria);

    Set<IndexVolumeGroup> getGroups(final String nodeName, final String path);
//
//    /**
//     * Add to membership of a volume group.
//     * @param volumeId The ID of the volume to add
//     * @param name The group into which the volume is being added.
//     */
//    void addVolumeToGroup(int volumeId, String name);
//
//    /**
//     * Remove a volume from a group.
//     * @param volumeId The ID of the volume to remove.
//     * @param name The name of the group
//     */
//    void removeVolumeFromGroup(int volumeId, String name);
//
//    /**
//     * Clear all group memberships for a specific volume.
//     * Used to remove a volume from the available pool.
//     * @param volumeId The ID of the volume to remove from all groups.
//     */
//    void clearVolumeGroupMemberships(int volumeId);


    void updateVolumeState(int id, Long updateTimeMs, Long bytesUsed, Long bytesFree, Long bytesTotal);
}
