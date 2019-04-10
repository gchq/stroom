package stroom.index.impl;

import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeDao {
    /**
     * Given an owning node and path, create a new index volume.
     * @param nodeName The node on which the volume lives
     * @param path The path of the volume.
     * @return The created Index Volume
     */
    IndexVolume create(String nodeName, String path);

    /**
     * Retrieve a single IndexVolume by it's unique ID
     * @param id The Unique ID of the volume
     * @return The located Index Volume
     */
    IndexVolume getById(Long id);

    /**
     * Delete a single volume
     * @param id The Unique ID of the volume to delete.
     */
    void delete(Long id);

    /**
     * Retrieve the complete list of index volumes.
     * @return
     */
    List<IndexVolume> getAll();

    /**
     * Retrieve all the volumes in a specific group.
     * @param groupName The name of the group to filter on.
     * @return The list of index volumes in that specific group.
     */
    List<IndexVolume> getVolumesInGroup(String groupName);

    /**
     * Retrieve the list of groups a volume belongs to.
     * @param id The ID of the volume to filter on.
     * @return The list of groups
     */
    List<IndexVolumeGroup> getGroupsForVolume(Long id);

    /**
     * Retrieve all the volumes in a specific group, on a specific node.
     * Used to retrieve a volume on which to put a new shard.
     * @param groupName The name of the group to filter on.
     * @param nodeName The node on which the volume must belong.
     * @return A list of candidate Index Volumes.
     */
    List<IndexVolume> getVolumesInGroupOnNode(String groupName, String nodeName);

    /**
     * Add to membership of a volume group.
     * @param volumeId The ID of the volume to add
     * @param name The group into which the volume is being added.
     */
    void addVolumeToGroup(Long volumeId, String name);

    /**
     * Remove a volume from a group.
     * @param volumeId The ID of the volume to remove.
     * @param name The name of the group
     */
    void removeVolumeFromGroup(Long volumeId, String name);

    /**
     * Clear all group memberships for a specific volume.
     * Used to remove a volume from the available pool.
     * @param volumeId The ID of the volume to remove from all groups.
     */
    void clearVolumeGroupMemberships(Long volumeId);
}
