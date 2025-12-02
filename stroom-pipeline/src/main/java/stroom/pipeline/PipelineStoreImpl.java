/*
 * Copyright 2017-2024 Crown Copyright
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
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.legacy.PipelineDataMigration;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorFilterUtil;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Singleton
public class PipelineStoreImpl implements PipelineStore {

    private final Store<PipelineDoc> store;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<ProcessorService> processorServiceProvider;
    private final PipelineDataMigration pipelineDataMigration;

    @Inject
    public PipelineStoreImpl(final StoreFactory storeFactory,
                             final PipelineSerialiser serialiser,
                             final Provider<ProcessorFilterService> processorFilterServiceProvider,
                             final Provider<ProcessorService> processorServiceProvider,
                             final PipelineDataMigration pipelineDataMigration) {
        this.processorServiceProvider = processorServiceProvider;
        this.store = storeFactory.createStore(serialiser, PipelineDoc.TYPE, PipelineDoc::builder);
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.pipelineDataMigration = pipelineDataMigration;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    /// /////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        // First we need to logically delete any child processors
        // which will in turn also logically delete any associated processor filters
        processorServiceProvider.get().deleteByPipelineUuid(docRef.getUuid());

        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(createMapper());
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, createMapper());
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, createMapper());
    }

    private BiConsumer<PipelineDoc, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            if (doc.getParentPipeline() != null) {
                doc.setParentPipeline(dependencyRemapper.remap(doc.getParentPipeline()));
            }

            final PipelineData pipelineData = doc.getPipelineData();
            if (pipelineData != null) {
                final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);
                builder.getProperties().getAddList().clear();
                remapPipelineProperties(
                        pipelineData.getAddedProperties(),
                        builder.getProperties().getAddList(), dependencyRemapper);
                builder.getProperties().getRemoveList().clear();
                remapPipelineProperties(
                        pipelineData.getRemovedProperties(),
                        builder.getProperties().getRemoveList(), dependencyRemapper);

                builder.getReferences().getAddList().clear();
                remapPipelineReferences(
                        pipelineData.getAddedPipelineReferences(),
                        builder.getReferences().getAddList(), dependencyRemapper);
                builder.getReferences().getRemoveList().clear();
                remapPipelineReferences(
                        pipelineData.getRemovedPipelineReferences(),
                        builder.getReferences().getRemoveList(), dependencyRemapper);

                doc.setPipelineData(builder.build());
            }
        };
    }

    public void remapPipelineProperties(final List<PipelineProperty> pipelineProperties,
                                        final List<PipelineProperty> dest,
                                        final DependencyRemapper dependencyRemapper) {
        if (pipelineProperties != null) {
            pipelineProperties.forEach(pipelineProperty -> {
                if (pipelineProperty.getValue() == null || pipelineProperty.getValue().getEntity() == null) {
                    dest.add(pipelineProperty);
                } else {
                    dest.add(new PipelineProperty.Builder(pipelineProperty)
                            .value(new PipelinePropertyValue(dependencyRemapper
                                    .remap(pipelineProperty.getValue().getEntity())))
                            .build());
                }
            });
        }
    }

    public void remapPipelineReferences(final List<PipelineReference> pipelineReferences,
                                        final List<PipelineReference> dest,
                                        final DependencyRemapper dependencyRemapper) {
        if (pipelineReferences != null) {
            pipelineReferences.forEach(pipelineReference ->
                    dest.add(new PipelineReference.Builder(pipelineReference)
                            .feed(dependencyRemapper.remap(pipelineReference.getFeed()))
                            .pipeline(dependencyRemapper.remap(pipelineReference.getPipeline()))
                            .build()));
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
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
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        // Migrate the data we are importing.
        pipelineDataMigration.migrate(dataMap);
        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        final Set<DocRef> processorFilters = new HashSet<>();

        if (docRef != null && PipelineDoc.TYPE.equals(docRef.getType())) {
            final ResultPage<ProcessorFilter> filterResultPage = processorFilterServiceProvider.get().find(docRef);

            final List<DocRef> docRefs = filterResultPage.getValues().stream()
                    .filter(ProcessorFilterUtil::shouldExport)
                    .map(v -> new DocRef(ProcessorFilter.ENTITY_TYPE, v.getUuid()))
                    .toList();

            processorFilters.addAll(docRefs);
        }
        return processorFilters;
    }

    @Override
    public String getType() {
        return store.getType();
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
