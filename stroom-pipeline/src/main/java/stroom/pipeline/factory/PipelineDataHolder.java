package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.pipeline.shared.data.PipelineData;

import java.util.Set;

public class PipelineDataHolder {

    private final PipelineData mergedPipelineData;
    private final Set<DocRef> docRefs;

    PipelineDataHolder(final PipelineData mergedPipelineData,
                               final Set<DocRef> docRefs) {
        this.mergedPipelineData = mergedPipelineData;
        this.docRefs = docRefs;
    }

    public PipelineData getMergedPipelineData() {
        return mergedPipelineData;
    }

    public boolean containsDocRef(final DocRef docRef) {
        return docRefs.contains(docRef);
    }

    @Override
    public String toString() {
        return "PipelineDataHolder{" +
               "mergedPipelineData=" + mergedPipelineData +
               ", docRefs=" + docRefs +
               '}';
    }
}
