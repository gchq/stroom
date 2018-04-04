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

package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.JsonSerialiser;
import stroom.docstore.Persistence;
import stroom.docstore.Serialiser;
import stroom.docstore.Store;
import stroom.entity.shared.PermissionException;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
class TextConverterStoreImpl implements TextConverterStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextConverterStoreImpl.class);

    private final Store<TextConverterDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final Serialiser<TextConverterDoc> serialiser = new JsonSerialiser<>();

    @Inject
    TextConverterStoreImpl(final Store<TextConverterDoc> store, final SecurityContext securityContext, final Persistence persistence) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;
        store.setType(TextConverterDoc.ENTITY_TYPE, TextConverterDoc.class);
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
        return new DocumentType(9, TextConverterDoc.ENTITY_TYPE, TextConverterDoc.ENTITY_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public TextConverterDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public TextConverterDoc writeDocument(final TextConverterDoc document) {
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
                    TextConverterDoc document;
                    if (exists) {
                        document = read(uuid);

                    } else {
                        final long now = System.currentTimeMillis();
                        final String userId = securityContext.getUserId();

                        document = new TextConverterDoc();
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

                    persistence.getLockFactory().lock(uuid, () -> {
                        try (final OutputStream outputStream = persistence.getOutputStream(docRef, exists)) {
                            serialiser.write(outputStream, document);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }

            } catch (final RuntimeException e) {
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
        return TextConverterDoc.ENTITY_TYPE;
    }

    @Override
    public TextConverterDoc read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public TextConverterDoc update(final TextConverterDoc dataReceiptPolicy) {
        return store.update(dataReceiptPolicy);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
