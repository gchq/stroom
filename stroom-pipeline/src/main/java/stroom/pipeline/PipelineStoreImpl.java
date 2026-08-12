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
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocumentStore;
import stroom.docstore.api.DocumentStoreRegistry;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.api.ImportExportDocument;
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
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Singleton
public class PipelineStoreImpl
        extends AbstractDocumentStore<PipelineDoc>
        implements PipelineStore {

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
        super(storeFactory,
                serialiser,
                PipelineDoc.TYPE,
                PipelineDoc::builder,
                PipelineDoc::copy);
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.processorServiceProvider = processorServiceProvider;
        this.pipelineDataMigration = pipelineDataMigration;
        this.documentStoreRegistryProvider = documentStoreRegistryProvider;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final DocumentStoreRegistry documentStoreRegistry = documentStoreRegistryProvider.get();
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DocRef newPipelineDocRef = getStore().copyDocument(docRef.getUuid(), newName);

        final PipelineDoc newPipelineDoc = readDocument(newPipelineDocRef);

        getStore().findDocRefsEmbeddedIn(docRef).forEach(d -> {
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

        getStore().writeDocument(newPipelineDoc);

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
    public void deleteDocument(final DocRef docRef) {
        // First we need to logically delete any child processors
        // which will in turn also logically delete any associated processor filters
        processorServiceProvider.get().deleteByPipelineUuid(docRef.getUuid());

        // delete any embedded docs
        final PipelineDoc pipelineDoc = getStore().readDocument(docRef);
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

        super.deleteDocument(docRef);
    }

    @Override
    protected DependencyRemapFunction<PipelineDoc> getDependencyRemapFunction() {
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

    @Override
    public PipelineDoc writeDocument(final PipelineDoc document) {
        final List<DocRef> docRefs = document.getPropertyDocRefs();

        // delete the embedded docs that are not currently in the pipeline
        getStore().findDocRefsEmbeddedIn(document.asDocRef()).stream()
                .filter(Predicate.not(docRefs::contains))
                .forEach(d -> {
                    final DocumentStoreRegistry documentStoreRegistry = documentStoreRegistryProvider.get();
                    final DocumentStore<?> docStore = documentStoreRegistry.getDocumentStore(d.getType());
                    docStore.deleteDocument(d);
                });

        return super.writeDocument(document);
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        // Migrate the data we are importing.
        pipelineDataMigration.migrate(importExportDocument);
        return getStore().importDocument(docRef, importExportDocument, importState, importSettings);
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

            docRefs.addAll(getStore().findDocRefsEmbeddedIn(docRef));
        }

        return docRefs;
    }
}
