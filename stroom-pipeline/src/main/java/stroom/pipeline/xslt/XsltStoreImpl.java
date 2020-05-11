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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.migration.LegacyXMLSerialiser;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
class XsltStoreImpl implements XsltStore {
    private final Store<XsltDoc> store;
    private final SecurityContext securityContext;
    private final XsltSerialiser serialiser;

    @Inject
    XsltStoreImpl(final StoreFactory storeFactory,
                  final SecurityContext securityContext,
                  final XsltSerialiser serialiser) {
        this.store = storeFactory.createStore(serialiser, XsltDoc.DOCUMENT_TYPE, XsltDoc.class);
        this.securityContext = securityContext;
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
    public DocRef copyDocument(final DocRef docRef, final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(docRef.getName(), existingNames);
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
        return new DocumentType(5, XsltDoc.DOCUMENT_TYPE, XsltDoc.DOCUMENT_TYPE);
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
    public XsltDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public XsltDoc writeDocument(final XsltDoc document) {
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
        if (!dataMap.containsKey("meta")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = store.exists(docRef);
                XsltDoc document;
                if (exists) {
                    document = readDocument(docRef);

                } else {
                    final OldXslt oldXslt = new OldXslt();
                    final LegacyXMLSerialiser legacySerialiser = new LegacyXMLSerialiser();
                    legacySerialiser.performImport(oldXslt, dataMap);

                    final long now = System.currentTimeMillis();
                    final String userId = securityContext.getUserId();

                    document = new XsltDoc();
                    document.setType(docRef.getType());
                    document.setUuid(uuid);
                    document.setName(docRef.getName());
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(now);
                    document.setUpdateTimeMs(now);
                    document.setCreateUser(userId);
                    document.setUpdateUser(userId);

                    document.setDescription(oldXslt.getDescription());
                }

                result = serialiser.write(document);
                if (dataMap.containsKey("data.xsl")) {
                    result.put("xsl", dataMap.remove("data.xsl"));
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        return null;
    }

    @Override
    public String getType() {
        return XsltDoc.DOCUMENT_TYPE;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> findByName(final String name) {
        return store.findByName(name);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
