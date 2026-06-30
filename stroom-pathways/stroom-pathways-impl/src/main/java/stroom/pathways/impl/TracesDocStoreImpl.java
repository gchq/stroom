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

package stroom.pathways.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.fs.SharedFileStoreDocStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class TracesDocStoreImpl implements TracesDocStore, SharedFileStoreDocStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesDocStoreImpl.class);

    private final Store<TracesDoc> store;
    private final TracesDocSerialiser serialiser;
    private final Provider<ClusterLockService> clusterLockServiceProvider;

    @Inject
    TracesDocStoreImpl(final StoreFactory storeFactory,
                       final TracesDocSerialiser serialiser,
                       final Provider<ClusterLockService> clusterLockServiceProvider) {
        this.store = storeFactory.createStore(
                serialiser,
                TracesDoc.TYPE,
                TracesDoc::tracesBuilder,
                TracesDoc::copyTraces);
        this.serialiser = serialiser;
        this.clusterLockServiceProvider = clusterLockServiceProvider;
    }

    // ---------------------------------------------------------------------
    // START OF ExplorerActionHandler
    // ---------------------------------------------------------------------

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
        // Read the doc BEFORE deleting the config so we can capture the sharedPath.
        final TracesDoc doc = docRef != null && docRef.getUuid() != null
                ? store.readDocument(DocRef.builder()
                        .uuid(docRef.getUuid())
                        .type(TracesDoc.TYPE)
                        .build())
                : null;

        // 1. Delete config from the document store.
        store.deleteDocument(docRef);

        // 2. Atomically rename shared-filesystem shard directories to trash.
        //    The housekeeping job drains trash asynchronously.
        //    Non-fatal: orphan detection will catch any rename failures on the next run.
        if (doc != null) {
            trashSharedData(doc);
        }

        // 3. Clean up cluster merge locks.
        if (docRef != null && docRef.getUuid() != null) {
            try {
                clusterLockServiceProvider.get().deleteLocks("planb-merge-" + docRef.getUuid() + "-");
            } catch (final Exception e) {
                // Ignore lock deletion failures to avoid failing the document delete itself.
            }
        }
    }

    private static final List<String> SHARD_SUBDIRS =
            List.of(PlanBConstants.SHARDS_DIR_NAME, PlanBConstants.PROCESSING_DIR_NAME,
                    PlanBConstants.ARCHIVE_DIR_NAME);

    /**
     * Atomically renames shard and processing directories for the given doc into
     * a {@code trash/} staging area under the same shared path root. The
     * housekeeping job ({@link stroom.planb.impl.SharedFileStoreCleaner}) drains the
     * trash on its next run.
     */
    private void trashSharedData(final TracesDoc doc) {
        final String sharedPathStr = doc.getSharedPath();
        if (sharedPathStr == null || sharedPathStr.isBlank()) {
            return;
        }
        final Path sharedRoot = Path.of(sharedPathStr);
        final String trashEntryName = doc.getUuid() + "-" + System.currentTimeMillis();
        final Path trashEntry = sharedRoot
                .resolve(PlanBConstants.TRASH_DIR_NAME)
                .resolve(trashEntryName);

        for (final String subdir : SHARD_SUBDIRS) {
            final Path src = sharedRoot.resolve(subdir).resolve(doc.getUuid());
            if (!Files.exists(src)) {
                continue;
            }
            final Path dest = trashEntry.resolve(subdir);
            try {
                Files.createDirectories(dest.getParent());
                Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
                LOGGER.info("Moved deleted doc shard data to trash: {} -> {}", src, dest);
            } catch (final NoSuchFileException e) {
                // Already moved (e.g. by a concurrent operation) — safe to ignore.
            } catch (final IOException e) {
                LOGGER.warn(() -> "Could not move shard data to trash for doc " +
                        doc.getUuid() + ": " + e.getMessage() +
                        " — housekeeping job will clean it up as an orphan");
            }
        }
    }


    // ---------------------------------------------------------------------
    // END OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF DocumentActionHandler
    // ---------------------------------------------------------------------

    @Override
    public TracesDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public TracesDoc writeDocument(final TracesDoc document) {
        final DocRef docRef = DocRef.builder()
                .type(document.getType())
                .uuid(document.getUuid())
                .name(document.getName())
                .build();
        final TracesDoc oldDoc = store.readDocument(docRef);
        // Guard against inadvertent shard-count changes when shared file store data already exists.
        if (oldDoc != null
                && document.getShardCount() > 0
                && oldDoc.getShardCount() != document.getShardCount()) {
            if (hasSharedFileStoreData(oldDoc)) {
                throw new EntityServiceException(
                        "Cannot change shard count: data has already been written to this store.");
            }
        }
        return store.writeDocument(document);
    }

    @Override
    public boolean hasSharedFileStoreData(final String uuid) {
        final DocRef docRef = DocRef.builder()
                .uuid(uuid)
                .type(TracesDoc.TYPE)
                .build();
        return hasSharedFileStoreData(store.readDocument(docRef));
    }

    private boolean hasSharedFileStoreData(final TracesDoc doc) {
        if (doc == null) {
            return false;
        }
        final String sharedPathStr = doc.getSharedPath();
        if (sharedPathStr == null || sharedPathStr.isBlank()) {
            return false;
        }
        try {
            final Path sharedRoot = Path.of(sharedPathStr);
            return Files.exists(sharedRoot.resolve(PlanBConstants.PROCESSING_DIR_NAME).resolve(doc.getUuid()))
                    || Files.exists(sharedRoot.resolve(PlanBConstants.SHARDS_DIR_NAME).resolve(doc.getUuid()));
        } catch (final Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // END OF DocumentActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public List<DocRef> list() {
        return store.list();
    }


    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    // -------------------------------------------------------------------------
    // SharedFileStoreDocStore
    // -------------------------------------------------------------------------

    @Override
    public Map<Path, Set<String>> getLiveSharedPathData() {
        final Map<Path, Set<String>> result = new HashMap<>();
        for (final DocRef docRef : store.list()) {
            final TracesDoc doc = store.readDocument(docRef);
            if (doc == null) {
                continue;
            }
            final String sharedPathStr = doc.getSharedPath();
            if (sharedPathStr == null || sharedPathStr.isBlank()) {
                continue;
            }
            result.computeIfAbsent(Path.of(sharedPathStr), k -> new HashSet<>())
                    .add(doc.getUuid());
        }
        return result;
    }
}
