package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupDao {
    IndexVolumeGroup getOrCreate(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup get(int id);

    IndexVolumeGroup get(String name);

    List<String> getNames();

    List<IndexVolumeGroup> getAll();

    void delete(int id);
}
