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

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * @param <D> The document type (e.g., {@code FeedDoc}, {@code PipelineDoc}).
 */
public abstract class AbstractDocumentStore<D extends AbstractDoc>
        implements DocumentStore<D> {

    private final Store<D> store;

    /**
     * Construct a new AbstractDocumentStore.
     *
     * @param storeFactory    The factory for creating the internal {@link Store}.
     * @param serialiser      The serialiser for this document type.
     * @param type            The document type constant (e.g., {@code FeedDoc.TYPE}).
     * @param builderSupplier Supplier for new document builders.
     * @param builderFunction Function to create a builder from an existing document (copy builder).
     */
    protected <B extends AbstractBuilder<D, ?>> AbstractDocumentStore(
            final StoreFactory storeFactory,
            final DocumentSerialiser2<D> serialiser,
            final String type,
            final Supplier<B> builderSupplier,
            final Function<D, B> builderFunction) {
        this.store = storeFactory.createStore(serialiser, type, builderSupplier, builderFunction);
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

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    // -------------------------------------------------------------------------
    // DocumentActionHandler
    // -------------------------------------------------------------------------

    @Override
    public D readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public D writeDocument(final D document) {
        return store.writeDocument(document);
    }

    // -------------------------------------------------------------------------
    // HasDependencies
    // -------------------------------------------------------------------------

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(getDependencyRemapFunction());
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, getDependencyRemapFunction());
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, getDependencyRemapFunction());
    }

    // -------------------------------------------------------------------------
    // ImportExportActionHandler
    // -------------------------------------------------------------------------

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
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
        return store.getIndexableData(docRef);
    }

    /**
     * List all documents of this type. Not part of {@link DocumentStore} but provided
     * as a convenience since most typed store interfaces declare this method.
     */
    public List<DocRef> list() {
        return store.list();
    }
}
