package stroom.index.dao;

import stroom.index.shared.IndexVolumeGroup;

public interface IndexVolumeGroupDao {
    IndexVolumeGroup create(String name);
    IndexVolumeGroup get(String name);
    void delete(String name);
}
