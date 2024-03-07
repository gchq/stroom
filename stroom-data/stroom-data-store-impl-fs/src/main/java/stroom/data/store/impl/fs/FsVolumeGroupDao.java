package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;

import java.util.List;

public interface FsVolumeGroupDao {

    FsVolumeGroup getOrCreate(FsVolumeGroup volumeGroup);

    FsVolumeGroup update(FsVolumeGroup volumeGroup);

    FsVolumeGroup get(int id);

    /**
     * Should be using {@link FsVolumeGroupDao#get(DocRef)} or {@link FsVolumeGroupDao#get(int)}
     */
    @Deprecated
    FsVolumeGroup get(String name);

    FsVolumeGroup get(DocRef docRef);

    /**
     * @return The volume group that has been marked as the default one to use.
     * May be null if no default is set.
     */
    FsVolumeGroup getDefaultVolumeGroup();

    List<String> getNames();

    List<FsVolumeGroup> getAll();

    void delete(String name);

    void delete(int id);

    FsVolumeGroup create(FsVolumeGroup fsVolumeGroup);
}
