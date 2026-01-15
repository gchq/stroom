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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pool API into open index shards.
 */
@Singleton
public class IndexShardManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardManager.class);

    private final IndexStore indexStore;
    private final IndexShardDao indexShardDao;
    private final IndexShardWriterCache indexShardWriterCache;
    private final NodeInfo nodeInfo;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;
    private final AtomicBoolean deletingShards = new AtomicBoolean();

    @Inject
    IndexShardManager(final IndexStore indexStore,
                      final IndexShardDao indexShardDao,
                      final IndexShardWriterCache indexShardWriterCache,
                      final NodeInfo nodeInfo,
                      final Executor executor,
                      final TaskContextFactory taskContextFactory,
                      final SecurityContext securityContext,
                      final PathCreator pathCreator) {
        this.indexStore = indexStore;
        this.indexShardDao = indexShardDao;
        this.indexShardWriterCache = indexShardWriterCache;
        this.nodeInfo = nodeInfo;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
    }

    /**
     * Delete anything that has been marked to delete
     */
    public void deleteFromDisk() {
        securityContext.secure(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            if (deletingShards.compareAndSet(false, true)) {
                try {
                    final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
                    criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
                    criteria.getIndexShardStatusSet().add(IndexShardStatus.DELETED);
                    final ResultPage<IndexShard> shards = indexShardDao.find(criteria);
                    if (NullSafe.test(shards, shards2 -> shards2.size() > 0)) {
                        deleteShardsFromDisk(indexShardWriterCache, shards);
                    } else {
                        LOGGER.debug("No matching shards to delete, criteria: {}", criteria);
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    deletingShards.set(false);
                }
            } else {
                LOGGER.debug("Another thread is deleting shards, we will just drop out quietly");
            }
        });
    }

    private void deleteShardsFromDisk(final IndexShardWriterCache indexShardWriterCache,
                                      final ResultPage<IndexShard> shards) {
        final Runnable runnable = taskContextFactory.context(
                "Delete Logically Deleted Shards",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    try {
                        taskContext.info(() -> LogUtil.message("Deleting {} Logically Deleted Shard{}...",
                                shards.size(), StringUtil.pluralSuffix(shards.size())));

                        LOGGER.logDurationIfDebugEnabled(() -> {
                            final Iterator<IndexShard> iter = shards.getValues().iterator();
                            while (!Thread.currentThread().isInterrupted() && iter.hasNext()) {
                                final IndexShard shard = iter.next();
                                final Optional<IndexShardWriter> optional =
                                        indexShardWriterCache.getIfPresent(shard.getId());
                                try {
                                    if (optional.isPresent()) {
                                        LOGGER.debug(() ->
                                                "deleteShardsFromDisk() - Unable to delete index " +
                                                "shard " + shard.getId() + " as it is currently " +
                                                "in use");
                                    } else {
                                        deleteFromDisk(shard);
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }
                            }
                        }, "deleteShardsFromDisk()");
                    } finally {
                        deletingShards.set(false);
                    }
                });

        // In tests we don't have a task manager.
        NullSafe.consumeOr(
                executor,
                ex -> ex.execute(runnable),
                runnable);
    }

    private void deleteFromDisk(final IndexShard shard) {
        try {
            // Find the index shard dir.
            final Path dir = IndexShardUtil.getIndexPath(shard, pathCreator);
            LOGGER.debug(() -> LogUtil.message("deleteFromDisk() - shard ID: {}, dir: '{}'",
                    shard.getId(), LogUtil.path(dir)));

            // See if there are any files in the directory.
            if (!Files.isDirectory(dir) || FileUtil.deleteDir(dir)) {
                // The directory either doesn't exist or we have
                // successfully deleted it so delete this index
                // shard from the database.
                if (indexShardDao != null) {
                    indexShardDao.delete(shard.getId());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public Long performAction(final FindIndexShardCriteria criteria, final IndexShardAction action) {
        return securityContext.secureResult(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            final String thisNodeName = nodeInfo.getThisNodeName();
            final ResultPage<IndexShard> shards = indexShardDao.find(criteria);

            // Only perform actions on shards owned by this node.
            final List<IndexShard> ownedShards = shards
                    .stream()
                    .filter(indexShard -> thisNodeName.equals(indexShard.getNodeName()))
                    .toList();
            return performAction(ownedShards, action);
        });
    }

    private long performAction(final List<IndexShard> ownedShards, final IndexShardAction action) {
        final AtomicLong shardCount = new AtomicLong();
        if (!ownedShards.isEmpty()) {
            taskContextFactory.context(
                    "Index Shard Manager",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    parentTaskContext -> {
                        parentTaskContext.info(() -> action.getActivity() + " index shards");

                        // Create an atomic integer to count the number of index shard writers yet to complete the
                        // specified action.
                        final AtomicInteger remaining = new AtomicInteger(ownedShards.size());

                        // Create a scheduled executor for us to continually log index shard writer action progress.
                        try (final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
                            // Start logging action progress.
                            executor.scheduleAtFixedRate(
                                    () -> LOGGER.info(() ->
                                            "Waiting for " + remaining.get() + " index shards to " + action.getName()),
                                    10,
                                    10,
                                    TimeUnit.SECONDS);

                            // Perform action on all of the index shard writers in parallel.
                            ownedShards.parallelStream().forEach(shard -> {
                                try {
                                    // We use a child tak context here to create child messages in the UI but also to
                                    // ensure the task is performed in the context of the parent user.
                                    taskContextFactory.childContext(parentTaskContext,
                                            "Index Shard Manager",
                                            TerminateHandlerFactory.NOOP_FACTORY,
                                            taskContext -> {
                                                taskContext.info(() -> action.getActivity() +
                                                                       " index shard: " +
                                                                       shard.getId());
                                                switch (action) {
                                                    case FLUSH:
                                                        shardCount.incrementAndGet();
                                                        flush(shard);
                                                        break;
                                                    case DELETE:
                                                        shardCount.incrementAndGet();
                                                        delete(shard);
                                                        break;
                                                }
                                            }).run();
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }

                                remaining.getAndDecrement();
                            });

                            // Shut down the progress logging executor.
                            executor.shutdown();
                        }

                        LOGGER.info(() -> "Finished " +
                                          action.getActivity().toLowerCase(Locale.ROOT) +
                                          " index shards");
                    }).run();
        }

        return shardCount.get();
    }

    private void flush(final IndexShard indexShard) {
        indexShardWriterCache.flush(indexShard.getId());
    }

    private void delete(final IndexShard indexShard) {
        final DocRef indexDocRef = DocRef
                .builder()
                .type(LuceneIndexDoc.TYPE)
                .uuid(indexShard.getIndexUuid())
                .build();
        if (!securityContext.hasDocumentPermission(indexDocRef,
                DocumentPermission.DELETE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to delete index shard");
        } else {
            indexShardWriterCache.delete(indexShard.getId());
        }
    }

    public void checkRetention() {
        taskContextFactory.current().info(() -> "Checking index shard retention");
        securityContext.secure(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
            criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
            final ResultPage<IndexShard> shards = indexShardDao.find(criteria);
            for (final IndexShard shard : shards.getValues()) {
                checkRetention(shard);
            }
        });
    }

    private void checkRetention(final IndexShard shard) {
        try {
            // Delete this shard if it is older than the retention age.
            final LuceneIndexDoc index = indexStore.readDocument(
                    new DocRef(LuceneIndexDoc.TYPE, shard.getIndexUuid()));
            if (index == null) {
                // If there is no associated index then delete the shard.
                indexShardWriterCache.delete(shard.getId());

            } else {
                final Integer retentionDayAge = index.getRetentionDayAge();
                final Long partitionToTime = shard.getPartitionToTime();
                if (retentionDayAge != null
                    && partitionToTime != null
                    && !IndexShardStatus.DELETED.equals(shard.getStatus())) {
                    // See if this index shard is older than the index retention
                    // period.
                    final long retentionTime = ZonedDateTime.now(ZoneOffset.UTC)
                            .minusDays(retentionDayAge)
                            .toInstant()
                            .toEpochMilli();

                    if (partitionToTime < retentionTime) {
                        indexShardWriterCache.delete(shard.getId());
                    }
                }
            }
        } catch (final DocumentNotFoundException e) {
            // If there is no associated index then delete the shard.
            indexShardWriterCache.delete(shard.getId());

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public enum IndexShardAction {
        FLUSH("flush", "Flushing"),
        DELETE("delete", "Deleting");

        private final String name;
        private final String activity;

        IndexShardAction(final String name, final String activity) {
            this.name = name;
            this.activity = activity;
        }

        public String getName() {
            return name;
        }

        public String getActivity() {
            return activity;
        }
    }
}
