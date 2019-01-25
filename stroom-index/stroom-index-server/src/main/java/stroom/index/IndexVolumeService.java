package stroom.index;

import stroom.index.shared.IndexVolume;
import stroom.node.shared.VolumeEntity;
import stroom.docref.DocRef;

import java.util.Set;

public interface IndexVolumeService {
    Set<IndexVolume> getVolumesForIndex(DocRef indexRef);
}