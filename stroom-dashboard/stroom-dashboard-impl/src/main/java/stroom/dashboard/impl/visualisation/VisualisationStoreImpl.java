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

package stroom.dashboard.impl.visualisation;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Singleton
class VisualisationStoreImpl
        extends AbstractDocumentStore<VisualisationDoc>
        implements VisualisationStore {

    private final VisualisationAssetService visualisationAssetService;

    @Inject
    VisualisationStoreImpl(final StoreFactory storeFactory,
                           final VisualisationSerialiser serialiser,
                           final VisualisationAssetService assetService) {
        super(storeFactory,
                serialiser,
                VisualisationDoc.TYPE,
                VisualisationDoc::builder,
                VisualisationDoc::copy);
        this.visualisationAssetService = assetService;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DocRef copyDocRef = getStore().copyDocument(docRef.getUuid(), newName);
        try {
            visualisationAssetService.copyAssetsToDoc(docRef, copyDocRef);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return copyDocRef;
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);
        try {
            visualisationAssetService.deleteAssetsForDoc(docRef);
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
            visualisationAssetService.setAssetsFromImport(docRef, importExportDocument.getPathAssets());
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
            final Collection<ImportExportAsset> assets = visualisationAssetService.getAssetsForExport(docRef);
            for (final ImportExportAsset asset : assets) {
                importExportDocument.addPathAsset(asset);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return importExportDocument;
    }
}
