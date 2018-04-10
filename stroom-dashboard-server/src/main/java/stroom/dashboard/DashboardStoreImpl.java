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

package stroom.dashboard;

import stroom.dashboard.shared.DashboardDoc;
import stroom.docstore.Persistence;
import stroom.docstore.Serialiser2;
import stroom.docstore.Store;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.LegacyXMLSerialiser;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
class DashboardStoreImpl implements DashboardStore {
    private final Store<DashboardDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final Serialiser2<DashboardDoc> serialiser;

    @Inject
    DashboardStoreImpl(final Store<DashboardDoc> store, final SecurityContext securityContext, final Persistence persistence) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;

        serialiser = new DashboardSerialiser();

        store.setType(DashboardDoc.DOCUMENT_TYPE, DashboardDoc.class);
        store.setSerialiser(serialiser);
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
        return new DocumentType(7, DashboardDoc.DOCUMENT_TYPE, DashboardDoc.DOCUMENT_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DashboardDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DashboardDoc writeDocument(final DashboardDoc document) {
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
    public DocRef importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        // Convert legacy import format to the new format.
        final Map<String, byte[]> map = convert(docRef, dataMap, importState, importMode);
        if (map != null) {
            return store.importDocument(docRef, map, importState, importMode);
        }

        return docRef;
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    private Map<String, byte[]> convert(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = persistence.exists(docRef);
                if (importState.ok(importMode)) {
                    DashboardDoc document;
                    if (exists) {
                        document = read(uuid);

                    } else {
                        final OldDashboard oldDashboard = new OldDashboard();
                        final LegacyXMLSerialiser legacySerialiser = new LegacyXMLSerialiser();
                        legacySerialiser.performImport(oldDashboard, dataMap);

                        final long now = System.currentTimeMillis();
                        final String userId = securityContext.getUserId();

                        document = new DashboardDoc();
                        document.setType(docRef.getType());
                        document.setUuid(uuid);
                        document.setName(docRef.getName());
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTime(now);
                        document.setUpdateTime(now);
                        document.setCreateUser(userId);
                        document.setUpdateUser(userId);
                    }

                    result = serialiser.write(document);
                    if (dataMap.containsKey("data.xml")) {
                        result.put("xml", dataMap.remove("data.xml"));
                    }
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public String getDocType() {
        return DashboardDoc.DOCUMENT_TYPE;
    }

    @Override
    public DashboardDoc read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public DashboardDoc update(final DashboardDoc document) {
        return store.update(document);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
