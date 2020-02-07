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

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.document.shared.PermissionException;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.factory.PipelineDataValidator;
import stroom.pipeline.factory.PipelineStackLoader;
import stroom.pipeline.shared.FetchPropertyTypesResult;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.SavePipelineXmlRequest;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class PipelineResourceImpl implements PipelineResource, RestResource, HasHealthCheck {
    private final PipelineStore pipelineStore;
    private final DocumentResourceHelper documentResourceHelper;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;
    private final PipelineSerialiser pipelineSerialiser;
    private final ElementRegistryFactory pipelineElementRegistryFactory;
    private final SecurityContext securityContext;

    @Inject
    PipelineResourceImpl(final PipelineStore pipelineStore,
                         final DocumentResourceHelper documentResourceHelper,
                         final PipelineStackLoader pipelineStackLoader,
                         final PipelineDataValidator pipelineDataValidator,
                         final PipelineSerialiser pipelineSerialiser,
                         final ElementRegistryFactory pipelineElementRegistryFactory,
                         final SecurityContext securityContext) {
        this.pipelineStore = pipelineStore;
        this.documentResourceHelper = documentResourceHelper;
        this.pipelineStackLoader = pipelineStackLoader;
        this.pipelineDataValidator = pipelineDataValidator;
        this.pipelineSerialiser = pipelineSerialiser;
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
        this.securityContext = securityContext;
    }

    @Override
    public PipelineDoc read(final DocRef docRef) {
        return documentResourceHelper.read(pipelineStore, docRef);
    }

    @Override
    public PipelineDoc update(final PipelineDoc doc) {
        return documentResourceHelper.update(pipelineStore, doc);
    }

    @Override
    public Boolean savePipelineXml(final SavePipelineXmlRequest request) {
        return securityContext.secureResult(() -> {
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(request.getPipeline());

            if (pipelineDoc != null) {
                final PipelineData pipelineData = pipelineSerialiser.getPipelineDataFromXml(request.getXml());
                pipelineDoc.setPipelineData(pipelineData);
                pipelineStore.writeDocument(pipelineDoc);
            }

            return true;
        });
    }

    @Override
    public String fetchPipelineXml(final DocRef pipeline) {
        return securityContext.secureResult(() -> {
            String result = null;

            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipeline);
            if (pipelineDoc != null) {
                result = pipelineSerialiser.getXmlFromPipelineData(pipelineDoc.getPipelineData());
            }

            return result;
        });
    }

    @Override
    public List<PipelineData> fetchPipelineData(final DocRef pipeline) {
        return securityContext.secureResult(() -> {
            try {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipeline);

                // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
                return securityContext.useAsReadResult(() -> {
                    final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);
                    final List<PipelineData> result = new ArrayList<>(pipelines.size());

                    final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
                    for (final PipelineDoc pipe : pipelines) {
                        final PipelineData pipelineData = pipe.getPipelineData();

                        // Validate the pipeline data and add element and property type
                        // information.
                        final SourcePipeline source = new SourcePipeline(pipe);
                        pipelineDataValidator.validate(source, pipelineData, elementMap);
                        result.add(pipelineData);
                    }

                    return result;
                });
            } catch (final PermissionException e) {
                throw new PermissionException(e.getUser(), e.getMessage().replaceAll("permission to read", "permission to use"));
            }
        });
    }

    @Override
    public List<FetchPropertyTypesResult> getPropertyTypes() {
        return securityContext.secureResult(() ->
                pipelineElementRegistryFactory.get().getPropertyTypes().entrySet()
                        .stream()
                        .map(entry -> new FetchPropertyTypesResult(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}