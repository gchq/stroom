package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;

import java.util.List;

public interface FsVolumeGroupDao {

    FsVolumeGroup getOrCreate(FsVolumeGroup volumeGroup);

    FsVolumeGroup update(FsVolumeGroup volumeGroup);

    FsVolumeGroup get(int id);

    FsVolumeGroup get(String name);

    List<String> getNames();

    List<FsVolumeGroup> getAll();

    void delete(String name);

    void delete(int id);
}
