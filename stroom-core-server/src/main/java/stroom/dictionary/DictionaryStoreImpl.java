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
import stroom.db.migration.OldDictionaryDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.docstore.Persistence;
import stroom.docstore.Serialiser2;
import stroom.docstore.Store;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.docref.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
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
    private final Serialiser2<DictionaryDoc> serialiser;

    @Inject
    DictionaryStoreImpl(final Store<DictionaryDoc> store,
                        final SecurityContext securityContext,
                        final Persistence persistence) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;

        serialiser = new DictionarySerialiser();

        store.setType(DictionaryDoc.ENTITY_TYPE, DictionaryDoc.class);
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
        final DocRef docRef = store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid);

        final DictionaryDoc doc = readDocument(docRef);

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
                    } catch (final RuntimeException e) {
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
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }

                    return Collections.emptySet();
                }));
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

        try {
            if (!dataMap.containsKey("meta")) {
                // The latest version has a 'meta' file for the core details about the dictionary so convert this data.

                if (dataMap.containsKey("dat")) {
                    // Version 6.0 stored the whole dictionary in a single JSON file ending in 'dat' so convert this.
                    dataMap.put("meta", dataMap.remove("dat"));
                    final JsonSerialiser2<OldDictionaryDoc> oldSerialiser = new JsonSerialiser2<>(OldDictionaryDoc.class);
                    final OldDictionaryDoc oldDocument = oldSerialiser.read(dataMap);

                    final DictionaryDoc document = new DictionaryDoc();
                    document.setVersion(oldDocument.getVersion());
                    document.setCreateTime(oldDocument.getCreateTime());
                    document.setUpdateTime(oldDocument.getUpdateTime());
                    document.setCreateUser(oldDocument.getCreateUser());
                    document.setUpdateUser(oldDocument.getUpdateUser());
                    document.setType(oldDocument.getType());
                    document.setUuid(oldDocument.getUuid());
                    document.setName(oldDocument.getName());
                    document.setDescription(oldDocument.getDescription());
                    document.setImports(oldDocument.getImports());
                    document.setData(oldDocument.getData());

                    result = serialiser.write(document);
                } else {
                    // If we don't have a 'dat' file then this version is pre 6.0. We need to create the dictionary meta and put the data in the map.

                    final boolean exists = persistence.exists(docRef);
                    DictionaryDoc document;
                    if (exists) {
                        document = readDocument(docRef);

                    } else {
                        final long now = System.currentTimeMillis();
                        final String userId = securityContext.getUserId();

                        document = new DictionaryDoc();
                        document.setType(docRef.getType());
                        document.setUuid(docRef.getUuid());
                        document.setName(docRef.getName());
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTime(now);
                        document.setUpdateTime(now);
                        document.setCreateUser(userId);
                        document.setUpdateUser(userId);
                    }

                    if (dataMap.containsKey("data.xml")) {
                        document.setData(EncodingUtil.asString(dataMap.get("data.xml")));
                    }

                    result = serialiser.write(document);
                }
            }
        } catch (final IOException | RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            result = null;
        }

        return result;
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
                    if (data != null && !data.isEmpty()) {
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
