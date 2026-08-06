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

package stroom.pathways.impl;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.dao.Count;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.ShardManager;
import stroom.util.concurrent.StripedLock;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

@Singleton
public class PathwaysProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);
    private static final ByteBuffer PROCESSED = ByteBuffer.allocate(0);

    private final PathwaysStore pathwaysStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final ByteBuffers byteBuffers;
    private final Path dbPath;
    private final Map<String, PathwaysDb> pathwaysDbMap = new ConcurrentHashMap<>();
    // Serialises creation of a given store without holding a lock on the map while we do it.
    private final StripedLock creationLocks = new StripedLock();
    private final PathwaySerde pathwaySerde;
    private final ShardManager shardManager;
    private final NodeInfo nodeInfo;

    @Inject
    public PathwaysProcessor(final PathwaysStore pathwaysStore,
                             final MessageReceiverFactory messageReceiverFactory,
                             final PathCreator pathCreator,
                             final ByteBuffers byteBuffers,
                             final PathwaySerde pathwaySerde,
                             final ShardManager shardManager,
                             final NodeInfo nodeInfo) {
        this.pathwaysStore = pathwaysStore;
        this.messageReceiverFactory = messageReceiverFactory;
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
        this.shardManager = shardManager;
        this.nodeInfo = nodeInfo;

        dbPath = pathCreator.toAppPath("${stroom.home}/pathways");
    }

    public void exec() {
        // Reclaim stores for docs that have been deleted since the last run.
        deleteOldStores();

        final List<DocRef> docRefs = pathwaysStore.list();
        for (final DocRef docRef : NullSafe.list(docRefs)) {
            final PathwaysDoc doc = pathwaysStore.readDocument(docRef);
            if (doc != null &&
                doc.getTracesDocRef() != null &&
                Objects.equals(doc.getProcessingNode(), nodeInfo.getThisNodeName())) {

                // Check that this is the node that trace stores are likely to be located.
                if (shardManager.isSnapshotNode()) {
                    throw new RuntimeException("Attempt to run pathways processing on different node to trace store");
                }

                // Load pathways DB for doc.
                final PathwaysDb pathwaysDb = getPathwaysDb(docRef, true);

                final DocRef infoFeed = doc.getInfoFeed();
                if (infoFeed != null && infoFeed.getName() != null) {
                    messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {

                        shardManager.get(doc.getTracesDocRef().getName(), db -> {
                            if (db instanceof final TraceDb traceDb) {

                                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                                    final TraceProcessor traceProcessor =
                                            new TraceProcessor(byteBuffers, pathwaySerde);
                                    traceDb.iterateTraces((traceId, function) ->
                                            traceProcessor.processTrace(writer,
                                                    pathwaysDb,
                                                    traceId,
                                                    function,
                                                    doc,
                                                    messageReceiver));
                                    writer.commit();
                                }
                            }
                            return null;
                        });
                    });
                }
            }
        }
    }

    private Path getPathwaysPath(final DocRef docRef) {
        return dbPath.resolve("pathways").resolve(PathSegmentUtil.requireSafeSegment(docRef.getUuid()));
    }

    /**
     * @param createIfNotExists Whether to create the store if we don't already have one. Pass
     *                          false from read only paths so that querying a doc that has
     *                          never been processed doesn't create a store as a side effect;
     *                          they get null and can report no results.
     */
    private PathwaysDb getPathwaysDb(final DocRef docRef, final boolean createIfNotExists) {
        final String uuid = docRef.getUuid();
        final PathwaysDb existing = pathwaysDbMap.get(uuid);
        if (existing != null) {
            return existing;
        }

        // Deliberately not ConcurrentHashMap.computeIfAbsent: the mapping function would run
        // while holding the lock for the key's bin, so opening the env would block lookups of
        // unrelated docs that hash to the same bin. Creation is serialised by a striped lock
        // outside the map instead, so concurrent callers for the same doc wait rather than
        // both building one. Same reasoning as ShardManager.getOrCreateShard, see gh-5689.
        final Lock lock = creationLocks.getLockForKey(uuid);
        lock.lock();
        try {
            // Another thread may have created it while we were waiting for the lock.
            final PathwaysDb created = pathwaysDbMap.get(uuid);
            if (created != null) {
                return created;
            }

            final Path processingPath = getPathwaysPath(docRef);
            if (!createIfNotExists && !Files.isDirectory(processingPath)) {
                return null;
            }

            try {
                Files.createDirectories(processingPath);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            final PathwaysDb pathwaysDb = PathwaysDb.create(processingPath, byteBuffers, false);
            pathwaysDbMap.put(uuid, pathwaysDb);
            return pathwaysDb;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes and deletes the stores of any docs that no longer exist. Nothing else reclaims
     * them, so without this a deleted doc leaves its pathway data on disk, and its env open,
     * for the life of the process.
     */
    private void deleteOldStores() {
        final Path pathwaysPath = dbPath.resolve("pathways");
        if (!Files.isDirectory(pathwaysPath)) {
            return;
        }

        try (final Stream<Path> stream = Files.list(pathwaysPath)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                final String uuid = dir.getFileName().toString();
                // Asked per dir rather than diffed against pathwaysStore.list(), so that a
                // list which is empty for any reason other than there being no docs can't
                // delete every store. Anything but a definite "not found" is left alone.
                boolean docDeleted = false;
                try {
                    docDeleted = pathwaysStore.readDocument(
                            DocRef.builder().type(PathwaysDoc.TYPE).uuid(uuid).build()) == null;
                } catch (final DocumentNotFoundException e) {
                    LOGGER.debug(e::getMessage, e);
                    docDeleted = true;
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> LogUtil.message(
                            "Error checking whether pathways doc '{}' still exists, keeping its store: {}",
                            uuid, e.getMessage()), e);
                }

                if (docDeleted) {
                    deleteStore(uuid, dir);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(() -> LogUtil.message("Error listing pathways dir '{}': {}",
                    FileUtil.getCanonicalPath(pathwaysPath), e.getMessage()), e);
        }
    }

    private void deleteStore(final String uuid, final Path dir) {
        // Take the creation lock so we can't race a caller that is opening this same store,
        // and remove from the map before closing so nobody can pick it up mid close.
        final Lock lock = creationLocks.getLockForKey(uuid);
        lock.lock();
        try {
            final PathwaysDb pathwaysDb = pathwaysDbMap.remove(uuid);
            if (pathwaysDb != null) {
                // Must close the env before deleting the dir it lives in.
                pathwaysDb.close();
            }
            LOGGER.info(() -> "Deleting pathways store for deleted doc: " + uuid);
            FileUtil.deleteDir(dir);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error deleting pathways store '{}': {}",
                    FileUtil.getCanonicalPath(dir), e.getMessage()), e);
        } finally {
            lock.unlock();
        }
    }

    public PathwayResultPage findPathways(final FindPathwayCriteria criteria) {
        final PathwaysDb pathwaysDb = getPathwaysDb(criteria.getDataSourceRef(), false);
        if (pathwaysDb == null) {
            // Nothing has been processed for this doc yet.
            return new PathwayResultPage(Collections.emptyList(), PageResponse
                    .builder()
                    .offset(criteria.getPageRequest().getOffset())
                    .length(0)
                    .total(0L)
                    .exact(true)
                    .build());
        }

        final Count count = new Count();
        final List<Pathway> list = new ArrayList<>();
        final PageRequest pageRequest = criteria.getPageRequest();
        pathwaysDb.getPathways().iterate((key, val) -> {
            boolean match = false;
            if (NullSafe.isNonEmptyString(criteria.getFilter())) {
                final String string = ByteBufferUtils.byteBufferToString(key);
                if (string.contains(criteria.getFilter())) {
                    match = true;
                }
            } else {
                match = true;
            }

            if (match) {
                final long pos = count.getAndIncrement();
                if (pos >= criteria.getPageRequest().getOffset() &&
                    pos < criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength()) {
                    list.add(pathwaySerde.readPathway(val));
                }
            }
        });

        final PageResponse pageResponse = PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(list.size())
                .total(count.get())
                .exact(true)
                .build();
        return new PathwayResultPage(list, pageResponse);
    }
}
