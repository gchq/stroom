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

package stroom.docstore.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.document.server.DocumentActionHandler;
import stroom.docstore.shared.Document;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.PermissionException;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.server.ImportExportActionHandler;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Scope(StroomScope.PROTOTYPE)
public class Store<D extends Document> implements ExplorerActionHandler, DocumentActionHandler<D>, ImportExportActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Store.class);

    private static final String FOLDER = ExplorerConstants.FOLDER;
    private static final Charset CHARSET = Charset.forName("UTF-8");
    private static final String KEY = "dat";

    private final SecurityContext securityContext;
    private final Persistence persistence;

    private Serialiser<D> serialiser;
    private String type;
    private Class<D> clazz;

    @Inject
    public Store(final Persistence persistence, final SecurityContext securityContext) throws IOException {
        this.persistence = persistence;
        this.securityContext = securityContext;
    }

    public void setSerialiser(final Serialiser<D> serialiser) {
        this.serialiser = serialiser;
    }

    public void setType(final String type, final Class<D> clazz) {
        this.type = type;
        this.clazz = clazz;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef createDocument(final String name, final String parentFolderUUID) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = create(type, UUID.randomUUID().toString(), name);
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        final D created = create(parentFolderUUID, document);
        return createDocRef(created);
    }

    @Override
    public final DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);
        document.setType(type);
        document.setUuid(UUID.randomUUID().toString());
        document.setName("Copy of " + document.getName());
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        final D created = create(parentFolderUUID, document);
        return createDocRef(created);
    }

    @Override
    public final DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);

        // If we are moving folder then make sure we are allowed to create items in the target folder.
        final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(type);
        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, permissionName)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to create items in this folder");
        }

        document.setUpdateTime(now);
        document.setUpdateUser(userId);

        final D updated = update(document);
        return createDocRef(updated);
    }

    @Override
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

    @Override
    public final void deleteDocument(final String uuid) {
        // Check that the user has permission to delete this item.
        if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.DELETE)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to delete this item");
        }

        try (final RWLock lock = persistence.getLockFactory().lock(uuid)) {
            persistence.delete(new DocRef(type, uuid));
        }
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

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        final String uuid = docRef.getUuid();
        try {
            final boolean exists = persistence.exists(docRef);
            if (exists && !securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.UPDATE)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this document " + docRef);
            }

            if (importState.ok(importMode)) {
                final byte[] data = dataMap.get(KEY).getBytes(CHARSET);

                try (final RWLock lock = persistence.getLockFactory().lock(uuid)) {
                    try (final OutputStream outputStream = persistence.getOutputStream(docRef, exists)) {
                        outputStream.write(data);
                    }
                }
            }

        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return docRef;
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        Map<String, String> data = Collections.emptyMap();

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

                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                serialiser.write(byteArrayOutputStream, document);

                data = new HashMap<>();
                data.put(KEY, new String(byteArrayOutputStream.toByteArray(), CHARSET));
            }
        } catch (final Exception e) {
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

    private D create(final String parentFolderUUID, final D document) {
        final DocRef docRef = createDocRef(document);
        try {
            // Check that the user has permission to create this item.
            final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(type);
            if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, permissionName)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to create documents of type '" + type + "' in this folder");
            }

            try (final RWLock lock = persistence.getLockFactory().lock(document.getUuid())) {
                serialiser.write(persistence.getOutputStream(docRef, false), document);
            }

            return document;

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
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

    public D read(final String uuid) {
        try (final RWLock lock = persistence.getLockFactory().lock(uuid)) {
            // Check that the user has permission to read this item.
            if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.READ)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to read this document");
            }

            final InputStream inputStream = persistence.getInputStream(new DocRef(type, uuid));
            return serialiser.read(inputStream, clazz);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public D update(final D document) {
        final DocRef docRef = createDocRef(document);
        try (final RWLock lock = persistence.getLockFactory().lock(document.getUuid())) {
            // Check that the user has permission to update this item.
            if (!securityContext.hasDocumentPermission(type, document.getUuid(), DocumentPermissionNames.UPDATE)) {
                throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this document");
            }

            try (final InputStream inputStream = persistence.getInputStream(docRef)) {
                final D existingDocument = serialiser.read(inputStream, clazz);

                // Perform version check to ensure the item hasn't been updated by somebody else before we try to update it.
                if (!existingDocument.getVersion().equals(document.getVersion())) {
                    throw new RuntimeException("Document has already been updated");
                }

                final long now = System.currentTimeMillis();
                final String userId = securityContext.getUserId();

                document.setVersion(UUID.randomUUID().toString());
                document.setUpdateTime(now);
                document.setUpdateUser(userId);

                try (final OutputStream outputStream = persistence.getOutputStream(docRef, true)) {
                    serialiser.write(outputStream, document);
                }

                return document;
            }

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public Set<DocRef> list() {
        return persistence.list(type);
    }
}