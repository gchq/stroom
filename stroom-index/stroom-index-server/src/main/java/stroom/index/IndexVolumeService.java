package stroom.index;

import stroom.index.shared.IndexVolume;

import java.util.List;

public interface IndexVolumeService {
    List<IndexVolume> getVolumesInGroup(String groupName);
}