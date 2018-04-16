package stroom.index;

import stroom.node.shared.Volume;
import stroom.query.api.v2.DocRef;

import java.util.Set;

public interface IndexVolumeService {
    Set<Volume> getVolumesForIndex(DocRef indexRef);

    void setVolumesForIndex(DocRef indexRef, Set<Volume> volumes);
}