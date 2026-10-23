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

package stroom.pathways.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.fs.SharedFileStoreDocStore;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
public class PathwaysStoreImpl
        extends AbstractDocumentStore<PathwaysDoc>
        implements PathwaysStore, SharedFileStoreDocStore {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(PathwaysStoreImpl.class);

    private final Provider<ClusterLockService> clusterLockServiceProvider;
    // Asked for rather than held, because a traces store holds a reference to a pathways document and
    // this holds one back — taking either at construction would be a circle.
    private final Provider<TracesDocStore> tracesDocStoreProvider;

    @Inject
    public PathwaysStoreImpl(final StoreFactory storeFactory,
                             final SecurityContext securityContext,
                             final PathwaysSerialiser serialiser,
                             final Provider<ClusterLockService> clusterLockServiceProvider,
                             final Provider<TracesDocStore> tracesDocStoreProvider) {
        super(storeFactory,
                securityContext,
                serialiser,
                PathwaysDoc.TYPE,
                PathwaysDoc::builder,
                PathwaysDoc::copy);
        this.clusterLockServiceProvider = clusterLockServiceProvider;
        this.tracesDocStoreProvider = tracesDocStoreProvider;
    }

    /**
     * Registers this document's reference to the feed it writes findings to, so it appears on the
     * Dependencies screen, is reported when the feed is deleted, and is rewritten when a copy or an
     * import lands the feed under a different uuid.
     *
     * <p>This does nothing for a rename. {@link stroom.docref.DocRef} equality is on uuid alone, so a
     * renamed target is the same reference and nothing here is rewritten; the Dependencies screen gets
     * the new name from {@code DocDependencyService.propagateName}, and the name stored inside this
     * document stays as it was.
     */
    @Override
    protected DependencyRemapFunction<PathwaysDoc> getDependencyRemapFunction() {
        return (doc, remapper) -> doc.getInfoFeed() == null
                ? doc
                : doc.copy()
                        .infoFeed(remapper.remap(doc.getInfoFeed()))
                        .build();
    }

    /**
     * Refuses a change to the shared path or the shard count once either has data under it.
     *
     * <p>Both decide where a pathway lives: the path is the root of the model and the queue, and the
     * count is what the operation name is hashed against. Changing either leaves everything already
     * written where nothing will look for it again — silently, because a search still finds the names
     * and then asks the wrong shard for them.
     *
     * <p>The editor locks both fields for the same reason. This is the check behind that, for an
     * import or a direct API call that never saw the screen.
     */
    @Override
    public PathwaysDoc writeDocument(final PathwaysDoc document) {
        checkSharedFileStoreUnchanged(document);
        return super.writeDocument(document);
    }

    private void checkSharedFileStoreUnchanged(final PathwaysDoc document) {
        final PathwaysDoc oldDoc;
        try {
            oldDoc = getStore().readDocument(document.asDocRef());
        } catch (final DocumentNotFoundException e) {
            // Nothing is being written over, so there is nothing to preserve. Reached whenever this
            // node has not held the document before — the ordinary case for an import.
            return;
        }
        if (oldDoc == null || !hasSharedFileStoreData(oldDoc)) {
            return;
        }
        final SharedFileStoreSettings oldSettings = oldDoc.getSharedFileStore();
        final SharedFileStoreSettings newSettings = document.getSharedFileStore();
        if (newSettings == null
            || !Objects.equals(oldSettings.getSharedPath(), newSettings.getSharedPath())) {
            throw new EntityServiceException(
                    "Cannot change the shared path: pathways have already been written under it.");
        }
        if (oldSettings.getShardCount() != newSettings.getShardCount()) {
            throw new EntityServiceException(
                    "Cannot change the shard count: pathways have already been written to this store.");
        }
    }

    @Override
    public boolean hasSharedFileStoreData(final String uuid) {
        return hasSharedFileStoreData(readDocument(
                DocRef.builder().uuid(uuid).type(PathwaysDoc.TYPE).build()));
    }

    // A model or a queue under this document's shared path. Both are enough to pin the settings: the
    // queue holds traces already routed by the current shard count, and the model holds pathways
    // already placed by it.
    private boolean hasSharedFileStoreData(final PathwaysDoc doc) {
        final SharedFileStoreSettings settings = doc == null
                ? null
                : doc.getSharedFileStore();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())) {
            return false;
        }
        final Path sharedRoot;
        try {
            sharedRoot = Path.of(settings.getSharedPath());
        } catch (final InvalidPathException e) {
            // Not a path at all, so it names nowhere data could be.
            LOGGER.warn(() -> "Not a usable shared file store path: " + settings.getSharedPath());
            return false;
        }
        return directoryExists(sharedRoot.resolve(PlanBConstants.SHARDS_DIR_NAME)
                       .resolve(doc.getUuid()))
               || directoryExists(sharedRoot.resolve(PlanBConstants.QUEUE_DIR_NAME)
                       .resolve(doc.getUuid()));
    }

    // Fails closed, which Files.isDirectory would not: it answers "not there" and "could not tell"
    // identically, and taking "could not tell" for "no data" would unlock settings that orphan a model.
    // A change refused because the mount was unreachable is recoverable; one allowed on a false
    // negative is not.
    private static boolean directoryExists(final Path path) {
        try {
            Files.readAttributes(path, BasicFileAttributes.class);
            return true;
        } catch (final NoSuchFileException e) {
            return false;
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn(() -> "Could not check " + path + ", so treating it as holding data: "
                              + e.getMessage());
            return true;
        }
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);

        // Clean up the per-shard cluster locks this document's processing takes. Lock rows accumulate
        // in cluster_lock as shards are worked and are never removed automatically, so they have to go
        // with the document or they stay for good.
        if (docRef != null && docRef.getUuid() != null) {
            try {
                final ClusterLockService clusterLockService = clusterLockServiceProvider.get();
                clusterLockService.deleteLocks(PathwaysProcessor.lockPrefix(docRef.getUuid()));
            } catch (final Exception e) {
                // Ignore lock deletion failures to avoid failing the document delete itself.
            }
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Every pathways document's shared path, whoever is asking.
     *
     * <p>Deliberately unchecked — {@link stroom.planb.impl.fs.SharedFileStoreCleaner} trashes whatever
     * is missing from this answer, so filtering by what the caller may see would delete live data.
     * For the same reason it throws rather than skipping a listed document it cannot read: a run that
     * cannot tell a live document from an orphan has to be abandoned, not guessed at. A document
     * deleted between the list and the read costs one housekeeping run, and is an orphan by the next.
     */
    @Override
    public Map<Path, Set<String>> getLiveSharedPathData() {
        final Map<Path, Set<String>> result = new HashMap<>();
        for (final DocRef docRef : getStore().list()) {
            final PathwaysDoc doc = getStore().readDocument(docRef);
            if (doc == null) {
                throw new EntityServiceException("Could not read listed pathways document " + docRef);
            }
            final SharedFileStoreSettings settings = doc.getSharedFileStore();
            if (settings != null && NullSafe.isNonBlankString(settings.getSharedPath())) {
                result.computeIfAbsent(Path.of(settings.getSharedPath()), k -> new HashSet<>())
                        .add(doc.getUuid());
            }
        }
        return result;
    }
}
