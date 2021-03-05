/*
 * Copyright 2017 Crown Copyright
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
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.shared.DocRefUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.factory.PipelineDataValidator;
import stroom.pipeline.factory.PipelineStackLoader;
import stroom.pipeline.shared.FetchPipelineXmlResponse;
import stroom.pipeline.shared.FetchPropertyTypesResult;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineXmlRequest;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.PermissionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

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
    public Boolean savePipelineXml(final SavePipelineXmlRequest request) {
        return pipelineServiceProvider.get().savePipelineXml(request.getPipeline(), request.getXml());
    }

    @Override
    public FetchPipelineXmlResponse fetchPipelineXml(final DocRef pipeline) {
        if (pipeline != null) {
            final String xml = pipelineServiceProvider.get().fetchPipelineXml(pipeline);
            return new FetchPipelineXmlResponse(pipeline, xml);
        }

        return null;
    }

    @Override
    public List<PipelineData> fetchPipelineData(final DocRef pipeline) {
        return pipelineServiceProvider.get().fetchPipelineData(pipeline);
    }

    @Override
    public List<FetchPropertyTypesResult> getPropertyTypes() {
        return  elementRegistryFactoryProvider.get().get().getPropertyTypes()
                    .entrySet()
                    .stream()
                    .map(entry ->
                            new FetchPropertyTypesResult(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
    }
}
