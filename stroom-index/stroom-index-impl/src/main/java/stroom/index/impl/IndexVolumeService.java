package stroom.index.impl;

import stroom.index.shared.CreateVolumeRequest;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import java.util.List;

public interface IndexVolumeService {
    IndexVolume create(CreateVolumeRequest createVolumeRequest);

    IndexVolume update(IndexVolume indexVolume);

    IndexVolume getById(int id);

    Boolean delete(int id);

    List<IndexVolume> getAll();

    void rescan();
}