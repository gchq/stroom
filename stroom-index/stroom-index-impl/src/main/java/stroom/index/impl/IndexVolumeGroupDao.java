package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupDao {

    IndexVolumeGroup create(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup getOrCreate(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup get(int id);

    IndexVolumeGroup get(String name);

    /**
     * @return The volume group that has been marked as the default one to use.
     * May be null if no default is set.
     */
    IndexVolumeGroup getDefaultVolumeGroup();

    List<String> getNames();

    List<IndexVolumeGroup> getAll();

    void delete(String name);

    void delete(int id);

    IndexVolumeGroup get(DocRef docRef);

}
