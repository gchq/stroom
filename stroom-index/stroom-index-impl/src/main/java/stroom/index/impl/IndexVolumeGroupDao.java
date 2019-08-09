package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupDao {
    IndexVolumeGroup getOrCreate(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup get(String name);

    IndexVolumeGroup get(int id);

    List<String> getNames();

    List<IndexVolumeGroup> getAll();

    void delete(int id);

    void delete(String name);
}
