/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docref.EmbeddedDocRef;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Store;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocRefUtil;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Embeddable;
import stroom.util.shared.HasAuditInfoBuilder;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StoreImpl<D extends AbstractDoc, B extends AbstractBuilder<D, ?>> implements Store<D> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreImpl.class);
    private static final String META = "meta";

    private final Persistence persistence;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;
    private final Provider<DocFinder> docFinderProvider;
    private final Provider<DocDependencyService> docDependencyServiceProvider;

    private final DocumentSerialiser2<D> serialiser;
    private final String type;
    private final Supplier<B> builderSupplier;
    private final Function<D, B> builderFunction;
    private final Supplier<DependencyRemapFunction<D>> dependencyRemapFunctionSupplier;

    @Inject
    StoreImpl(final Persistence persistence,
              final EntityEventBus entityEventBus,
              final SecurityContext securityContext,
              final Provider<DocFinder> docFinderProvider,
              final Provider<DocDependencyService> docDependencyServiceProvider,
              final DocumentSerialiser2<D> serialiser,
              final String type,
              final Supplier<B> builderSupplier,
              final Function<D, B> builderFunction,
              final Supplier<DependencyRemapFunction<D>> dependencyRemapFunctionSupplier) {
        this.persistence = persistence;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
        this.docFinderProvider = docFinderProvider;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
        this.serialiser = serialiser;
        this.type = type;
        this.builderSupplier = builderSupplier;
        this.builderFunction = builderFunction;
        this.dependencyRemapFunctionSupplier = dependencyRemapFunctionSupplier;
    }

    // ---------------------------------------------------------------------
    // START OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    @Override
    public final DocRef createDocument(final String name) {
        Objects.requireNonNull(name);

        // Get a doc builder.
        final AbstractBuilder<D, ?> builder = builderSupplier.get();

        final D document = builder
                .uuid(UUID.randomUUID().toString())
                .name(name)
                .version(UUID.randomUUID().toString())
                .stampAudit(securityContext)
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

        // Copy and mutate the doc.
        final AbstractBuilder<D, ?> builder = builderFunction
                .apply(document)
                .uuid(UUID.randomUUID().toString())
                .name(newName)
                .version(UUID.randomUUID().toString())
                .stampAudit(securityContext);

        final D created = create(builder.build());
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
            // Copy and mutate the doc.
            final AbstractBuilder<D, ?> builder = builderFunction
                    .apply(document)
                    .name(name);
            final D updated = update(builder.build(), oldDocRef);
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

        persistence.delete(docRef, securityContext.getUserRef());
        EntityEvent.fire(entityEventBus, docRef, EntityAction.DELETE);

        removeDocDependencies(docRef);
    }

    // ---------------------------------------------------------------------
    // END OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        final DependencyRemapFunction<D> mapper = getDependencyRemapFunction();
        if (mapper != null) {
            try {
                D doc = readDocument(docRef);
                if (doc != null) {
                    final DependencyRemapper dependencyRemapper = new DependencyRemapper(remappings);
                    doc = mapper.remap(doc, dependencyRemapper);
                    if (dependencyRemapper.isChanged()) {
                        writeDocument(doc);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF DocumentActionHandler
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // END OF DocumentActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

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
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        if (importExportDocument != null) {
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
                                importExportDocument,
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

                    final D document = importDocument(docRef, existingDocument, uuid, importExportDocument);

                    if (document instanceof final Embeddable embeddable && embeddable.getEmbeddedIn() != null) {
                        docRef = new EmbeddedDocRef(docRef);
                    }
                }

            } catch (final RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
            }
        }

        return docRef;
    }

    private D importDocument(final DocRef docRef,
                             final D existingDocument,
                             final String uuid,
                             final ImportExportDocument convertedImportExportDocument) {
        try {
            // Turn the data map into a document.
            final D newDocument = serialiser.read(convertedImportExportDocument);

            // Get a builder to mutate the doc.
            final AbstractBuilder<D, ?> builder = builderFunction.apply(newDocument);

            // Copy create time and user from the existing document.
            if (existingDocument != null) {
                builder
                        .name(existingDocument.getName())
                        .createTimeMs(existingDocument.getCreateTimeMs())
                        .createUser(existingDocument.getCreateUser());
            }

            // Stamp audit data on the imported document.
            builder.stampAudit(securityContext);

            final D builtDoc = builder.build();
            // Convert the document back into a data map.
            final ImportExportDocument finalData = serialiser.write(builtDoc);
            // Write the data — import always succeeds, no version check.
            persistence.write(docRef, AuditAction.IMPORT, securityContext.getUserRef(),
                    finalData, null, builtDoc.getVersion());

            // Fire an entity event to alert other services of the change.
            if (existingDocument != null) {
                EntityEvent.fire(entityEventBus, docRef, EntityAction.UPDATE);
            } else {
                EntityEvent.fire(entityEventBus, docRef, EntityAction.CREATE);
            }

            updateDocDependencies(docRef, builtDoc, true);

            return newDocument;
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
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
    public ImportExportDocument exportDocument(final DocRef docRef,
                                               final boolean omitAuditFields,
                                               final List<Message> messageList) {
        return exportDocument(docRef, omitAuditFields, messageList, d -> d);
    }

    @Override
    public ImportExportDocument exportDocument(final DocRef docRef,
                                               final boolean omitAuditFields,
                                               final List<Message> messageList,
                                               final Function<D, D> function) {
        ImportExportDocument importExportDocument = new ImportExportDocument();

        try {
            // Check that the user has permission to read this item.
            if (!canRead(docRef)) {
                throwPermissionException("You are not authorised to read " + toDocRefDisplayString(docRef));
            } else {
                D document = read(docRef);
                if (document == null) {
                    throw new IOException("Unable to read " + toDocRefDisplayString(docRef));
                }
                if (omitAuditFields) {
                    document = removeAuditData(builderFunction, document);
                }
                importExportDocument = serialiser.write(function.apply(document));
            }
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR, e.getMessage()));
        }

        return importExportDocument;
    }

    private void checkForUpdatedFields(final D existingDoc,
                                       final ImportExportDocument importExportDocument,
                                       final List<String> updatedFieldList) {
        try {
            final D newDoc = serialiser.read(importExportDocument);
            final D existingDocument = removeAuditData(builderFunction, existingDoc);
            final D newDocument = removeAuditData(builderFunction, newDoc);

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

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    private DocRef createDocRef(final D document) {
        if (document == null) {
            return null;
        }

        return new DocRef(type, document.getUuid(), document.getName());
    }

    /**
     * Keep the doc_dependency store current for a create/update by extracting the document's
     * dependencies <b>directly from the in-hand object</b> and writing them.
     *
     * @param propagateName if {@code true}, also propagate this document's (possibly changed) name
     *                      to all edges that reference it as a target. Not required on create as
     *                      nothing can reference a brand-new document yet.
     */
    private void updateDocDependencies(final DocRef docRef, final D document, final boolean propagateName) {
        final DocDependencyService docDependencyService = getDocDependencyService();
        if (docDependencyService != null) {
            // The remap function may perform registry lookups for some doc types, so run as the
            // processing user. Errors are swallowed and logged by the service so a dependency-update
            // failure never blocks the document save (the table is a self-healing, rebuildable index).
            securityContext.asProcessingUser(() -> {
                final Set<DocRef> deps = extractDependencies(document, getDependencyRemapFunction());
                docDependencyService.setDependencies(docRef, deps);
                if (propagateName) {
                    docDependencyService.propagateName(docRef);
                }
            });
        }
    }

    /**
     * Remove all of a deleted document's outgoing dependency edges directly (see
     * {@link #updateDocDependencies}).
     */
    private void removeDocDependencies(final DocRef docRef) {
        final DocDependencyService docDependencyService = getDocDependencyService();
        if (docDependencyService != null) {
            securityContext.asProcessingUser(() -> docDependencyService.removeDependencies(docRef));
        }
    }

    /**
     * Extract the dependencies of a document using this store's dependency remap function. Returns an
     * empty set when this doc type has no mapper (i.e. it tracks no dependencies).
     */
    private Set<DocRef> extractDependencies(final D document, final DependencyRemapFunction<D> mapper) {
        if (mapper == null || document == null) {
            return Collections.emptySet();
        }
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        mapper.remap(document, dependencyRemapper);
        return dependencyRemapper.getDependencies();
    }

    /**
     * @return this store's dependency remap function, or {@code null} if it has none (either the doc
     * type tracks no dependencies, or no supplier was provided, e.g. in lightweight tests).
     */
    private DependencyRemapFunction<D> getDependencyRemapFunction() {
        return dependencyRemapFunctionSupplier != null
                ? dependencyRemapFunctionSupplier.get()
                : null;
    }

    /**
     * @return the dependency service, or {@code null} when it has not been provided (e.g. in
     * lightweight in-memory tests that construct a store directly, as with the nullable
     * {@code entityEventBus}).
     */
    private DocDependencyService getDocDependencyService() {
        return docDependencyServiceProvider != null
                ? docDependencyServiceProvider.get()
                : null;
    }

    private D create(final D document) {
        try {
            final DocRef docRef = createDocRef(document);
            final ImportExportDocument importExportDocument = serialiser.write(document);
            persistence.write(docRef, AuditAction.CREATE, securityContext.getUserRef(),
                    importExportDocument, null, document.getVersion());
            EntityEvent.fire(entityEventBus, docRef, EntityAction.CREATE);

            // A copied document inherits the original's outgoing edges, so this is needed on create.
            updateDocDependencies(docRef, document, false);
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

        final ImportExportDocument importExportDocument = readPersistence(docRef);
        if (importExportDocument != null) {
            try {
                final D doc = serialiser.read(importExportDocument);
                if (doc instanceof final Embeddable embeddable) {
                    final DocRef parentDocRef = embeddable.getEmbeddedIn();
                    if (parentDocRef != null) {
                        if (!securityContext.hasDocumentPermission(parentDocRef, DocumentPermission.VIEW)) {
                            throwPermissionException(LogUtil.message("You are not authorised to read {}",
                                    toDocRefDisplayString(parentDocRef)));
                        }
                    } else {
                        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                            throwPermissionException(LogUtil.message("You are not authorised to read {}",
                                    toDocRefDisplayString(docRef)));
                        }
                    }
                }
                return doc;
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
        D updatedDoc = document;
        final DocRef docRef = createDocRef(updatedDoc);

        // Check that the user has permission to update this item.
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throwPermissionException("You are not authorised to update " + toDocRefDisplayString(docRef));
        }

        try {
            // Capture the version the caller expects to be current.
            final String currentVersion = updatedDoc.getVersion();
            final String newVersion = UUID.randomUUID().toString();

            // Copy and mutate the doc with a new version.
            final AbstractBuilder<D, ?> builder = builderFunction
                    .apply(updatedDoc)
                    .version(newVersion);

            // Add audit data.
            builder.stampAudit(securityContext);
            updatedDoc = builder.build();

            final ImportExportDocument newData = serialiser.write(updatedDoc);

            // Single atomic call — persistence layer handles version check.
            // For DB: UPDATE ... WHERE version = expectedVersion (optimistic lock).
            // For FS: StripedLockFactory in StoreImpl.readPersistence() serialises access.
            persistence.write(docRef, AuditAction.UPDATE, securityContext.getUserRef(),
                    newData, currentVersion, newVersion);
            EntityEvent.fire(entityEventBus, docRef, oldDocRef, EntityAction.UPDATE);

            // Re-compute this doc's outgoing edges and propagate a potential name change to all
            // edges that reference it (covers both a content save and a rename).
            updateDocDependencies(docRef, updatedDoc, true);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return updatedDoc;
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
    public List<DocRef> findDocRefsEmbeddedIn(final DocRef parent) {
        return persistence.findDocRefsEmbeddedIn(parent);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        if (!canRead(docRef)) {
            return Collections.emptyMap();
        }

        final ImportExportDocument importExportDocument = readPersistence(docRef);
        if (importExportDocument == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> retval = new HashMap<>();
        final Collection<ImportExportAsset> extAssets = importExportDocument.getExtAssets();
        for (final ImportExportAsset asset : extAssets) {
            final byte[] data;
            try {
                data = asset.getInputData();
            } catch (final IOException e) {
                throw new UncheckedIOException(LogUtil.message("Error reading {} asset {}: {}",
                        toDocRefDisplayString(docRef),
                        asset.getKey(),
                        e.getMessage()), e);
            }

            String stringData = "";
            if (data != null) {
                stringData = new String(data, StandardCharsets.UTF_8);
            }
            retval.put(asset.getKey(), stringData);
        }

        return retval;
    }

    private ImportExportDocument readPersistence(final DocRef docRef) {
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
    }

    private String toDocRefDisplayString(final DocRef docRef) {
        if (docRef == null || !NullSafe.isBlankString(docRef.getName())) {
            return "";
        } else {
            try {
                return DocRefUtil.createTypedDocRefString(docFinderProvider.get().decorate(docRef));
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

    /**
     * Remove audit data from docs.
     *
     * @param builderFunction The builder factory that allows a builder to be created for the doc that can remove the
     *                        fields.
     * @param doc             The doc to alter.
     * @param <D>             Doc type.
     * @param <B>             Builder type.
     * @return The doc with audit fields removed.
     */
    private static <D, B extends HasAuditInfoBuilder<D, ?>> D removeAuditData(final Function<D, B> builderFunction,
                                                                              final D doc) {
        return builderFunction
                .apply(doc)
                .createTimeMs(null)
                .createUser(null)
                .updateTimeMs(null)
                .updateUser(null)
                .build();
    }

}
