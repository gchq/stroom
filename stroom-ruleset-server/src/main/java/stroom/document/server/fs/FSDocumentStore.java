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

package stroom.document.server.fs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import stroom.document.server.DocumentActionHandler;
import stroom.document.shared.Document;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.Folder;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsUtil;
import stroom.util.task.ServerTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

public final class FSDocumentStore<D extends Document> implements ExplorerActionHandler, DocumentActionHandler {
    private static final String FILE_EXTENSION = ".json";

    private final Path dir;
    private final String type;
    private final SecurityContext securityContext;
    private final StripedLock stripedLock = new StripedLock();
    private final ObjectMapper mapper;
    private final Class<D> clazz;

    public FSDocumentStore(final Path dir, final String type, final Class<D> clazz, final SecurityContext securityContext) throws IOException {
        this.dir = dir;
        this.type = type;
        this.securityContext = securityContext;
        this.mapper = getMapper(true);
        this.clazz = clazz;

        Files.createDirectories(dir);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef create(final String parentFolderUUID, final String name) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = createDocument(type, UUID.randomUUID().toString(), name);
        document.setParentFolderUUID(parentFolderUUID);
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        final D created = create(parentFolderUUID, document);
        return createDocRef(created);
    }

    @Override
    public final DocRef copy(final String uuid, final String parentFolderUUID) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);
        document.setType(type);
        document.setUuid(UUID.randomUUID().toString());
        document.setParentFolderUUID(parentFolderUUID);
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
    public final DocRef move(final String uuid, final String parentFolderUUID) {
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        final D document = read(uuid);

        // If we are moving folder then make sure we are allowed to create items in the target folder.
        if (!EqualsUtil.isEquals(document.getParentFolderUUID(), parentFolderUUID)) {
            // Check that the user has permission to create this item.
            final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(type);
            if (!securityContext.hasDocumentPermission(Folder.ENTITY_TYPE, parentFolderUUID, permissionName)) {
                throw new RuntimeException("You are not authorised to create items in this folder");
            }
        }

        document.setParentFolderUUID(parentFolderUUID);
//        document.setVersion(UUID.randomUUID().toString());
        document.setUpdateTime(now);
        document.setUpdateUser(userId);

        final D updated = update(document);
        return createDocRef(updated);
    }

    @Override
    public DocRef rename(final String uuid, final String name) {
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
    public final void delete(final String uuid) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            // Check that the user has permission to delete this item.
            if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.DELETE)) {
                throw new RuntimeException("You are not authorised to delete this item");
            }

            final Path path = getPathForUUID(uuid);
            Files.delete(path);

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Object read(final DocRef docRef) {
        return read(docRef.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object write(final Object object) {
        if (!clazz.isAssignableFrom(object.getClass())) {
            throw new EntityServiceException("Unexpected document type");
        }

        final D document = (D) object;
        return update(document);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object fork(final Object object, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        if (!clazz.isAssignableFrom(object.getClass())) {
            throw new EntityServiceException("Unexpected document type");
        }

        String parentFolderUUID = null;
        if (destinationFolderRef != null) {
            parentFolderUUID = destinationFolderRef.getUuid();
        }

        final D document = (D) object;

        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();

        document.setUuid(UUID.randomUUID().toString());
        document.setName(docName);
        document.setParentFolderUUID(parentFolderUUID);
        document.setVersion(UUID.randomUUID().toString());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setCreateUser(userId);
        document.setUpdateUser(userId);

        return create(parentFolderUUID, document);

        // TODO : Call the explorer service to notify it that a new item has been created.
    }

    @Override
    public void delete(final DocRef docRef) {
        delete(docRef.getUuid());
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    private DocRef createDocRef(final D document) {
        if (document == null) {
            return null;
        }

        return new DocRef(type, document.getUuid(), document.getName());
    }

    private D create(final String parentFolderUUID, final D document) {
        try {
            // Check that the user has permission to create this item.
            final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(type);
            if (!securityContext.hasDocumentPermission(Folder.ENTITY_TYPE, parentFolderUUID, permissionName)) {
                throw new RuntimeException("You are not authorised to create documents of type '" + type + "' in this folder");
            }

            final Path filePath = getPathForUUID(document.getUuid());

            if (Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document already exists with uuid=" + document.getUuid());
            }

            mapper.writeValue(Files.newOutputStream(filePath), document);

            return document;

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private D createDocument(final String type, final String uuid, final String name) {
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
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            // Check that the user has permission to read this item.
            if (!securityContext.hasDocumentPermission(type, uuid, DocumentPermissionNames.READ)) {
                throw new RuntimeException("You are not authorised to read this document");
            }

            final Path path = getPathForUUID(uuid);
            return mapper.readValue(Files.newInputStream(path), clazz);

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public D update(final D document) {
        final Lock lock = stripedLock.getLockForKey(document.getUuid());
        lock.lock();
        try {
            // Check that the user has permission to read this item.
            if (!securityContext.hasDocumentPermission(type, document.getUuid(), DocumentPermissionNames.UPDATE)) {
                throw new RuntimeException("You are not authorised to update this document");
            }

            final Path path = getPathForUUID(document.getUuid());
            final D existingDocument = mapper.readValue(Files.newInputStream(path), clazz);

            // Perform version check to ensure the item hasn't been updated by somebody else before we try to update it.
            if (!existingDocument.getUuid().equals(document.getUuid())) {
                throw new RuntimeException("Document has already been updated");
            }

            final long now = System.currentTimeMillis();
            final String userId = securityContext.getUserId();

            document.setVersion(UUID.randomUUID().toString());
            document.setUpdateTime(now);
            document.setUpdateUser(userId);

            mapper.writeValue(Files.newOutputStream(path), document);

            return document;

        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public Set<D> list() {
        final Set<D> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        try (final Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(FILE_EXTENSION)).parallel().forEach(p -> {

                securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
                try {

                    final String fileName = p.getFileName().toString();
                    final int index = fileName.indexOf(".");
                    final String uuid = fileName.substring(0, index);
                    final D document = read(uuid);
                    set.add(document);

                } finally {
                    securityContext.popUser();
                }

            });
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return set;
    }

    private Path getPathForUUID(final String uuid) throws IOException {
        return dir.resolve(uuid + FILE_EXTENSION);
    }

    private ObjectMapper getMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
//        mapper.enableDefaultTyping();
        return mapper;
    }
}
