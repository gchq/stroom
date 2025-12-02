/*
 * Copyright 2024 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.pathways.shared.PathwaysDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class PathwaysStoreImpl implements PathwaysStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysStoreImpl.class);

    private final Store<PathwaysDoc> store;
    private final PathwaysSerialiser serialiser;

    @Inject
    PathwaysStoreImpl(final StoreFactory storeFactory,
                      final PathwaysSerialiser serialiser) {
        this.store = storeFactory.createStore(serialiser, PathwaysDoc.TYPE, PathwaysDoc::builder);
        this.serialiser = serialiser;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

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
        final DocRef copy = store.copyDocument(docRef.getUuid(), newName);
        // TODO : Leaving here as we will want to copy pathways once they are in the DB.
//        indexFieldServiceProvider.get().copyAll(docRef, copy);
        return copy;
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

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(null);
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, null);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, null);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public PathwaysDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public PathwaysDoc writeDocument(final PathwaysDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
//
//        Map<String, byte[]> effectiveDataMap = dataMap;
//        try {
////            boolean altered = false;
//            final PathwaysDoc doc = serialiser.read(dataMap);
//
//            // TODO : Leaving here as we will want to copy pathways once they are in the DB.
////            // If the imported feed's vol grp doesn't exist in this env use our default
////            // or null it out
////            final String volumeGroup = doc.getVolumeGroupName();
////            if (volumeGroup != null) {
////                final IndexVolumeGroupService fsVolumeGroupService = indexVolumeGroupServiceProvider.get();
////                final List<String> allVolumeGroups = fsVolumeGroupService.getNames();
////                if (!allVolumeGroups.contains(volumeGroup)) {
////                    LOGGER.debug("Volume group '{}' in imported index {} is not a valid volume group",
////                            volumeGroup, docRef);
////                    fsVolumeGroupService.getDefaultVolumeGroup()
////                            .ifPresentOrElse(
////                                    doc::setVolumeGroupName,
////                                    () -> doc.setVolumeGroupName(null));
////                    altered = true;
////                }
////            }
////
////            // Transfer fields to the database.
////            if (NullSafe.hasItems(doc.getFields())) {
////                // Make sure we transfer all fields to the DB and remove them from the doc.
////                final List<IndexField> fields = doc
////                        .getFields()
////                        .stream()
////                        .map(field -> (IndexField) field)
////                        .toList();
////                indexFieldServiceProvider.get().addFields(doc.asDocRef(), fields);
////                doc.setFields(null);
////                altered = true;
////            }
//
////            if (altered) {
////                effectiveDataMap = serialiser.write(doc);
////            }
//
//        } catch (final IOException e) {
//            throw new RuntimeException(LogUtil.message("Error de-serialising feed {}: {}",
//                    docRef, e.getMessage()), e);
//        }

        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        // TODO : Leaving here as we will want to copy pathways once they are in the DB.
//        // Get the first 1000 fields.
//        final List<PathwaysField> fields = getFieldsForExport(docRef);
//        if (omitAuditFields) {
//            return store.exportDocument(docRef, messageList, d -> {
//                new AuditFieldFilter<>().apply(d);
//                d.setFields(fields);
//                return d;
//            });
//        }
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    // TODO : Leaving here as we will want to copy pathways once they are in the DB.
//    private List<PathwaysField> getFieldsForExport(final DocRef docRef) {
//        try {
//            // Limited to 1000 fields.
//            final PageRequest pageRequest = new PageRequest(0, 1000);
//            final FindFieldCriteria findFieldCriteria =
//                    new FindFieldCriteria(pageRequest, FindFieldCriteria.DEFAULT_SORT_LIST, docRef);
//            final ResultPage<IndexField> indexFields =
//                    indexFieldServiceProvider.get().findFields(findFieldCriteria);
//            return indexFields
//                    .getValues()
//                    .stream()
//                    .map(indexField -> new PathwaysField.Builder(indexField)
//                            .build())
//                    .toList();
//        } catch (final RuntimeException e) {
//            LOGGER.error(e::getMessage, e);
//        }
//        return null;
//    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
