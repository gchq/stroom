/*
 * Copyright 2024 Crown Copyright
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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.LuceneIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class IndexStoreImpl implements IndexStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexStoreImpl.class);

    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.INDEXING,
            LuceneIndexDoc.DOCUMENT_TYPE,
            "Lucene Index",
            LuceneIndexDoc.ICON);
    private final Store<LuceneIndexDoc> store;
    private final Provider<IndexFieldService> indexFieldServiceProvider;
    private final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider;
    private final IndexSerialiser serialiser;

    @Inject
    IndexStoreImpl(final StoreFactory storeFactory,
                   final IndexSerialiser serialiser,
                   final Provider<IndexFieldService> indexFieldServiceProvider,
                   final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider) {
        this.indexVolumeGroupServiceProvider = indexVolumeGroupServiceProvider;
        this.store = storeFactory.createStore(serialiser, LuceneIndexDoc.DOCUMENT_TYPE, LuceneIndexDoc.class);
        this.indexFieldServiceProvider = indexFieldServiceProvider;
        this.serialiser = serialiser;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

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
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(DocRef docRef) {
        return store.info(docRef);
    }

    @Override
    public DocumentType getDocumentType() {
        return DOCUMENT_TYPE;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(null);
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, null);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, null);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public LuceneIndexDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public LuceneIndexDoc writeDocument(final LuceneIndexDoc document) {
        final LuceneIndexDoc luceneIndexDoc = store.writeDocument(document);
        if (document != null) {
            indexFieldServiceProvider.get().transferFieldsToDB(document.asDocRef());
        }
        return luceneIndexDoc;
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

        // If the imported feed's vol grp doesn't exist in this env use our default
        // or null it out
        Map<String, byte[]> effectiveDataMap = dataMap;
        try {
            final LuceneIndexDoc doc = serialiser.read(dataMap);

            final String volumeGroup = doc.getVolumeGroupName();
            if (volumeGroup != null) {
                final IndexVolumeGroupService fsVolumeGroupService = indexVolumeGroupServiceProvider.get();
                final List<String> allVolumeGroups = fsVolumeGroupService.getNames();
                if (!allVolumeGroups.contains(volumeGroup)) {
                    LOGGER.debug("Volume group '{}' in imported index {} is not a valid volume group",
                            volumeGroup, docRef);
                    fsVolumeGroupService.getDefaultVolumeGroup()
                            .ifPresentOrElse(
                                    doc::setVolumeGroupName,
                                    () -> doc.setVolumeGroupName(null));

                    effectiveDataMap = serialiser.write(doc);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising feed {}: {}",
                    docRef, e.getMessage()), e);
        }

        final DocRef ref = store.importDocument(docRef, effectiveDataMap, importState, importSettings);
        indexFieldServiceProvider.get().transferFieldsToDB(ref);
        return ref;
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
    public String getType() {
        return LuceneIndexDoc.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> names,
                                    final boolean allowWildCards,
                                    final boolean isCaseSensitive) {
        return store.findByNames(names, allowWildCards, isCaseSensitive);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
