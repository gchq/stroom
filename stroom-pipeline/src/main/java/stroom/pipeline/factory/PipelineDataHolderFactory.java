/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
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

    @Inject
    public PipelineDataHolderFactory(final PipelineStackLoader pipelineStackLoader,
                                     final SecurityContext securityContext) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
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
