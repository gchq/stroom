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

package stroom.dictionary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.JsonSerialiser;
import stroom.docstore.Persistence;
import stroom.docstore.RWLock;
import stroom.docstore.Serialiser;
import stroom.docstore.Store;
import stroom.entity.shared.PermissionException;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
class DictionaryStoreImpl implements DictionaryStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DictionaryStoreImpl.class);

    private final Store<DictionaryDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final Serialiser<DictionaryDoc> serialiser = new JsonSerialiser<>();

    @Inject
    DictionaryStoreImpl(final Store<DictionaryDoc> store, final SecurityContext securityContext, final Persistence persistence) {
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
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid,
                               final String parentFolderUUID) {
        final DocRef docRef = store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid, parentFolderUUID);

        final DictionaryDoc doc = read(docRef.getUuid());

        if (null != doc.getImports()) {
            final List<DocRef> replacedDocRefImports = doc.getImports().stream()
                    .map(docRefImport -> Optional.ofNullable(otherCopiesByOriginalUuid.get(docRefImport.getUuid())) // if there is a copy
                            .map(u -> new DocRef.Builder() // build a new Doc Ref
                                    .type(docRefImport.getType())
                                    .name(docRefImport.getName())
                                    .uuid(u)
                                    .build())
                            .orElse(docRefImport)) // otherwise just leave it as is
                    .collect(Collectors.toList());

            doc.setImports(replacedDocRefImports);
            writeDocument(doc);
        }

        return docRef;
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

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(9, DictionaryDoc.ENTITY_TYPE, DictionaryDoc.ENTITY_TYPE);
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
    public Map<DocRef, Set<DocRef>> getDependencies() {
        final List<DocRef> list = list();
        return list.stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.READ) && securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT))
                .map(d -> {
                    // We need to read the document to get the name and imports.
                    DictionaryDoc doc = null;
                    try {
                        doc = readDocument(d);
                    } catch (final Exception e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                    return Optional.ofNullable(doc);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(doc -> new DocRef(doc.getType(), doc.getUuid(), doc.getName()), doc -> {
                    try {
                        if (doc.getImports() != null && doc.getImports() != null) {
                            return Collections.unmodifiableSet(new HashSet<>(doc.getImports()));
                        }
                    } catch (final Exception e) {
                        LOGGER.debug(e.getMessage(), e);
                    }

                    return Collections.emptySet();
                }));
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
    public String getCombinedData(final DocRef docRef) {
        return doGetCombinedData(docRef, new HashSet<>());
    }

    private String doGetCombinedData(final DocRef docRef, final Set<DocRef> visited) {
        final DictionaryDoc doc = readDocument(docRef);
        if (doc != null && !visited.contains(docRef)) {
            // Prevent circular dependencies.
            visited.add(docRef);

            final StringBuilder sb = new StringBuilder();
            if (doc.getImports() != null) {
                for (final DocRef ref : doc.getImports()) {
                    final String data = doGetCombinedData(ref, visited);
                    if (data != null && data.length() > 0) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(data);
                    }
                }
            }
            if (doc.getData() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(doc.getData());
            }
            return sb.toString();
        }
        return null;
    }
}
