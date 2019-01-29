package stroom.index.dao;

import stroom.index.shared.IndexVolume;

import java.util.List;

public interface IndexVolumeDao {
    IndexVolume create(String nodeName, String path);

    IndexVolume getById(Long id);

    void delete(Long id);

    List<IndexVolume> getVolumesInGroup(String groupName);

    List<IndexVolume> getVolumesInGroupOnNode(String groupName, String nodeName);

    void addVolumeToGroup(Long volumeId, String name);
    void removeVolumeFromGroup(Long volumeId, String name);
    void clearVolumeGroupMemberships(Long volumeId);
}
