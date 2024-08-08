package stroom.documentation.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class DocumentationStoreImpl implements DocumentationStore {

    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            DocumentationDoc.DOCUMENT_TYPE,
            DocumentationDoc.DOCUMENT_TYPE,
            DocumentationDoc.ICON);
    private final Store<DocumentationDoc> store;

    @Inject
    public DocumentationStoreImpl(final StoreFactory storeFactory,
                                  final DocumentationSerialiser documentationSerialiser) {
        this.store = storeFactory.createStore(
                documentationSerialiser,
                DocumentationDoc.DOCUMENT_TYPE,
                DocumentationDoc.class);
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
        // Documentation has no deps forwards or backwards
        return Collections.emptyMap();
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        // Documentation has no deps forwards or backwards
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        // Documentation has no deps forwards or backwards
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocumentationDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DocumentationDoc writeDocument(final DocumentationDoc document) {
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
    public String getType() {
        return DocumentationDoc.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

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
