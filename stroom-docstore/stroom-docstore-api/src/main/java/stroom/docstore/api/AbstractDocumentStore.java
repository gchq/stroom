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
 */

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Embeddable;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class for document store implementations that eliminates delegation boilerplate.
 * <p>
 * Subclasses only need to:
 * <ol>
 *     <li>Call the constructor with a serialiser, type constant, and builder references.</li>
 *     <li>Override methods where custom behaviour is required (e.g., {@link #createDocument},
 *         dependency mapping via {@link #getDependencyRemapFunction()}).</li>
 * </ol>
 * <p>
 * All delegation to the internal {@link Store} is handled by this class.
 *
 * <h2>Authorisation lives here</h2>
 * A document store is the service layer for its document type, so it is the level that decides who
 * may read, write or delete one. The {@link Store} beneath is a persistence layer: it does what it is
 * asked and does not make that decision. {@link #getStore()} is therefore a <b>deliberately
 * unchecked</b> handle, and an override that reaches for it instead of calling {@code super} is
 * choosing to bypass the check — which is legitimate (see
 * {@link #isDocumentPermissionCheckRequired()}) but has to be a choice rather than an accident.
 *
 * <p>Two things are NOT checked here, and both are deliberate:
 * <ul>
 *     <li>{@link #createDocument} — the document does not exist yet, so there is nothing to hold a
 *         permission on. Authority is established one frame up, by the explorer checking that the
 *         user may create this type in the destination folder.
 *     <li>{@link #importDocument} — its check is conditional on the document already existing, and
 *         importing a <em>new</em> one requires no document permission for the same reason a create
 *         does not. That decision needs state the persistence layer already has in hand, so it stays
 *         in {@code StoreImpl}; hoisting it here would refuse every new document, which is how
 *         content packs load at startup.
 * </ul>
 *
 * @param <D> The document type (e.g., {@code FeedDoc}, {@code PipelineDoc}).
 */
public abstract class AbstractDocumentStore<D extends AbstractDoc>
        implements DocumentStore<D> {

    private final Store<D> store;
    private final SecurityContext securityContext;

    /**
     * Construct a new AbstractDocumentStore.
     *
     * @param storeFactory    The factory for creating the internal {@link Store}.
     * @param securityContext The security context this store applies its document permission checks
     *                        with.
     * @param serialiser      The serialiser for this document type.
     * @param type            The document type constant (e.g., {@code FeedDoc.TYPE}).
     * @param builderSupplier Supplier for new document builders.
     * @param builderFunction Function to create a builder from an existing document (copy builder).
     */
    protected <B extends AbstractBuilder<D, ?>> AbstractDocumentStore(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final DocumentSerialiser2<D> serialiser,
            final String type,
            final Supplier<B> builderSupplier,
            final Function<D, B> builderFunction) {
        this.store = storeFactory.createStore(
                serialiser, type, builderSupplier, builderFunction, this::getDependencyRemapFunction);
        this.securityContext = securityContext;
    }

    // -------------------------------------------------------------------------
    // Authorisation
    // -------------------------------------------------------------------------

    /**
     * Whether documents of this type carry document permissions at all.
     *
     * <p>Override to {@code false} for a type that is not an explorer document and on which no user
     * will ever hold a document permission — the singleton configuration documents (data receipt
     * rules, data retention rules, content templates) are the cases. Those are authorised by an
     * <em>application</em> permission at their own entry points instead, and they write as the user
     * who made the change, so the audit trail names that user.
     */
    protected boolean isDocumentPermissionCheckRequired() {
        return true;
    }

    /** Throw unless the current user holds {@code permission} on {@code docRef}. */
    protected final void checkDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        if (!isDocumentPermissionCheckRequired()) {
            return;
        }
        if (!securityContext.hasDocumentPermission(docRef, permission)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You are not authorised to " + permission.getDisplayValue().toLowerCase() + " " + docRef);
        }
    }

    /**
     * The document a read is authorised against.
     *
     * <p>An embedded document — one declaring a parent via {@link Embeddable#getEmbeddedIn()}, such as
     * an XSLT or TextConverter embedded in a pipeline — is authorised by VIEW on its <b>parent</b>;
     * everything else by VIEW on itself.
     */
    protected final void checkReadPermission(final Object document, final DocRef docRef) {
        if (!isDocumentPermissionCheckRequired()) {
            return;
        }
        final DocRef refToAuthorise =
                document instanceof final Embeddable embeddable && embeddable.getEmbeddedIn() != null
                        ? embeddable.getEmbeddedIn()
                        : docRef;
        checkDocumentPermission(refToAuthorise, DocumentPermission.VIEW);
    }

    /** The {@link DocRef} of an in-hand document, for authorising a write against. */
    private DocRef docRefOf(final D document) {
        return new DocRef(store.getType(), document.getUuid(), document.getName());
    }

    /**
     * Access the underlying {@link Store} for subclasses that need direct access.
     */
    protected Store<D> getStore() {
        return store;
    }

    // -------------------------------------------------------------------------
    // Dependency mapping — override to provide a custom mapper
    // -------------------------------------------------------------------------

    /**
     * Override this method to provide a dependency remap function for this document type.
     * <p>
     * The default returns {@code null} (no dependency tracking).
     *
     * @return The dependency remap function, or {@code null} if this doc type has no dependencies.
     */
    protected DependencyRemapFunction<D> getDependencyRemapFunction() {
        return null;
    }

    // -------------------------------------------------------------------------
    // ExplorerActionHandler
    // -------------------------------------------------------------------------

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    /**
     * Copy reads the SOURCE document, so it is authorised by VIEW on the source. What may be created
     * at the destination is the explorer's decision, not this one.
     */
    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    /** Move reads the document but does not change it; the destination is the explorer's decision. */
    @Override
    public DocRef moveDocument(final DocRef docRef) {
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        return store.moveDocument(docRef);
    }

    /**
     * Rename mutates the document, so it is authorised by EDIT — which subsumes the VIEW its read
     * needs, permissions being hierarchical (VIEW 20 &lt; EDIT 30).
     *
     * <p>This check is <b>load-bearing rather than defence in depth</b>. {@code ExplorerServiceImpl}
     * guards move (EDIT) and copy (OWNER, where permissions are inherited) itself, but its rename path
     * performs no permission check at all, so this is the only thing standing between a user and
     * renaming a document they may not edit.
     */
    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        checkDocumentPermission(docRef, DocumentPermission.EDIT);
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        checkDocumentPermission(docRef, DocumentPermission.DELETE);
        store.deleteDocument(docRef);
    }

    // -------------------------------------------------------------------------
    // DocumentActionHandler
    // -------------------------------------------------------------------------

    @Override
    public D readDocument(final DocRef docRef) {
        // Read first, then authorise: whether an embedded document is authorised by its parent can
        // only be known from the document itself.
        final D document = store.readDocument(docRef);
        checkReadPermission(document, docRef);
        return document;
    }

    @Override
    public D writeDocument(final D document) {
        checkDocumentPermission(docRefOf(document), DocumentPermission.EDIT);
        return store.writeDocument(document);
    }

    // -------------------------------------------------------------------------
    // HasDependencies
    // -------------------------------------------------------------------------

    /**
     * Rewrite this document's references to other documents. It reads the document and writes it back
     * if anything changed, so it is authorised by EDIT.
     *
     * <p>In practice it is called on documents the caller has just COPIED, and therefore owns, so the
     * check is satisfied on the path that matters — but the operation is a document mutation and must
     * not be reachable without EDIT.
     *
     * <p>Checking here also makes a denial visible: {@code StoreImpl.remapDependencies} catches
     * {@link RuntimeException} and logs it, so a permission failure raised any deeper would be
     * swallowed, leaving a copy silently pointing at the originals' dependencies. Raised here, it is
     * refused out loud.
     */
    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        checkDocumentPermission(docRef, DocumentPermission.EDIT);
        store.remapDependencies(docRef, remappings);
    }

    // -------------------------------------------------------------------------
    // ImportExportActionHandler
    // -------------------------------------------------------------------------

    @Override
    public Set<DocRef> listDocuments() {
        if (!isDocumentPermissionCheckRequired()) {
            return Set.copyOf(store.list());
        }
        return store.list()
                .stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                .collect(Collectors.toSet());
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return store.importDocument(docRef, importExportDocument, importState, importSettings);
    }

    @Override
    public ImportExportDocument exportDocument(final DocRef docRef,
                                               final boolean omitAuditFields,
                                               final List<Message> messageList) {
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        // Historically this returned an empty map rather than throwing for a document the user may not
        // see; keep that, since it feeds an index rather than answering a user.
        if (isDocumentPermissionCheckRequired()
            && !securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return Map.of();
        }
        return store.getIndexableData(docRef);
    }

    /**
     * List all documents of this type. Not part of {@link DocumentStore} but provided
     * as a convenience since most typed store interfaces declare this method.
     */
    public List<DocRef> list() {
        if (!isDocumentPermissionCheckRequired()) {
            return store.list();
        }
        return store.list()
                .stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                .toList();
    }
}
