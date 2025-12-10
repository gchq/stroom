/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.FetchPipelineJsonResponse;
import stroom.pipeline.shared.FetchPropertyTypesResult;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineJsonRequest;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.util.shared.FetchWithUuid;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.stream.Collectors;

@AutoLogged
class PipelineResourceImpl implements PipelineResource, FetchWithUuid<PipelineDoc> {

    private final Provider<ElementRegistryFactory> elementRegistryFactoryProvider;
    private final Provider<PipelineService> pipelineServiceProvider;

    @Inject
    PipelineResourceImpl(final Provider<PipelineService> pipelineServiceProvider,
                         final Provider<ElementRegistryFactory> elementRegistryFactoryProvider) {
        this.pipelineServiceProvider = pipelineServiceProvider;
        this.elementRegistryFactoryProvider = elementRegistryFactoryProvider;
    }

    @Override
    public PipelineDoc fetch(final String uuid) {
        return pipelineServiceProvider.get().fetch(uuid);
    }

    @Override
    public PipelineDoc update(final String uuid, final PipelineDoc doc) {
        return pipelineServiceProvider.get().update(uuid, doc);
    }

    @Override
    public Boolean savePipelineJson(final SavePipelineJsonRequest request) {
        return pipelineServiceProvider.get().savePipelineJson(request.getPipeline(), request.getJson());
    }

    @Override
    public FetchPipelineJsonResponse fetchPipelineJson(final DocRef pipeline) {
        if (pipeline != null) {
            final String json = pipelineServiceProvider.get().fetchPipelineJson(pipeline);
            return new FetchPipelineJsonResponse(pipeline, json);
        }

        return null;
    }

    @Override
    public List<PipelineLayer> fetchPipelineLayers(final DocRef pipeline) {
        return pipelineServiceProvider.get().fetchPipelineLayers(pipeline);
    }

    @Override
    public List<FetchPropertyTypesResult> getPropertyTypes() {
        return elementRegistryFactoryProvider.get().get().getPropertyTypes()
                    .entrySet()
                    .stream()
                    .map(entry ->
                            new FetchPropertyTypesResult(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
    }
}
