package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.ResultPage;

public interface FsVolumeDao {
    FsVolume create(FsVolume fileVolume);

    FsVolume fetch(int id);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    ResultPage<FsVolume> find(FindFsVolumeCriteria criteria);
}
