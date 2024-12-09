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
import stroom.docstore.shared.DocRefUtil;
import stroom.docstore.shared.UniqueNameUtil;
import stroom.importexport.api.ImportConverter;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;
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
    private final ImportConverter importConverter;
    private final SecurityContext securityContext;
    private final Provider<DocRefDecorator> docRefInfoServiceProvider;

    private final DocumentSerialiser2<D> serialiser;
    private final String type;
    private final Class<D> clazz;

    @Inject
    StoreImpl(final Persistence persistence,
              final EntityEventBus entityEventBus,
              final ImportConverter importConverter,
              final SecurityContext securityContext,
              final Provider<DocRefDecorator> docRefInfoServiceProvider,
              final DocumentSerialiser2<D> serialiser,
              final String type,
              final Class<D> clazz) {
        this.persistence = persistence;
        this.entityEventBus = entityEventBus;
        this.importConverter = importConverter;
        this.securityContext = securityContext;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.serialiser = serialiser;
        this.type = type;
        this.clazz = clazz;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String newName) {
        Objects.requireNonNull(originalUuid);
        Objects.requireNonNull(newName);

        final D document = read(originalUuid);
        document.setUuid(UUID.randomUUID().toString());
        document.setName(newName);
        document.setUniqueName(UniqueNameUtil.createDefault(document.asDocRef()));
        document.setVersion(UUID.randomUUID().toString());

        // Add audit data.
        stampAuditData(document);

        final DocumentData documentData = createDocumentData(document);
        final D created = forceCreate(documentData, document);
        return created.asDocRef();
    }

    private DocumentData createDocumentData(D document) {
        try {
            final DocRef docRef = document.asDocRef();
            final Map<String, byte[]> data = serialiser.write(document);
            return DocumentData
                    .builder()
                    .docRef(docRef)
                    .uniqueName(document.getUniqueName())
                    .version(document.getVersion())
                    .data(data)
                    .build();
        } catch (final IOException e) {
            LOGGER.error("Error serialising {}", document.getType(), e);
            throw new UncheckedIOException(e);
        }
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
        return document.asDocRef();
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        Objects.requireNonNull(docRef);
        Objects.requireNonNull(name);
        final D document = read(docRef);

        // Only update the document if the name has actually changed.
        if (!Objects.equals(document.getName(), name)) {
            document.setName(name);
            final String expectedVersion = document.getVersion();
            document.setVersion(UUID.randomUUID().toString());
            final D updated = update(expectedVersion, document, document.asDocRef());
            return updated.asDocRef();
        }

        return document.asDocRef();
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
    ////////////////////////////////////////////////////////////////////////

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
    ////////////////////////////////////////////////////////////////////////


    @Override
    public D createDocument() {
        try {
            final D document = clazz.getDeclaredConstructor(new Class[0]).newInstance();
            document.setName("Untitled");
            document.setUuid(UUID.randomUUID().toString());
            // Add audit data.
            stampAuditData(document);
            return document;
        } catch (final InstantiationException
                       | IllegalAccessException
                       | NoSuchMethodException
                       | InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public D readDocument(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        return read(docRef.getUuid());
    }

    @Override
    public D writeDocument(final D document) {
        Objects.requireNonNull(document);
        if (document.getVersion() == null) {
            document.setVersion(UUID.randomUUID().toString());
            return create(document);
        } else {
            final String expectedVersion = document.getVersion();
            document.setVersion(UUID.randomUUID().toString());
            return update(expectedVersion, document, document.asDocRef());
        }
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
    ////////////////////////////////////////////////////////////////////////


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
        // Convert legacy import format to the new format if necessary.
        final Map<String, byte[]> convertedDataMap = importConverter.convert(
                docRef,
                dataMap,
                importState,
                importSettings,
                securityContext.getUserIdentityForAudit());

        if (convertedDataMap != null) {
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
                                convertedDataMap,
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

                    importDocument(docRef, existingDocument, uuid, convertedDataMap);
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
                    newDocument.setUniqueName(existingDocument.getUniqueName());
                    newDocument.setCreateTimeMs(existingDocument.getCreateTimeMs());
                    newDocument.setCreateUser(existingDocument.getCreateUser());
                }

                // Ensure we have a unique name.
                if (NullSafe.isBlankString(newDocument.getUniqueName())) {
                    newDocument.setUniqueName(UniqueNameUtil.createDefault(docRef));
                }

                // Stamp audit data on the imported document.
                stampAuditData(newDocument);

                // Write the data.
                final DocumentData documentData = createDocumentData(newDocument);
                if (existingDocument != null) {
                    persistence.update(existingDocument.getVersion(), documentData);

                } else {
                    // Try and create with supplied unique name.
                    forceCreate(documentData, newDocument);
                }

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

    private D forceCreate(final DocumentData documentData,
                          final D document) {
        return persistence.getLockFactory().lockResult(document.getUuid(), () -> {
            try {
                // Try and create with supplied unique name.
                boolean created = false;
                Exception lastException = null;
                DocumentData createdDocumentData = documentData;
                try {
                    createdDocumentData = persistence.create(createdDocumentData);
                    document.setUniqueName(createdDocumentData.getUniqueName());
                    document.setVersion(createdDocumentData.getVersion());
                    created = true;
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                    lastException = e;
                }

                // Try different unique names if we can't create.
                if (!created) {
                    String uniqueName = createdDocumentData.getUniqueName();
                    int index = uniqueName.indexOf("_");
                    if (index != -1) {
                        uniqueName = uniqueName.substring(0, index);
                    }
                    for (int i = 2; i < 100 && !created; i++) {
                        final String uniqueName2 = uniqueName + "_" + i;

                        createdDocumentData = createdDocumentData.copy().uniqueName(uniqueName2).build();
                        try {
                            createdDocumentData = persistence.create(createdDocumentData);
                            document.setUniqueName(createdDocumentData.getUniqueName());
                            document.setVersion(createdDocumentData.getVersion());
                            created = true;
                        } catch (final Exception e) {
                            LOGGER.debug(e::getMessage, e);
                            lastException = e;
                        }
                    }

                    if (!created) {
                        LOGGER.debug(lastException::getMessage, lastException);
                        throw lastException;
                    }
                }

                return document;
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage(), e);
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
    ////////////////////////////////////////////////////////////////////////

    private D create(final D document) {
        final DocumentData documentData = createDocumentData(document);
        persistence.getLockFactory().lock(document.getUuid(), () -> {
            try {
                final DocumentData createdDocumentData = persistence.create(documentData);
                document.setUniqueName(createdDocumentData.getUniqueName());
                document.setVersion(createdDocumentData.getVersion());
                EntityEvent.fire(entityEventBus, documentData.getDocRef(), EntityAction.CREATE);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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

        final Optional<DocumentData> optional = persistence.getLockFactory().lockResult(uuid, () -> {
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

        if (optional.isPresent()) {
            try {
                final DocumentData documentData = optional.get();
                final D doc = serialiser.read(documentData.getData());
                doc.setUniqueName(documentData.getUniqueName());
                doc.setVersion(documentData.getVersion());
                return doc;
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(
                        LogUtil.message("Error deserialising {} from store {}, {}",
                                toDocRefDisplayString(uuid),
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
        Objects.requireNonNull(docRef);
        if (!Objects.equals(type, docRef.getType())) {
            throw new RuntimeException(LogUtil.message(
                    "Invalid docRef type, found: '{}', expecting: '{}'",
                    docRef.getType(), type));
        }
    }

    private D update(String expectedVersion, final D document, final DocRef oldDocRef) {
        // Add audit data.
        stampAuditData(document);
        final DocumentData documentData = createDocumentData(document);
        final DocRef docRef = documentData.getDocRef();

        // Check that the user has permission to update this item.
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throwPermissionException("You are not authorised to update " + toDocRefDisplayString(docRef));
        }

        // Add audit data.
        stampAuditData(document);

        persistence.getLockFactory().lock(document.getUuid(), () -> {
            try {
                final DocumentData updated = persistence.update(expectedVersion, documentData);
                document.setUniqueName(updated.getUniqueName());
                EntityEvent.fire(entityEventBus, docRef, oldDocRef, EntityAction.UPDATE);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

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

        final String uuid = docRef.getUuid();
        final Optional<DocumentData> optional = persistence.getLockFactory().lockResult(uuid, () -> {
            try {
                return persistence.read(new DocRef(type, uuid));
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedIOException(
                        LogUtil.message("Error reading {} from store {}, {}",
                                toDocRefDisplayString(uuid),
                                persistence.getClass().getSimpleName(),
                                e.getMessage()), e);
            }
        });

        return optional
                .map(documentData -> documentData
                        .getData()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> new String(e.getValue(), StandardCharsets.UTF_8))))
                .orElse(Collections.emptyMap());

    }

    private String toDocRefDisplayString(final String uuid) {
        if (uuid == null) {
            return "";
        } else {
            return toDocRefDisplayString(new DocRef(type, uuid));
        }
    }

    private String toDocRefDisplayString(final DocRef docRef) {
        if (docRef == null || !NullSafe.isBlankString(docRef.getName())) {
            return "";
        } else {
            try {
                return DocRefUtil.createTypedDocRefString(docRefInfoServiceProvider.get()
                        .decorate(docRef));
            } catch (Exception e) {
                // This method is for use in decorating the docref for exception messages
                // so swallow any errors.
                LOGGER.debug("Error decorating docRef {}: {}",
                        docRef, LogUtil.exceptionMessage(e), e);
                try {
                    return DocRefUtil.createTypedDocRefString(docRef);
                } catch (Exception ex) {
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
}
