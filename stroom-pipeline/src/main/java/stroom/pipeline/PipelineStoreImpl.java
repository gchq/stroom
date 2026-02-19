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
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocumentStore;
import stroom.docstore.api.DocumentStoreRegistry;
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
import stroom.util.shared.Document;
import stroom.util.shared.Embeddable;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Singleton
public class PipelineStoreImpl implements PipelineStore {

    private final Store<PipelineDoc> store;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<ProcessorService> processorServiceProvider;
    private final PipelineDataMigration pipelineDataMigration;
    private final Provider<DocumentStoreRegistry> documentStoreRegistryProvider;

    @Inject
    public PipelineStoreImpl(final StoreFactory storeFactory,
                             final PipelineSerialiser serialiser,
                             final Provider<ProcessorFilterService> processorFilterServiceProvider,
                             final Provider<ProcessorService> processorServiceProvider,
                             final PipelineDataMigration pipelineDataMigration,
                             final Provider<DocumentStoreRegistry> documentStoreRegistryProvider) {
        this.processorServiceProvider = processorServiceProvider;
        this.store = storeFactory.createStore(
                serialiser,
                PipelineDoc.TYPE,
                PipelineDoc::builder,
                PipelineDoc::copy);
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.pipelineDataMigration = pipelineDataMigration;
        this.documentStoreRegistryProvider = documentStoreRegistryProvider;
    }

    // ---------------------------------------------------------------------
    // START OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final DocumentStoreRegistry documentStoreRegistry = documentStoreRegistryProvider.get();
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DocRef newPipelineDocRef = store.copyDocument(docRef.getUuid(), newName);

        final PipelineDoc newPipelineDoc = readDocument(newPipelineDocRef);

        store.findDocRefsEmbeddedIn(docRef).forEach(d -> {
            // copy the embedded doc and set the new parent doc ref on it
            final DocumentStore<?> docStore = documentStoreRegistry.getDocumentStore(d.getType());
            final DocRef newChildDocRef = docStore.copyDocument(d, d.getName(), false, Set.of());
            writeEmbeddedIn(docStore, newChildDocRef, newPipelineDocRef);

            // remove the old child property and add the new child doc ref on the new pipeline doc property
            final List<PipelineProperty> properties = newPipelineDoc.getPipelineData().getProperties().getAdd();

            final PipelineProperty property = properties.stream().filter(p ->
                    p.getValue() != null && p.getValue().getEntity() != null &&
                    p.getValue().getEntity().getUuid().equals(d.getUuid())
            ).findFirst().orElse(null);

            if (property != null) {
                properties.remove(property);
                final PipelinePropertyValue newValue = new PipelinePropertyValue(newChildDocRef, true);
                properties.add(PipelineProperty.builder(property).value(newValue).build());
            }
        });

        store.writeDocument(newPipelineDoc);

        return newPipelineDocRef;
    }

    private <D extends Document> void writeEmbeddedIn(
            final DocumentStore<D> docStore, final DocRef docRef, final DocRef parentDocRef) {
        final D document = docStore.readDocument(docRef);
        if (document instanceof final Embeddable embeddable) {
            embeddable.setEmbeddedIn(parentDocRef);
            docStore.writeDocument(document);
        }
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

        // delete any embedded docs
        final PipelineDoc pipelineDoc = store.readDocument(docRef);
        NullSafe.consume(pipelineDoc.getPipelineData().getProperties(), properties ->
                properties.getAdd().stream()
                        .filter(p -> p.getValue() != null && p.getValue().getEntity() != null &&
                                p.getValue().isEmbedded())
                        .map(PipelineProperty::getValue)
                        .map(PipelinePropertyValue::getEntity)
                        .forEach(d -> {
                            final DocumentStoreRegistry documentStoreRegistry = documentStoreRegistryProvider.get();
                            final DocumentStore<?> docStore = documentStoreRegistry.getDocumentStore(d.getType());
                            docStore.deleteDocument(d);
                        })
        );

        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    // ---------------------------------------------------------------------
    // END OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

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

    private DependencyRemapFunction<PipelineDoc> createMapper() {
        return (doc, dependencyRemapper) -> {
            final PipelineDoc.Builder copy = doc.copy();
            if (doc.getParentPipeline() != null) {
                copy.parentPipeline(dependencyRemapper.remap(doc.getParentPipeline()));
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

                copy.pipelineData(builder.build());
            }
            return copy.build();
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
                    dest.add(PipelineProperty.builder(pipelineProperty)
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

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF DocumentActionHandler
    // ---------------------------------------------------------------------

    @Override
    public PipelineDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public PipelineDoc writeDocument(final PipelineDoc document) {
        final List<DocRef> docRefs = document.getPropertyDocRefs();

        // delete the embedded docs that are not currently in the pipeline
        store.findDocRefsEmbeddedIn(document.asDocRef()).stream()
                .filter(Predicate.not(docRefs::contains))
                .forEach(d -> {
                    final DocumentStoreRegistry documentStoreRegistry = documentStoreRegistryProvider.get();
                    final DocumentStore<?> docStore = documentStoreRegistry.getDocumentStore(d.getType());
                    docStore.deleteDocument(d);
                });

        return store.writeDocument(document);
    }

    // ---------------------------------------------------------------------
    // END OF DocumentActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

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
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        final Set<DocRef> docRefs = new HashSet<>();

        if (docRef != null && PipelineDoc.TYPE.equals(docRef.getType())) {
            final ResultPage<ProcessorFilter> filterResultPage = processorFilterServiceProvider.get().find(docRef);

            final List<DocRef> processorFilters = filterResultPage.getValues().stream()
                    .filter(ProcessorFilterUtil::shouldExport)
                    .map(v -> new DocRef(ProcessorFilter.ENTITY_TYPE, v.getUuid()))
                    .toList();

            docRefs.addAll(processorFilters);

            docRefs.addAll(store.findDocRefsEmbeddedIn(docRef));
        }

        return docRefs;
    }

    @Override
    public String getType() {
        return store.getType();
    }

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

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
