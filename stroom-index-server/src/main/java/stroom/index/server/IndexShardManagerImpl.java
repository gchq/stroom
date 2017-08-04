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
 *
 */

package stroom.index.server;

import org.springframework.stereotype.Component;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.security.Secured;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.thread.ThreadScopeRunnable;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

/**
 * Pool API into open index shards.
 */
@Component
@Secured(IndexShard.MANAGE_INDEX_SHARDS_PERMISSION)
public class IndexShardManagerImpl implements IndexShardManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardManagerImpl.class);

    private final IndexShardService indexShardService;
    private final Provider<IndexShardWriterCache> indexShardWriterCacheProvider;
    private final NodeCache nodeCache;
    private final TaskManager taskManager;

    private final StripedLock shardUpdateLocks = new StripedLock();
    private final AtomicBoolean deletingShards = new AtomicBoolean();

    private final Map<IndexShardStatus, Set<IndexShardStatus>> allowedStateTransitions = new HashMap<>();


    @Inject
    IndexShardManagerImpl(final IndexShardService indexShardService, final Provider<IndexShardWriterCache> indexShardWriterCacheProvider, final NodeCache nodeCache, final TaskManager taskManager) {
        this.indexShardService = indexShardService;
        this.indexShardWriterCacheProvider = indexShardWriterCacheProvider;
        this.nodeCache = nodeCache;
        this.taskManager = taskManager;

        allowedStateTransitions.put(IndexShardStatus.CLOSED, new HashSet<>(Arrays.asList(IndexShardStatus.OPEN, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT)));
        allowedStateTransitions.put(IndexShardStatus.OPEN, new HashSet<>(Arrays.asList(IndexShardStatus.CLOSED, IndexShardStatus.DELETED, IndexShardStatus.CORRUPT)));
        allowedStateTransitions.put(IndexShardStatus.DELETED, Collections.emptySet());
        allowedStateTransitions.put(IndexShardStatus.CORRUPT, Collections.singleton(IndexShardStatus.DELETED));
    }

    @Override
    @StroomShutdown
    public void shutdown() {
        // Close all currently used writers and clear the cache.
        indexShardWriterCacheProvider.get().clear();
    }

    /**
     * Delete anything that has been marked to delete
     */
    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Index Shard Delete", description = "Job to delete index shards from disk that have been marked as deleted")
    @Override
    public void deleteFromDisk() {
        if (deletingShards.compareAndSet(false, true)) {
            try {
                final IndexShardWriterCache indexShardWriterCache = indexShardWriterCacheProvider.get();

                final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
                criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
                criteria.getFetchSet().add(Index.ENTITY_TYPE);
                criteria.getFetchSet().add(Node.ENTITY_TYPE);
                criteria.getIndexShardStatusSet().add(IndexShardStatus.DELETED);
                final List<IndexShard> shards = indexShardService.find(criteria);

                final GenericServerTask task = GenericServerTask.create("Delete Logically Deleted Shards", "Deleting Logically Deleted Shards...");
                final Runnable runnable = () -> {
                    try {
                        final LogExecutionTime logExecutionTime = new LogExecutionTime();
                        final Iterator<IndexShard> iter = shards.iterator();
                        while (!task.isTerminated() && iter.hasNext()) {
                            final IndexShard shard = iter.next();
                            final IndexShardWriter writer = indexShardWriterCache.getQuiet(shard.getId());
                            try {
                                if (writer != null) {
                                    LOGGER.debug(() -> "deleteLogicallyDeleted() - Unable to delete index shard " + shard.getId() + " as it is currently in use");
                                } else {
                                    deleteFromDisk(shard);
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                        LOGGER.debug(() -> "deleteLogicallyDeleted() - Completed in " + logExecutionTime);
                    } finally {
                        deletingShards.set(false);
                    }
                };

                // In tests we don't have a task manager.
                if (taskManager == null) {
                    runnable.run();
                } else {
                    task.setRunnable(runnable);
                    taskManager.execAsync(task);
                }

            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
                deletingShards.set(false);
            }
        }
    }

    private void deleteFromDisk(final IndexShard shard) {
        try {
            // Find the index shard dir.
            final Path dir = IndexShardUtil.getIndexPath(shard);

            // See if there are any files in the directory.
            if (!Files.isDirectory(dir) || FileSystemUtil.deleteDirectory(dir)) {
                // The directory either doesn't exist or we have
                // successfully deleted it so delete this index
                // shard from the database.
                if (indexShardService != null) {
                    indexShardService.delete(shard);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
        }
    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findFlush(final FindIndexShardCriteria criteria) {
        return performAction(criteria, IndexShardAction.FLUSH);
    }

//    /**
//     * This is called when a user wants to close some index shards or during shutdown.
//     * This method returns the number of index shard writers that have been closed.
//     */
//    @Override
//    public Long findClose(final FindIndexShardCriteria criteria) {
//        return performAction(criteria, IndexShardAction.CLOSE);
//    }

    /**
     * This is called by the node command service and is a result of a user
     * interaction. The map is synchronised so no writers will be created or
     * destroyed while this is called.
     */
    @Override
    public Long findDelete(final FindIndexShardCriteria criteria) {
        return performAction(criteria, IndexShardAction.DELETE);
    }

    private Long performAction(final FindIndexShardCriteria criteria, final IndexShardAction action) {
        final List<IndexShard> shards = indexShardService.find(criteria);
        return performAction(shards, action);
    }

    private long performAction(final List<IndexShard> shards, final IndexShardAction action) {
        final AtomicLong shardCount = new AtomicLong();

        if (shards.size() > 0) {
            final IndexShardWriterCache indexShardWriterCache = indexShardWriterCacheProvider.get();

            // Create an atomic integer to count the number of index shard writers yet to complete the specified action.
            final AtomicInteger remaining = new AtomicInteger(shards.size());

            // Create a scheduled executor for us to continually log index shard writer action progress.
            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            // Start logging action progress.
            executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + remaining.get() + " index shards to " + action.getName()), 10, 10, TimeUnit.SECONDS);

            // Perform action on all of the index shard writers in parallel.
            shards.parallelStream().forEach(shard -> new ThreadScopeRunnable() {
                @Override
                protected void exec() {
                    try {
                        switch (action) {
                            case FLUSH:
                                final IndexShardWriter indexShardWriter = indexShardWriterCache.getQuiet(shard.getId());
                                if (indexShardWriter != null) {
                                    LOGGER.debug(() -> action.getActivity() + " index shard " + shard.getId());
                                    shardCount.incrementAndGet();
                                    indexShardWriter.flush();
                                }
                                break;
//                                case CLOSE:
//                                    indexShardWriter.close();
//                                    break;
                            case DELETE:
                                shardCount.incrementAndGet();
                                setStatus(shard.getId(), IndexShardStatus.DELETED);
                                break;
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }

                    remaining.getAndDecrement();
                }
            }.run());

            // Shut down the progress logging executor.
            executor.shutdown();

            LOGGER.info(() -> "Finished " + action.getActivity() + " index shards");
        }

        return shardCount.get();
    }

    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Shard Retention", description = "Job to set index shards to have a status of deleted that have past their retention period")
    @Override
    public void checkRetention() {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        final List<IndexShard> shards = indexShardService.find(criteria);
        for (final IndexShard shard : shards) {
            checkRetention(shard);
        }
    }

    private void checkRetention(final IndexShard shard) {
        try {
            // Delete this shard if it is older than the retention age.
            final Index index = shard.getIndex();
            if (index.getRetentionDayAge() != null && shard.getPartitionToTime() != null) {
                // See if this index shard is older than the index retention
                // period.
                final long retentionTime = ZonedDateTime.now(ZoneOffset.UTC).minusDays(index.getRetentionDayAge()).toInstant().toEpochMilli();
                final long shardAge = shard.getPartitionToTime();

                if (shardAge < retentionTime) {
                    setStatus(shard.getId(), IndexShardStatus.DELETED);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
        }
    }

    @Override
    public IndexShard load(final long indexShardId) {
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
    }

    @Override
    public void setStatus(final long indexShardId, final IndexShardStatus status) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (indexShardService != null) {
            final Lock lock = shardUpdateLocks.getLockForKey(indexShardId);
            lock.lock();
            try {
                final IndexShard indexShard = indexShardService.loadById(indexShardId);

                // Only allow certain state transitions.
                final Set<IndexShardStatus> allowed = allowedStateTransitions.get(indexShard.getStatus());
                if (allowed.contains(status)) {
                    indexShard.setStatus(status);
                    indexShardService.save(indexShard);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void update(final long indexShardId, final Integer documentCount, final Long commitDurationMs, final Long commitMs, final Long fileSize) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (indexShardService != null) {
            final Lock lock = shardUpdateLocks.getLockForKey(indexShardId);
            lock.lock();
            try {
                final IndexShard indexShard = indexShardService.loadById(indexShardId);

                if (documentCount != null) {
                    indexShard.setDocumentCount(documentCount);
                    indexShard.setCommitDocumentCount(documentCount - indexShard.getDocumentCount());

                    // Output some debug so we know how long commits are taking.
                    LOGGER.debug(() -> {
                        final String durationString = ModelStringUtil.formatDurationString(commitDurationMs);
                        return "Documents written since last update " + (documentCount - indexShard.getDocumentCount()) + " ("
                                + durationString + ")";
                    });
                }
                if (commitDurationMs != null) {
                    indexShard.setCommitDurationMs(commitDurationMs);
                }
                if (commitMs != null) {
                    indexShard.setCommitMs(commitMs);
                }
                if (fileSize != null) {
                    indexShard.setFileSize(fileSize);
                }

                indexShardService.save(indexShard);
            } finally {
                lock.unlock();
            }
        }
    }

    private enum IndexShardAction {
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
