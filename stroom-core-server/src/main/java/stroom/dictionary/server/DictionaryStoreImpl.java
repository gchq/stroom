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

package stroom.dictionary.server;

import org.springframework.stereotype.Component;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Persistence;
import stroom.docstore.server.RWLock;
import stroom.docstore.server.Serialiser;
import stroom.docstore.server.Store;
import stroom.entity.shared.PermissionException;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.DocRefInfo;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Singleton
public class DictionaryStoreImpl implements DictionaryStore {
    private final Store<DictionaryDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final Serialiser<DictionaryDoc> serialiser = new JsonSerialiser<>();

    @Inject
    public DictionaryStoreImpl(final Store<DictionaryDoc> store, final SecurityContext securityContext, final Persistence persistence) throws IOException {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;
        store.setType(DictionaryDoc.ENTITY_TYPE, DictionaryDoc.class);
        store.setSerialiser(serialiser);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        return store.createDocument(name, parentFolderUUID);
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        return store.copyDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        return store.moveDocument(uuid, parentFolderUUID);
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

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DictionaryDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DictionaryDoc writeDocument(final DictionaryDoc document) {
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
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        if (!legacyImport(docRef, dataMap, importState, importMode)) {
            return store.importDocument(docRef, dataMap, importState, importMode);
        }

        return docRef;
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    private boolean legacyImport(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        if (dataMap.size() > 1 && !dataMap.containsKey("dat")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = persistence.exists(docRef);
                if (exists && !securityContext.hasDocumentPermission(docRef.getType(), uuid, DocumentPermissionNames.UPDATE)) {
                    throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this document " + docRef);
                }

                if (importState.ok(importMode)) {
                    DictionaryDoc document;
                    if (exists) {
                        document = read(uuid);

                    } else {
                        final long now = System.currentTimeMillis();
                        final String userId = securityContext.getUserId();

                        document = new DictionaryDoc();
                        document.setType(docRef.getType());
                        document.setUuid(uuid);
                        document.setName(docRef.getName());
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTime(now);
                        document.setUpdateTime(now);
                        document.setCreateUser(userId);
                        document.setUpdateUser(userId);
                    }

                    document.setData(dataMap.get("data.txt"));

                    try (final RWLock lock = persistence.getLockFactory().lock(uuid)) {
                        try (final OutputStream outputStream = persistence.getOutputStream(docRef, exists)) {
                            serialiser.write(outputStream, document);
                        }
                    }
                }

            } catch (final Exception e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
            }

            return true;
        }

        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public String getDocType() {
        return DictionaryDoc.ENTITY_TYPE;
    }

    @Override
    public DictionaryDoc read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public DictionaryDoc update(final DictionaryDoc dataReceiptPolicy) {
        return store.update(dataReceiptPolicy);
    }

    @Override
    public List<DocRef> findByName(final String name) {
        return store.list().stream().filter(docRef -> docRef.getName().equals(name)).collect(Collectors.toList());
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public Set<String> getWords(final DocRef docRef) {
        Set<String> words = Collections.emptySet();
        final DictionaryDoc doc = readDocument(docRef);
        if (doc != null && doc.getData() != null) {
            words = new HashSet<>(Arrays.asList(doc.getData().split("\n")));
        }
        return words;
    }
}
