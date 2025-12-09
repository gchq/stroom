package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PipelineDataHolderFactory {

    private final PipelineStackLoader pipelineStackLoader;
    private final SecurityContext securityContext;
    private final PipelineStore pipelineStore;

    @Inject
    public PipelineDataHolderFactory(final PipelineStackLoader pipelineStackLoader,
                                     final SecurityContext securityContext,
                                     final PipelineStore pipelineStore) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.pipelineStore = pipelineStore;
    }

    public PipelineDataHolder create(final DocRef docRef) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(docRef);
        return create(pipelineDoc);
    }

    public PipelineDataHolder create(final PipelineDoc pipelineDoc) {
        return securityContext.asProcessingUserResult(() -> {
            final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);
            // Iterate over the pipeline list reading the deepest ancestor first.
            final List<PipelineLayer> pipelineLayers = new ArrayList<>(pipelines.size());

            for (final PipelineDoc pipe : pipelines) {
                final PipelineData pipelineData = pipe.getPipelineData();
                if (pipelineData != null) {
                    pipelineLayers.add(new PipelineLayer(DocRefUtil.create(pipe), pipelineData));
                }
            }

            final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
            try {
                pipelineDataMerger.merge(pipelineLayers);
            } catch (final PipelineModelException e) {
                throw new PipelineFactoryException(e);
            }

            final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();
            // Include all the docRefs of the docs in the inheritance chain so we can invalidate
            // cache entries if any one of them is changed.
            final Set<DocRef> docRefs = pipelines.stream()
                    .map(DocRefUtil::create)
                    .collect(Collectors.toSet());
            return new PipelineDataHolder(mergedPipelineData, docRefs);
        });
    }

}
