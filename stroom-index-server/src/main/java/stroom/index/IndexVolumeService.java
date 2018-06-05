package stroom.index;

import stroom.node.shared.VolumeEntity;
import stroom.docref.DocRef;

import java.util.Set;

public interface IndexVolumeService {
    Set<VolumeEntity> getVolumesForIndex(DocRef indexRef);

    void setVolumesForIndex(DocRef indexRef, Set<VolumeEntity> volumes);
}