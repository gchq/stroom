package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.IndexVolumeGroup;

import java.util.List;

public interface IndexVolumeGroupService {

    String ENTITY_TYPE = "INDEX_VOLUME_GROUP";
    DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);

    List<String> getNames();

    List<IndexVolumeGroup> getAll();

    IndexVolumeGroup create();

    IndexVolumeGroup create(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup getOrCreate(String name);

    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);

    /**
     * Should only be used for checking if another vol group exists with this name.
     * Fetching vol groups should be done by ID/{@link DocRef}
     */
    IndexVolumeGroup get(String name);

    IndexVolumeGroup get(int id);

    IndexVolumeGroup get(DocRef docRef);

    IndexVolumeGroup getDefaultVolumeGroup();

    void delete(int id);

    void ensureDefaultVolumes();
}
