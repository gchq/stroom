/*
 * Copyright 2017-2021 Crown Copyright
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
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.factory.PipelineDataValidator;
import stroom.pipeline.factory.PipelineStackLoader;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class PipelineServiceImpl implements PipelineService {

    private final PipelineStore pipelineStore;
    private final DocumentResourceHelper documentResourceHelper;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;
    private final PipelineSerialiser pipelineSerialiser;
    private final ElementRegistryFactory elementRegistryFactory;
    private final SecurityContext securityContext;

    @Inject
    PipelineServiceImpl(final PipelineStore pipelineStoreProvider,
                        final DocumentResourceHelper documentResourceHelperProvider,
                        final PipelineStackLoader pipelineStackLoaderProvider,
                        final PipelineDataValidator pipelineDataValidatorProvider,
                        final PipelineSerialiser pipelineSerialiserProvider,
                        final ElementRegistryFactory elementRegistryFactoryProvider,
                        final SecurityContext securityContext) {
        this.pipelineStore = pipelineStoreProvider;
        this.documentResourceHelper = documentResourceHelperProvider;
        this.pipelineStackLoader = pipelineStackLoaderProvider;
        this.pipelineDataValidator = pipelineDataValidatorProvider;
        this.pipelineSerialiser = pipelineSerialiserProvider;
        this.elementRegistryFactory = elementRegistryFactoryProvider;
        this.securityContext = securityContext;
    }

    @Override
    public PipelineDoc fetch(final String uuid) {
        return documentResourceHelper.read(pipelineStore, getDocRef(uuid));
    }

    @Override
    public PipelineDoc update(final String uuid, final PipelineDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelper.update(pipelineStore, doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(PipelineDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    public Boolean savePipelineXml(final DocRef pipeline, final String xml) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipeline);

        if (pipelineDoc == null) {
            return false;
        }

        final PipelineData pipelineData = pipelineSerialiser.getPipelineDataFromXml(xml);
        pipelineDoc.setPipelineData(pipelineData);
        pipelineStore.writeDocument(pipelineDoc);

        return true;
    }

    @Override
    public String fetchPipelineXml(final DocRef pipeline) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipeline);
        if (pipelineDoc != null) {
            return pipelineSerialiser.getXmlFromPipelineData(pipelineDoc.getPipelineData());
        }

        return null;
    }

    @Override
    public List<PipelineData> fetchPipelineData(final DocRef pipeline) {
        return securityContext.secureResult(() -> {
            try {
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipeline);

                // A user should be allowed to read pipelines that they are inheriting from as
                // long as they have 'use' permission on them.
                return securityContext.useAsReadResult(() -> {
                    final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);
                    final List<PipelineData> result = new ArrayList<>(pipelines.size());

                    final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
                    for (final PipelineDoc pipe : pipelines) {
                        final PipelineData pipelineData = pipe.getPipelineData();

                        // Validate the pipeline data and add element and property type
                        // information.
                        pipelineDataValidator.validate(DocRefUtil.create(pipe), pipelineData, elementMap);
                        result.add(pipelineData);
                    }

                    return result;
                });
            } catch (final PermissionException e) {
                throw new PermissionException(
                        e.getUser(),
                        e.getMessage().replaceAll("permission to read", "permission to use"));
            }
        });
    }

    @Override
    public List<String> findUuidsByName(final String nameFilter) {
        return securityContext.secureResult(() ->
                pipelineStore.findByName(nameFilter, true, false)
                        .stream()
                        .map(DocRef::getUuid)
                        .collect(Collectors.toList()));
    }
}
