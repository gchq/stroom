/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.document.asset.impl.DocumentAssetService;
import stroom.floormap.shared.FloorMapDoc;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/// Singleton implementation of [FloorMapStore] built on [AbstractDocumentStore],
/// which handles the standard document CRUD, import/export and dependency delegation.
///
/// This class adds floor-map specific behaviour: it materialises newly created documents as a
/// processing user, copies the document when duplicating, and remaps the facts/events store
/// references it depends on.
///
/// Documents of this type can own uploaded assets (images used as fact graphics and map
/// backgrounds), held by the `stroom.document.asset` subsystem in its own table keyed on the
/// owning document's UUID rather than inside the serialised document. Nothing in
/// [AbstractDocumentStore] knows about them, so every lifecycle operation that should carry
/// them has to say so explicitly — export, import, copy and delete are all overridden below for
/// that reason alone. This mirrors `VisualisationStoreImpl`, the other owner of assets;
/// the two should be changed together.
///
/// The duplicate is a genuine copy rather than an aliasing one: `FloorMapDoc.copy()` copies
/// the document's `valueSchema`, `typeStyles` and `groups` collections, and their
/// elements expose no setters, so nothing is shared with the original. That was not true when this
/// class was written — the collections were assigned by reference and a duplicated document shared
/// list instances with its source — so do not weaken `FloorMapDoc.Builder`'s copying without
/// revisiting this.
@Singleton
class FloorMapStoreImpl extends AbstractDocumentStore<FloorMapDoc> implements FloorMapStore {

    private final SecurityContext securityContext;
    private final DocumentAssetService documentAssetService;

    @Inject
    FloorMapStoreImpl(final StoreFactory storeFactory,
                      final FloorMapSerialiser serialiser,
                      final SecurityContext securityContext,
                      final DocumentAssetService documentAssetService) {
        super(storeFactory,
                securityContext,
                serialiser,
                FloorMapDoc.TYPE,
                FloorMapDoc::builder,
                FloorMapDoc::copy);
        this.securityContext = securityContext;
        this.documentAssetService = documentAssetService;
    }

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = getStore().createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final FloorMapDoc floorMapDoc = getStore().readDocument(docRef);
            getStore().writeDocument(floorMapDoc);
        });
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        // Copy reads the source document, so it needs VIEW on it. This override reaches
        // getStore() directly, which is the unchecked handle, so the check the base class applies
        // has to be applied here. ExplorerServiceImpl guards its own copy path with OWNER, so this
        // is defence in depth rather than the only barrier - but the store is reachable by other
        // callers, and the base class does not stop checking just because someone else also does.
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final FloorMapDoc document = getStore().readDocument(docRef);
        final DocRef copyDocRef = getStore().createDocument(newName,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) ->
                        document.copy()
                                .uuid(uuid)
                                .name(docName)
                                .version(version)
                                .createTimeMs(createTime)
                                .updateTimeMs(updateTime)
                                .createUser(createUser)
                                .updateUser(updateUser)
                                .build());
        // The document's assets live outside the document, so copy() does not bring them and a
        // duplicated floor map would render with every graphic and background missing.
        try {
            documentAssetService.copyAssetsToDoc(docRef, copyDocRef);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return copyDocRef;
    }

    /// Deletes the document and the assets it owns.
    ///
    /// Without this the rows in the asset table outlive the document that owned them: nothing
    /// else is keyed to find them, so the blobs are unreachable and permanent.
    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);
        try {
            documentAssetService.deleteAssetsForDoc(docRef);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /// Imports the document, then restores the assets that travelled with it.
    ///
    /// The assets arrive as *path* assets — sub-paths beside the document's own entries —
    /// rather than extension assets, because their names are user-chosen file names and there is no
    /// fixed set of them.
    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        final DocRef storeDocRef = getStore()
                .importDocument(docRef, importExportDocument, importState, importSettings);
        try {
            // The ref the store returned, not the one passed in. Equivalent today - StoreImpl only
            // rewrites the name, never the UUID, and setAssetsFromImport keys on the UUID - so this
            // is defensive rather than a fix. Worth doing because the failure it would cause is
            // silent and destructive: setAssetsFromImport DELETES everything under the owner UUID
            // before inserting, so a mismatched ref would both lose the imported assets and wipe
            // whatever was already there.
            documentAssetService.setAssetsFromImport(
                    storeDocRef, importExportDocument.getPathAssets());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return storeDocRef;
    }

    /// Exports the document together with the assets it owns.
    ///
    /// Assets are not part of the serialised document, so a content pack built without this
    /// carries a floor map whose graphics and backgrounds are all absent on import — and it fails
    /// on the importing system, silently, rather than at export time where it could be noticed.
    @Override
    public ImportExportDocument exportDocument(final DocRef docRef,
                                               final boolean omitAuditFields,
                                               final List<Message> messageList) {
        final ImportExportDocument importExportDocument = getStore()
                .exportDocument(docRef, omitAuditFields, messageList);
        try {
            final Collection<ImportExportAsset> assets =
                    documentAssetService.getAssetsForExport(docRef);
            for (final ImportExportAsset asset : assets) {
                importExportDocument.addPathAsset(asset);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return importExportDocument;
    }

    @Override
    protected DependencyRemapFunction<FloorMapDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final FloorMapDoc.Builder builder = doc.copy();
            if (doc.getFactsStoreRef() != null) {
                builder.factsStoreRef(dependencyRemapper.remap(doc.getFactsStoreRef()));
            }
            if (doc.getEventsStoreRef() != null) {
                builder.eventsStoreRef(dependencyRemapper.remap(doc.getEventsStoreRef()));
            }
            return builder.build();
        };
    }
}
