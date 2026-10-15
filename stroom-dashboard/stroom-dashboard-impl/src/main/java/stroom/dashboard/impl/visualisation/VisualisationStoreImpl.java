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

package stroom.dashboard.impl.visualisation;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.document.asset.impl.DocumentAssetService;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Message;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master.
// Part of "Make the Visualisation Asset system generic" - upstream's VisualisationAsset*
// classes were generalised into the shared stroom.document.asset subsystem so FloorMap can
// carry assets too. Upstream still has the visualisation-specific version, so a merge will
// try to reinstate it; keep this side and re-point any new upstream code at document.asset.
@Singleton
class VisualisationStoreImpl
        extends AbstractDocumentStore<VisualisationDoc>
        implements VisualisationStore {

    private final DocumentAssetService documentAssetService;

    @Inject
    VisualisationStoreImpl(final StoreFactory storeFactory,
                           final SecurityContext securityContext,
                           final VisualisationSerialiser serialiser,
                           final DocumentAssetService assetService) {
        super(storeFactory,
                securityContext,
                serialiser,
                VisualisationDoc.TYPE,
                VisualisationDoc::builder,
                VisualisationDoc::copy);
        this.documentAssetService = assetService;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        // Copy reads the source document, so it needs VIEW on it. This override reaches
        // getStore() directly, which is the unchecked handle, so the check the base applies is
        // applied here.
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        final DocRef copyDocRef = getStore().copyDocument(docRef.getUuid(), newName);
        try {
            documentAssetService.copyAssetsToDoc(docRef, copyDocRef);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return copyDocRef;
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);
        try {
            documentAssetService.deleteAssetsForDoc(docRef);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected DependencyRemapFunction<VisualisationDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) ->
                doc.copy().scriptRef(dependencyRemapper.remap(doc.getScriptRef())).build();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {

        final DocRef storeDocRef = getStore().importDocument(docRef, importExportDocument, importState, importSettings);

        // Import the path assets
        try {
            documentAssetService.setAssetsFromImport(docRef, importExportDocument.getPathAssets());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return storeDocRef;
    }

    @Override
    public ImportExportDocument exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {

        final ImportExportDocument importExportDocument = getStore()
                .exportDocument(docRef, omitAuditFields, messageList);

        // Get all the assets to be exported to sub-paths
        try {
            final Collection<ImportExportAsset> assets = documentAssetService.getAssetsForExport(docRef);
            for (final ImportExportAsset asset : assets) {
                importExportDocument.addPathAsset(asset);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return importExportDocument;
    }
}
