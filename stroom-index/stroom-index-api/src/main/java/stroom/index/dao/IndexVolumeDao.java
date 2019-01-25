package stroom.index.dao;

import stroom.index.shared.IndexVolume;

import java.util.Set;

public interface IndexVolumeDao {
    Set<IndexVolume> getVolumesForIndex(String indexUuid);
}
