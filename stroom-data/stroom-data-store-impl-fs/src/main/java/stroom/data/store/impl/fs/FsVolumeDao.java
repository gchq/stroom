package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;

import java.util.List;

public interface FsVolumeDao {
    FsVolume create(FsVolume fileVolume);

    FsVolume fetch(int id);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    List<FsVolume> find(FindFsVolumeCriteria criteria);
}
