package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupDao {
    IndexVolumeGroup create(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup get(String name);

    List<String> getNames();

    List<IndexVolumeGroup> getAll();

    void delete(String name);
}
