package stroom.index.service;

import stroom.index.shared.IndexVolume;

import java.util.List;

public interface IndexVolumeService {

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

    List<IndexVolume> getVolumesInGroup(String groupName);
}