package stroom.index;

import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupDao {
    List<String> getNames();
    List<IndexVolumeGroup> getAll();
    IndexVolumeGroup create(String name);
    IndexVolumeGroup get(String name);
    void delete(String name);

}
