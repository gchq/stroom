package stroom.index.service;

import stroom.index.shared.IndexVolume;

import java.util.List;

public interface IndexVolumeService {
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