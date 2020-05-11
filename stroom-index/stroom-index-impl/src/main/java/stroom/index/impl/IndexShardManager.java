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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * Pool API into open index shards.
 */
@Singleton
public class IndexShardManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardManager.class);

    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final Provider<IndexShardWriterCache> indexShardWriterCacheProvider;
    private final NodeInfo nodeInfo;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;

    private final StripedLock shardUpdateLocks = new StripedLock();
    private final AtomicBoolean deletingShards = new AtomicBoolean();

    private final Map<IndexShardStatus, Set<IndexShardStatus>> allowedStateTransitions = new HashMap<>();

    @Inject
    IndexShardManager(final IndexStore indexStore,
                      final IndexShardService indexShardService,
                      final Provider<IndexShardWriterCache> indexShardWriterCacheProvider,
                      final NodeInfo nodeInfo,
                      final Executor executor,
                      final TaskContextFactory taskContextFactory,
                      final SecurityContext securityContext) {
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
        this.indexShardWriterCacheProvider = indexShardWriterCacheProvider;
        this.nodeInfo = nodeInfo;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;

        // Ensure all but deleted and corrupt states can be set to closed on clean.
        allowedStateTransitions.put(IndexShardStatus.NEW, Set.of(IndexShardStatus.OPENING, IndexShardStatus.CLOSED, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT));
        allowedStateTransitions.put(IndexShardStatus.OPENING, Set.of(IndexShardStatus.OPEN, IndexShardStatus.CLOSED, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT));
        allowedStateTransitions.put(IndexShardStatus.OPEN, Set.of(IndexShardStatus.CLOSING, IndexShardStatus.CLOSED, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT));
        allowedStateTransitions.put(IndexShardStatus.CLOSING, Set.of(IndexShardStatus.CLOSED, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT));
        allowedStateTransitions.put(IndexShardStatus.CLOSED, Set.of(IndexShardStatus.OPENING, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT));
        allowedStateTransitions.put(IndexShardStatus.DELETED, Collections.emptySet());
        allowedStateTransitions.put(IndexShardStatus.CORRUPT, Collections.singleton(IndexShardStatus.DELETED));
    }

    /**
     * Delete anything that has been marked to delete
     */
    public void deleteFromDisk() {
        securityContext.secure(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            if (deletingShards.compareAndSet(false, true)) {
                try {
                    final IndexShardWriterCache indexShardWriterCache = indexShardWriterCacheProvider.get();

                    final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
                    criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
                    criteria.getIndexShardStatusSet().add(IndexShardStatus.DELETED);
                    final ResultPage<IndexShard> shards = indexShardService.find(criteria);

                    final Runnable runnable = taskContextFactory.context("Delete Logically Deleted Shards", taskContext -> {
                        try {
                            taskContext.info(() -> "Deleting Logically Deleted Shards...");

                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            final Iterator<IndexShard> iter = shards.getValues().iterator();
                            while (!Thread.currentThread().isInterrupted() && iter.hasNext()) {
                                final IndexShard shard = iter.next();
                                final IndexShardWriter writer = indexShardWriterCache.getWriterByShardId(shard.getId());
                                try {
                                    if (writer != null) {
                                        LOGGER.debug(() -> "deleteLogicallyDeleted() - Unable to delete index shard " + shard.getId() + " as it is currently in use");
                                    } else {
                                        deleteFromDisk(shard);
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }
                            }
                            LOGGER.debug(() -> "deleteLogicallyDeleted() - Completed in " + logExecutionTime);
                        } finally {
                            deletingShards.set(false);
                        }
                    });

                    // In tests we don't have a task manager.
                    if (executor == null) {
                        runnable.run();
                    } else {
                        executor.execute(runnable);
                    }

                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    deletingShards.set(false);
                }
            }
        });
    }

    private void deleteFromDisk(final IndexShard shard) {
        try {
            // Find the index shard dir.
            final Path dir = IndexShardUtil.getIndexPath(shard);

            // See if there are any files in the directory.
            if (!Files.isDirectory(dir) || FileUtil.deleteDir(dir)) {
                // The directory either doesn't exist or we have
                // successfully deleted it so delete this index
                // shard from the database.
                if (indexShardService != null) {
                    indexShardService.delete(shard);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public Long performAction(final FindIndexShardCriteria criteria, final IndexShardAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            final String thisNodeName = nodeInfo.getThisNodeName();
            final ResultPage<IndexShard> shards = indexShardService.find(criteria);

            // Only perform actions on shards owned by this node.
            final List<IndexShard> ownedShards = shards
                    .stream()
                    .filter(indexShard -> thisNodeName.equals(indexShard.getNodeName()))
                    .collect(Collectors.toList());
            return performAction(ownedShards, action);
        });
    }

    private long performAction(final List<IndexShard> ownedShards, final IndexShardAction action) {
        final AtomicLong shardCount = new AtomicLong();
        if (ownedShards.size() > 0) {
            final IndexShardWriterCache indexShardWriterCache = indexShardWriterCacheProvider.get();

            // Create an atomic integer to count the number of index shard writers yet to complete the specified action.
            final AtomicInteger remaining = new AtomicInteger(ownedShards.size());

            // Create a scheduled executor for us to continually log index shard writer action progress.
            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            // Start logging action progress.
            executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + remaining.get() + " index shards to " + action.getName()), 10, 10, TimeUnit.SECONDS);

            // Perform action on all of the index shard writers in parallel.
            ownedShards.parallelStream().forEach(shard -> {
                try {
                    switch (action) {
                        case FLUSH:
                            shardCount.incrementAndGet();
                            indexShardWriterCache.flush(shard.getId());
                            break;
                        case DELETE:
                            shardCount.incrementAndGet();
                            indexShardWriterCache.delete(shard.getId());
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }

                remaining.getAndDecrement();
            });

            // Shut down the progress logging executor.
            executor.shutdown();

            LOGGER.info(() -> "Finished " + action.getActivity() + " index shards");
        }

        return shardCount.get();
    }

    public void checkRetention() {
        securityContext.secure(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
            criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
            final ResultPage<IndexShard> shards = indexShardService.find(criteria);
            for (final IndexShard shard : shards.getValues()) {
                checkRetention(shard);
            }
        });
    }

    private void checkRetention(final IndexShard shard) {
        try {
            // Delete this shard if it is older than the retention age.
            final IndexDoc index = indexStore.readDocument(new DocRef(IndexDoc.DOCUMENT_TYPE, shard.getIndexUuid()));
            if (index == null) {
                // If there is no associated index then delete the shard.
                setStatus(shard.getId(), IndexShardStatus.DELETED);

            } else {
                final Integer retentionDayAge = index.getRetentionDayAge();
                final Long partitionToTime = shard.getPartitionToTime();
                if (retentionDayAge != null && partitionToTime != null && !IndexShardStatus.DELETED.equals(shard.getStatus())) {
                    // See if this index shard is older than the index retention
                    // period.
                    final long retentionTime = ZonedDateTime.now(ZoneOffset.UTC).minusDays(retentionDayAge).toInstant().toEpochMilli();

                    if (partitionToTime < retentionTime) {
                        setStatus(shard.getId(), IndexShardStatus.DELETED);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public IndexShard load(final long indexShardId) {
        return securityContext.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            // Allow the thing to run without a service (e.g. benchmark mode)
            if (indexShardService != null) {
                final Lock lock = shardUpdateLocks.getLockForKey(indexShardId);
                lock.lock();
                try {
                    return indexShardService.loadById(indexShardId);
                } finally {
                    lock.unlock();
                }
            }

            return null;
        });
    }

    public void setStatus(final long indexShardId, final IndexShardStatus status) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (indexShardService != null) {
            final Lock lock = shardUpdateLocks.getLockForKey(indexShardId);
            lock.lock();
            try {
                final IndexShard indexShard = indexShardService.loadById(indexShardId);
                if (indexShard != null) {
                    // Only allow certain state transitions.
                    final Set<IndexShardStatus> allowed = allowedStateTransitions.get(indexShard.getStatus());
                    if (allowed == null) {
                        throw new RuntimeException("No state transitions are defined for " + indexShard.getStatus());
                    } else {
                        if (allowed.contains(status)) {
                            indexShardService.setStatus(indexShard.getId(), status);
                        } else {
                            throw new RuntimeException("State transition from " + indexShard.getStatus() + " to " + status + " was attempted but is not allowed");
                        }
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            } finally {
                lock.unlock();
            }
        }
    }

    public void update(final long indexShardId,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (indexShardService != null) {
            final Lock lock = shardUpdateLocks.getLockForKey(indexShardId);
            lock.lock();
            try {
                indexShardService.update(indexShardId,
                        documentCount,
                        commitDurationMs,
                        commitMs,
                        fileSize);
            } finally {
                lock.unlock();
            }
        }
    }

    public enum IndexShardAction {
        FLUSH("flush", "flushing"), DELETE("delete", "deleting");

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
