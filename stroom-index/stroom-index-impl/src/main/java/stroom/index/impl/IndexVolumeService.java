package stroom.index.impl;

import stroom.index.shared.IndexVolume;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import java.util.List;

public interface IndexVolumeService extends Clearable, Flushable {

    IndexVolume create(CreateVolumeDTO createVolumeDTO);

    IndexVolume update(UpdateVolumeDTO updateVolumeDTO);

    IndexVolume getById(int id);

    void delete(int id);

    List<IndexVolume> getAll();

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
//     * Retrieve all the volumes that are in a given group.
//     * @param groupName The group name to filter on.
//     * @return The list of volumes in that group.
//     */
//    List<IndexVolume> getVolumesInGroup(String groupName);
//
//    /**
//     * Retrieve all the groups that a given volume belongs to
//     * @param id The ID of the volume to filter on
//     * @return The list of groups that the volume belongs to.
//     */
//    List<IndexVolumeGroup> getGroupsForVolume(int id);
}