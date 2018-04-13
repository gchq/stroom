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

package stroom.docstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.shared.Doc;
import stroom.entity.shared.PermissionException;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Store<D extends Doc> implements DocumentActionHandler<D> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Store.class);

    private final SecurityContext securityContext;
    private final Persistence persistence;

    private Serialiser2<D> serialiser;
    private String type;
    private Class<D> clazz;

    @Inject
    public Store(final Persistence persistence, final SecurityContext securityContext) {
        this.persistence = persistence;
        this.securityContext = securityContext;
    }

    public void setSerialiser(final Serialiser2<D> serialiser) {
        this.serialiser = serialiser;
    }

    public void setType(final String type, final Class<D> clazz) {
        this.type = type;
        this.clazz = clazz;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    public final DocRef createDocument(final String name) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = create(type, UUID.randomUUID().toString(), name);
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        final D created = create(document);
        return createDocRef(created);
    }

    public final DocRef copyDocument(final String originalUuid,
                                     final String copyUuid,
                                     final Map<String, String> otherCopiesByOriginalUuid) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(originalUuid);
        document.setType(type);
        document.setUuid(copyUuid);
        document.setName(document.getName());
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        final D created = create(document);
        return createDocRef(created);
    }

    public final DocRef moveDocument(final String uuid) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);

        document.setUpdateTime(now);
        document.setUpdateUser(userId);

        final D updated = update(document);
        return createDocRef(updated);
    }

    public DocRef renameDocument(final String uuid, final String name) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);

        document.setName(name);
//        document.setVersion(UUID.randomUUID().toString());
        document.setUpdateTime(now);
        document.setUpdateUser(userId);

        final D updated = update(document);
        return createDocRef(updated);
    }

    public final void deleteDocument(final String uuid) {
        // Check that the user has permission to delete this item.
        if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.DELETE)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to delete this item");
        }

        persistence.getLockFactory().lock(uuid, () -> persistence.delete(new DocRef(type, uuid)));
    }

    public DocRefInfo info(final String uuid) {
        final D document = read(uuid);
        return new DocRefInfo.Builder()
                .docRef(new DocRef.Builder()
                        .type(document.getType())
                        .uuid(document.getUuid())
                        .name(document.getName())
                        .build())
                .createTime(document.getCreateTime())
                .createUser(document.getCreateUser())
                .updateTime(document.getUpdateTime())
                .updateUser(document.getUpdateUser())
                .build();
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public D readDocument(final DocRef docRef) {
        return read(docRef.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Override
    public D writeDocument(final D document) {
        return update(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////


    public Set<DocRef> listDocuments() {
        final List<DocRef> list = list();
        return list.stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.READ) && securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT))
                .collect(Collectors.toSet());
    }

    public Map<DocRef, Set<DocRef>> getDependencies() {
        final List<DocRef> list = list();
        return list.stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.READ) && securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT))
                .map(d -> {
                    // We need to read the document to get the name.
                    DocRef docRef = null;
                    try {
                        final D doc = readDocument(d);
                        docRef = new DocRef(doc.getType(), doc.getUuid(), doc.getName());
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                    return Optional.ofNullable(docRef);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Function.identity(), d -> Collections.emptySet()));
    }

    public DocRef importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        final String uuid = docRef.getUuid();
        try {
            final boolean exists = persistence.exists(docRef);
            if (exists && !securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.UPDATE)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this document " + docRef);
            }

            if (importState.ok(importMode)) {
                persistence.getLockFactory().lock(uuid, () -> {
                    try {
                        persistence.write(docRef, exists, dataMap);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return docRef;
    }

    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        Map<String, byte[]> data = Collections.emptyMap();

        final String uuid = docRef.getUuid();

        try {
            // Check that the user has permission to read this item.
            if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.READ)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to read this document " + docRef);
            } else if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.EXPORT)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to export this document " + docRef);
            } else {
                D document = read(uuid);
                if (document == null) {
                    throw new IOException("Unable to read " + docRef);
                }

                if (omitAuditFields) {
                    document.setCreateTime(null);
                    document.setCreateUser(null);
                    document.setUpdateTime(null);
                    document.setUpdateUser(null);
                }

                data = serialiser.write(document);
            }
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR, e.getMessage()));
        }

        return data;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    private DocRef createDocRef(final D document) {
        if (document == null) {
            return null;
        }

        return new DocRef(type, document.getUuid(), document.getName());
    }

    private D create(final D document) {
        try {
            final DocRef docRef = createDocRef(document);
            final Map<String, byte[]> data = serialiser.write(document);
            persistence.getLockFactory().lock(document.getUuid(), () -> {
                try {
                    persistence.write(docRef, false, data);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return document;
    }

    private D create(final String type, final String uuid, final String name) {
        try {
            final D document = clazz.newInstance();
            document.setType(type);
            document.setUuid(uuid);
            document.setName(name);
            return document;
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private D read(final String uuid) {
        // Check that the user has permission to read this item.
        if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to read this document");
        }

        final Map<String, byte[]> data = persistence.getLockFactory().lockResult(uuid, () -> {
            try {
                return persistence.read(new DocRef(type, uuid));
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(e);
            }
        });

        try {
            return serialiser.read(data);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    private D update(final D document) {
        final DocRef docRef = createDocRef(document);

        // Check that the user has permission to update this item.
        if (!securityContext.hasDocumentPermission(type, document.getUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this document");
        }

        try {
            // Get the current document version to make sure the document hasn't been changed by somebody else since we last read it.
            final String currentVersion = document.getVersion();

            final long now = System.currentTimeMillis();
            final String userId = securityContext.getUserId();

            document.setVersion(UUID.randomUUID().toString());
            document.setUpdateTime(now);
            document.setUpdateUser(userId);

            final Map<String, byte[]> newData = serialiser.write(document);

            persistence.getLockFactory().lock(document.getUuid(), () -> {
                try {
                    // Read existing data for this document.
                    final Map<String, byte[]> data = persistence.read(docRef);

                    // Perform version check to ensure the item hasn't been updated by somebody else before we try to update it.
                    if (data == null) {
                        throw new RuntimeException("Document does not exist " + docRef);
                    }

                    final D existingDocument = serialiser.read(data);

                    // Perform version check to ensure the item hasn't been updated by somebody else before we try to update it.
                    if (!existingDocument.getVersion().equals(currentVersion)) {
                        throw new RuntimeException("Document has already been updated " + docRef);
                    }

                    persistence.write(docRef, true, newData);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return document;
    }

    public List<DocRef> list() {
        return persistence.list(type);
    }
}