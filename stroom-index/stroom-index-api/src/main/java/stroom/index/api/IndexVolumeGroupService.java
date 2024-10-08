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

    IndexVolumeGroup getOrCreate(String name);

    IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup);

    IndexVolumeGroup get(String name);

    IndexVolumeGroup get(int id);

    void delete(int id);

    void ensureDefaultVolumes();
}
