package stroom.data.store.api;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;

import java.util.List;

public interface FsVolumeGroupService {

    String ENTITY_TYPE = "FS_VOLUME_GROUP";
    DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);

//    List<String> getNames();

    List<FsVolumeGroup> getAll();

//    FsVolumeGroup create();

    FsVolumeGroup create(FsVolumeGroup fsVolumeGroup);

    FsVolumeGroup getOrCreate(DocRef docRef, boolean isDefaultVolumeGroup);

    FsVolumeGroup update(FsVolumeGroup indexVolumeGroup);

    /**
     * Should only be used for checking if another vol group exists with this name.
     * Fetching vol groups should be done by ID/{@link DocRef}
     */
    FsVolumeGroup get(String name);

    FsVolumeGroup get(int id);

    FsVolumeGroup get(DocRef docRef);

    FsVolumeGroup getDefaultVolumeGroup();

    void delete(int id);

    void ensureDefaultVolumes();

    List<FsVolumeGroup> find(final List<String> nameFilters,
                             final boolean allowWildCards);
}
