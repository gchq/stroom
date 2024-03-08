package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Set;

public interface FsVolumeDao {

    FsVolume create(FsVolume fileVolume);

    FsVolume fetch(int id);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    ResultPage<FsVolume> find(FindFsVolumeCriteria criteria);

    Set<FsVolume> get(final String path);

    List<FsVolume> getAll();

    List<FsVolume> getVolumesInGroup(String groupName);

    List<FsVolume> getVolumesInGroup(final int groupid);
}
