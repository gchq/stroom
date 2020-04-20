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
 *
 */

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.migration.LegacyXMLSerialiser;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class PipelineStoreImpl implements PipelineStore {
    private final Store<PipelineDoc> store;
    private final SecurityContext securityContext;
    private final PipelineSerialiser serialiser;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;

    @Inject
    public PipelineStoreImpl(final StoreFactory storeFactory,
                             final SecurityContext securityContext,
                             final PipelineSerialiser serialiser,
                             final Provider<ProcessorFilterService> processorFilterServiceProvider) {
        this.store = storeFactory.createStore(serialiser, PipelineDoc.DOCUMENT_TYPE, PipelineDoc.class);
        this.securityContext = securityContext;
        this.serialiser = serialiser;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid) {
        return store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid);
    }

    @Override
    public DocRef moveDocument(final String uuid) {
        return store.moveDocument(uuid);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    @Override
    public DocRefInfo info(String uuid) {
        return store.info(uuid);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(6, PipelineDoc.DOCUMENT_TYPE, PipelineDoc.DOCUMENT_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public PipelineDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public PipelineDoc writeDocument(final PipelineDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return Collections.emptyMap();
    }

    @Override
    public ImpexDetails importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        // Convert legacy import format to the new format.
        final Map<String, byte[]> map = convert(docRef, dataMap, importState, importMode);
        if (map != null) {
            return store.importDocument(docRef, map, importState, importMode);
        }

        return new ImpexDetails(docRef);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    private Map<String, byte[]> convert(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = store.exists(docRef);
                PipelineDoc document;
                if (exists) {
                    document = readDocument(docRef);

                } else {
                    final OldPipelineEntity oldPipeline = new OldPipelineEntity();
                    final LegacyXMLSerialiser legacySerialiser = new LegacyXMLSerialiser();
                    legacySerialiser.performImport(oldPipeline, dataMap);

                    final long now = System.currentTimeMillis();
                    final String userId = securityContext.getUserId();

                    document = new PipelineDoc();
                    document.setType(docRef.getType());
                    document.setUuid(uuid);
                    document.setName(docRef.getName());
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(now);
                    document.setUpdateTimeMs(now);
                    document.setCreateUser(userId);
                    document.setUpdateUser(userId);
                    document.setDescription(oldPipeline.getDescription());

                    final DocRef pipelineRef = serialiser.getDocRefFromLegacyXML(oldPipeline.getParentPipelineXML());
                    if (pipelineRef != null) {
                        document.setParentPipeline(pipelineRef);
                    }

                    final PipelineData pipelineData = serialiser.getPipelineDataFromXml(oldPipeline.getData());
                    document.setPipelineData(pipelineData);
                }

                if (dataMap.containsKey("data.xml")) {
                    final PipelineData pipelineData = serialiser.getPipelineDataFromXml(EncodingUtil.asString(dataMap.remove("data.xml")));
                    document.setPipelineData(pipelineData);
                }

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        Set <DocRef> processorFilters = new HashSet<DocRef>();

        if (docRef != null && PipelineDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
            ResultPage<ProcessorFilter> filterResultPage = processorFilterServiceProvider.get().find(docRef);

            List <DocRef> docRefs = filterResultPage.getValues().stream().map(v -> new DocRef(ProcessorFilter.ENTITY_TYPE, v.getUuid()))
                    .collect(Collectors.toList());

            processorFilters.addAll(docRefs);
        }
        return processorFilters;
    }


    @Override
    public String getType() {
        return PipelineDoc.DOCUMENT_TYPE;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> findByName(final String name) {
        return store.findByName(name);
    }

    @Override
    public PipelineDoc find(DocRef docRef) {

        return store.readDocument(docRef);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
