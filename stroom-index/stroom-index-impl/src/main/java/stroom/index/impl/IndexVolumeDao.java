package stroom.index.impl;

import stroom.index.shared.IndexVolume;

import java.util.List;
import java.util.Optional;

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
//    /**
//     * Retrieve all the volumes in a specific group, on a specific node.
//     * Used to retrieve a volume on which to put a new shard.
//     * @param groupName The name of the group to filter on.
//     * @param nodeName The node on which the volume must belong.
//     * @return A list of candidate Index Volumes.
//     */
//    List<IndexVolume> getVolumesInGroupOnNode(String groupName, String nodeName);
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
}
