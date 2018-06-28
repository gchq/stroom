package stroom.index;

import stroom.node.shared.Volume;
import stroom.docref.DocRef;

import java.util.Set;

public interface IndexVolumeService {
    Set<Volume> getVolumesForIndex(DocRef indexRef);

    void setVolumesForIndex(DocRef indexRef, Set<Volume> volumes);
}