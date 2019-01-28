package stroom.index;

import stroom.index.shared.IndexVolume;

import java.util.List;

public interface IndexVolumeService {
    List<IndexVolume> getVolumesOnNode(String nodeName);
    List<IndexVolume> getVolumesInGroup(String groupName);
    List<IndexVolume> getVolumesInGroupOnNode(String groupName, String nodeName);
}