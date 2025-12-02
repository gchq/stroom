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

package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Store;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.docstore.shared.DocRefUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.AuditUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StoreImpl<D extends AbstractDoc> implements Store<D> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreImpl.class);

    private final Persistence persistence;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;
    private final Provider<DocRefDecorator> docRefInfoServiceProvider;

    private final DocumentSerialiser2<D> serialiser;
    private final String type;
    private final Supplier<AbstractBuilder<D, ?>> builderSupplier;

    @Inject
    StoreImpl(final Persistence persistence,
              final EntityEventBus entityEventBus,
              final SecurityContext securityContext,
              final Provider<DocRefDecorator> docRefInfoServiceProvider,
              final DocumentSerialiser2<D> serialiser,
              final String type,
              final Supplier<AbstractBuilder<D, ?>> builderSupplier) {
        this.persistence = persistence;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.serialiser = serialiser;
        this.type = type;
        this.builderSupplier = builderSupplier;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler

    /// /////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef createDocument(final String name) {
        Objects.requireNonNull(name);

        // Get a doc builder.
        final AbstractBuilder<D, ?> builder = builderSupplier.get();

        // Add audit data.
        stampAuditData(builder);

        final D document = builder
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .version(UUID.randomUUID().toString())
                .build();

        final D created = create(document);
        return createDocRef(created);
    }

    @Override
    public final DocRef createDocument(final String name, final DocumentCreator<D> documentCreator) {
        Objects.requireNonNull(name);
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserIdentityForAudit();

        final D document = documentCreator.create(
                UUID.randomUUID().toString(),
                name,
                UUID.randomUUID().toString(),
                now,
                now,
                userId,
                userId);

        final D created = create(document);
        return createDocRef(created);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String newName) {
        Objects.requireNonNull(originalUuid);
        Objects.requireNonNull(newName);

        final D document = read(originalUuid);
        document.setUuid(UUID.randomUUID().toString());
        document.setName(newName);
        document.setVersion(UUID.randomUUID().toString());

        // Add audit data.
        stampAuditData(document);

        final D created = create(document);
        return createDocRef(created);
    }

    @Override
    public final DocRef moveDocument(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        final D document = read(docRef);

//        // If we are moving folder then make sure we are allowed to create items in the target folder.
//        final String permissionName = DocumentPermissionNames.getDocumentCreatePermission(type);
//        if (!securityContext.hasDocumentPermission(FOLDER, parentFolderUUID, permissionName)) {
//            throw new PermissionException(
//            securityContext.getUserId(), "You are not authorised to create items in this folder");
//        }

        // No need to save as the document has not been changed only moved.
        return createDocRef(document);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        Objects.requireNonNull(docRef);
        Objects.requireNonNull(name);
        final D document = read(docRef);

        final DocRef oldDocRef = createDocRef(document);

        // Only update the document if the name has actually changed.
        if (!Objects.equals(document.getName(), name)) {
            document.setName(name);
            final D updated = update(document, oldDocRef);
            return createDocRef(updated);
        }

        return createDocRef(document);
    }

    @Override
    public final void deleteDocument(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        // Check that the user has permission to delete this item.
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            throwPermissionException(
                    "You are not authorised to delete this item",
                    () -> "document: " + toDocRefDisplayString(docRef));
        }

        persistence.getLockFactory().lock(docRef.getUuid(), () -> {
            persistence.delete(docRef);
            EntityEvent.fire(entityEventBus, docRef, EntityAction.DELETE);
        });
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        final D document = read(docRef);
        return DocRefInfo
                .builder()
                .docRef(DocRef.builder()
                        .type(document.getType())
                        .uuid(document.getUuid())
                        .name(document.getName())
                        .build())
                .createTime(document.getCreateTimeMs())
                .createUser(document.getCreateUser())
                .updateTime(document.getUpdateTimeMs())
                .updateUser(document.getUpdateUser())
                .build();
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies

    /// /////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies(final BiConsumer<D, DependencyRemapper> mapper) {
        return list()
                .stream()
                .filter(this::canRead)
                .collect(Collectors.toMap(docRef -> docRef, docRef ->
                        getDependencies(docRef, mapper)));
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef,
                                       final BiConsumer<D, DependencyRemapper> mapper) {
        if (mapper != null) {
            try {
                final D doc = readDocument(docRef);
                if (doc != null) {
                    final DependencyRemapper dependencyRemapper = new DependencyRemapper();
                    mapper.accept(doc, dependencyRemapper);
                    return dependencyRemapper.getDependencies();
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings,
                                  final BiConsumer<D, DependencyRemapper> mapper) {
        if (mapper != null) {
            try {
                final D doc = readDocument(docRef);
                if (doc != null) {
                    final DependencyRemapper dependencyRemapper = new DependencyRemapper(remappings);
                    mapper.accept(doc, dependencyRemapper);
                    if (dependencyRemapper.isChanged()) {
                        writeDocument(doc);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler

    /// /////////////////////////////////////////////////////////////////////

    @Override
    public D readDocument(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        return read(docRef.getUuid());
    }

    @Override
    public D writeDocument(final D document) {
        Objects.requireNonNull(document);
        return update(document);
    }

    @Override
    public String getType() {
        return type;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler

    /// /////////////////////////////////////////////////////////////////////


    @Override
    public boolean exists(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        return persistence.exists(docRef);
    }

    @Override
    public Set<DocRef> listDocuments() {
        final List<DocRef> list = list();
        return list.stream()
                .filter(this::canRead)
                .collect(Collectors.toSet());
    }

    private boolean canRead(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
    }

    @Override
    public DocRef importDocument(DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        if (dataMap != null) {
            Objects.requireNonNull(docRef);
            final String uuid = docRef.getUuid();
            try {
                // See if this document already exists and try and read it.
                final D existingDocument = getExistingDocument(docRef);

                if (ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
                    // See if the new document is the same as the old one.
                    if (existingDocument == null) {
                        importState.setState(State.NEW);

                    } else {
                        docRef = docRef.copy().name(existingDocument.getName()).build();
                        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
                            throwPermissionException(
                                    "You are not authorised to update " + toDocRefDisplayString(docRef));
                        }

                        final List<String> updatedFields = importState.getUpdatedFieldList();
                        checkForUpdatedFields(
                                existingDocument,
                                dataMap,
                                new AuditFieldFilter<>(),
                                updatedFields);
                        if (updatedFields.isEmpty()) {
                            importState.setState(State.EQUAL);
                        }
                    }

                } else if (ImportSettings.ok(importSettings, importState)) {
                    if (existingDocument != null) {
                        docRef = docRef.copy().name(existingDocument.getName()).build();
                        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
                            throwPermissionException(
                                    "You are not authorised to update " + toDocRefDisplayString(docRef));
                        }
                    }

                    importDocument(docRef, existingDocument, uuid, dataMap);
                }

            } catch (final RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
            }
        }

        return docRef;
    }

    private void importDocument(final DocRef docRef,
                                final D existingDocument,
                                final String uuid,
                                final Map<String, byte[]> convertedDataMap) {
        persistence.getLockFactory().lock(uuid, () -> {
            try {
                // Turn the data map into a document.
                final D newDocument = serialiser.read(convertedDataMap);
                // Copy create time and user from the existing document.
                if (existingDocument != null) {
                    newDocument.setName(existingDocument.getName());
                    newDocument.setCreateTimeMs(existingDocument.getCreateTimeMs());
                    newDocument.setCreateUser(existingDocument.getCreateUser());
                }
                // Stamp audit data on the imported document.
                stampAuditData(newDocument);
                // Convert the document back into a data map.
                final Map<String, byte[]> finalData = serialiser.write(newDocument);
                // Write the data.
                persistence.write(docRef, existingDocument != null, finalData);

                // Fire an entity event to alert other services of the change.
                if (existingDocument != null) {
                    EntityEvent.fire(entityEventBus, docRef, EntityAction.UPDATE);
                } else {
                    EntityEvent.fire(entityEventBus, docRef, EntityAction.CREATE);
                }

            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        });
    }

    private D getExistingDocument(final DocRef docRef) {
        try {
            if (!exists(docRef)) {
                return null;
            } else {
                return readDocument(docRef);
            }
        } catch (final PermissionException e) {
            throwPermissionException("The document being imported exists but you are not authorised to read "
                                     + toDocRefDisplayString(docRef));
        } catch (final RuntimeException e) {
            // Ignore.
            LOGGER.debug(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final List<Message> messageList,
                                              final Function<D, D> filter) {
        Map<String, byte[]> data = Collections.emptyMap();

        try {
            // Check that the user has permission to read this item.
            if (!canRead(docRef)) {
                throwPermissionException("You are not authorised to read " + toDocRefDisplayString(docRef));
            } else {
                D document = read(docRef);
                if (document == null) {
                    throw new IOException("Unable to read " + toDocRefDisplayString(docRef));
                }
                document = filter.apply(document);
                data = serialiser.write(document);
            }
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR, e.getMessage()));
        }

        return data;
    }

    private void checkForUpdatedFields(final D existingDoc,
                                       final Map<String, byte[]> dataMap,
                                       final Function<D, D> filter,
                                       final List<String> updatedFieldList) {
        try {
            final D newDoc = serialiser.read(dataMap);
            final D existingDocument = filter.apply(existingDoc);
            final D newDocument = filter.apply(newDoc);

            try {
                final Method[] methods = existingDocument.getClass().getMethods();
                for (final Method method : methods) {
                    String field = method.getName();
                    if (field.length() > 4 && field.startsWith("get") && method.getParameterTypes().length == 0) {
                        final Object existingObject = method.invoke(existingDocument);
                        final Object newObject = method.invoke(newDocument);
                        if (!Objects.equals(existingObject, newObject)) {
                            field = field.substring(3);
                            field = field.substring(0, 1).toLowerCase() + field.substring(1);

                            updatedFieldList.add(field);
                        }
                    }
                }
            } catch (final InvocationTargetException | IllegalAccessException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler

    /// /////////////////////////////////////////////////////////////////////

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
                    EntityEvent.fire(entityEventBus, docRef, EntityAction.CREATE);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error("Error serialising {}", document.getType(), e);
            throw new UncheckedIOException(e);
        }

        return document;
    }

    private D read(final String uuid) {
        return read(new DocRef(type, uuid));
    }

    private D read(final DocRef docRef) {
        final String uuid = NullSafe.requireNonNull(docRef, DocRef::getUuid, () -> "UUID required");
        checkType(docRef);
        // Check that the user has permission to read this item.
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            throwPermissionException(LogUtil.message("You are not authorised to read {}",
                    toDocRefDisplayString(docRef)));
        }

        final Map<String, byte[]> data = readPersistence(docRef);
        if (data != null) {
            try {
                return serialiser.read(data);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(
                        LogUtil.message("Error deserialising {} from store {}, {}",
                                new DocRef(type, uuid),
                                persistence.getClass().getSimpleName(),
                                e.getMessage()), e);
            }
        } else {
            throw new DocumentNotFoundException(new DocRef(type, uuid));
        }
    }

    private void throwPermissionException(final String msg) {
        throwPermissionException(msg, null);
    }

    private void throwPermissionException(final String msg,
                                          final Supplier<String> additionalDebugMsgSupplier)
            throws PermissionException {

        // The exception messages are purposefully vague so add some debug, so if it is a recurring problem
        // we can find out the who and what.
        LOGGER.debug(() -> LogUtil.message("Throwing PermissionException '{}', userIdentity: {}. {}",
                msg,
                securityContext.getUserIdentity(),
                NullSafe.getOrElse(additionalDebugMsgSupplier, Supplier::get, "")));

        throw new PermissionException(securityContext.getUserRef(), msg);
    }

    private void checkType(final DocRef docRef) {
        try {
            Objects.requireNonNull(docRef);
            Objects.requireNonNull(docRef.getType());
            if (!Objects.equals(type, docRef.getType())) {
                throw new RuntimeException(LogUtil.message(
                        "Invalid docRef type, found: '{}', expecting: '{}'",
                        docRef.getType(), type));
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private D update(final D document) {
        return update(document, null);
    }

    private D update(final D document, final DocRef oldDocRef) {
        final DocRef docRef = createDocRef(document);

        // Check that the user has permission to update this item.
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throwPermissionException("You are not authorised to update " + toDocRefDisplayString(docRef));
        }

        try {
            // Get the current document version to make sure the document hasn't been changed by
            // somebody else since we last read it.
            final String currentVersion = document.getVersion();
            document.setVersion(UUID.randomUUID().toString());

            // Add audit data.
            stampAuditData(document);

            final Map<String, byte[]> newData = serialiser.write(document);

            persistence.getLockFactory().lock(document.getUuid(), () -> {
                try {
                    // Read existing data for this document.
                    final Map<String, byte[]> data = persistence.read(docRef);

                    // Perform version check to ensure the item hasn't been updated by somebody
                    // else before we try to update it.
                    if (data == null) {
                        throw new DocumentNotFoundException(docRef);
                    }

                    final D existingDocument = serialiser.read(data);

                    // Perform version check to ensure the item hasn't been updated by somebody
                    // else before we try to update it.
                    if (!existingDocument.getVersion().equals(currentVersion)) {
                        throw new RuntimeException(toDocRefDisplayString(docRef)
                                                   + " has already been updated.");
                    }

                    persistence.write(docRef, true, newData);
                    EntityEvent.fire(entityEventBus, docRef, oldDocRef, EntityAction.UPDATE);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return document;
    }

    @Override
    public List<DocRef> list() {
        return persistence
                .list(type)
                .stream()
                .filter(this::canRead)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
        return persistence.find(type, names, allowWildCards)
                .stream()
                .filter(this::canRead)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        if (!canRead(docRef)) {
            return Collections.emptyMap();
        }

        final Map<String, byte[]> data = readPersistence(docRef);
        if (data == null) {
            return Collections.emptyMap();
        }

        return data
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new String(e.getValue(), StandardCharsets.UTF_8)));
    }

    private Map<String, byte[]> readPersistence(final DocRef docRef) {
        return persistence.getLockFactory().lockResult(docRef.getUuid(), () -> {
            try {
                return persistence.read(docRef);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(
                        LogUtil.message("Error reading {} from store {}, {}",
                                toDocRefDisplayString(docRef),
                                persistence.getClass().getSimpleName(),
                                e.getMessage()), e);
            }
        });
    }

    @Deprecated // remove once pipelines have been migrated.
    public void migratePipelines(final Function<Map<String, byte[]>, Optional<Map<String, byte[]>>> function) {
        persistence.list(type).forEach(docRef ->
                persistence.getLockFactory().lock(docRef.getUuid(), () -> {
                    final Map<String, byte[]> data = readPersistence(docRef);
                    if (data != null) {
                        final Optional<Map<String, byte[]>> migrated = function.apply(data);
                        migrated.ifPresent(newData -> {
                            try {
                                persistence.write(docRef, true, newData);
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });
                    }
                }));
    }

    private String toDocRefDisplayString(final DocRef docRef) {
        if (docRef == null || !NullSafe.isBlankString(docRef.getName())) {
            return "";
        } else {
            try {
                return DocRefUtil.createTypedDocRefString(docRefInfoServiceProvider.get()
                        .decorate(docRef));
            } catch (final Exception e) {
                // This method is for use in decorating the docref for exception messages
                // so swallow any errors.
                LOGGER.debug("Error decorating docRef {}: {}",
                        docRef, LogUtil.exceptionMessage(e), e);
                try {
                    return DocRefUtil.createTypedDocRefString(docRef);
                } catch (final Exception ex) {
                    LOGGER.debug("Error displaying docRef {}: {}",
                            docRef, LogUtil.exceptionMessage(e), e);
                    return docRef.toString();
                }
            }
        }
    }

    private void stampAuditData(final D document) {
        AuditUtil.stamp(securityContext, document);
    }

    private void stampAuditData(final AbstractBuilder<D, ?> builder) {
        final long now = System.currentTimeMillis();
        final String userIdentityForAudit = securityContext.getUserIdentityForAudit();

        builder.createTimeMs(now);
        builder.createUser(userIdentityForAudit);
        builder.updateTimeMs(now);
        builder.updateUser(userIdentityForAudit);
    }
}
