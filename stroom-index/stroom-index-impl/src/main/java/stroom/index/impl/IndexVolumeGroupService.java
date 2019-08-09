package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupService {
    List<String> getNames();
    List<IndexVolumeGroup> getAll();
    IndexVolumeGroup create();
    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);
    IndexVolumeGroup get(String name);
    void delete(int id);
}
