package stroom.index.service;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupService {
    List<String> getNames();
    List<IndexVolumeGroup> getAll();
    IndexVolumeGroup create(String name);
    IndexVolumeGroup get(String name);
    void delete(String name);
}
