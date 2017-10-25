/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.server;

import org.apache.lucene.store.LockObtainFailedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.Node;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.spring.StroomStartup;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Profile(StroomSpringProfiles.PROD)
public class IndexShardWriterCacheImpl implements IndexShardWriterCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterCacheImpl.class);

    private final NodeCache nodeCache;
    private final IndexShardService indexShardService;
    private final IndexConfigCache indexConfigCache;
    private final IndexShardManager indexShardManager;
    private final StroomPropertyService stroomPropertyService;

    // We have to keep a separate map of writers so that readers can get them without touching Ehcache.
    // Touching Ehcache causes expired elements to be evicted which causes them to be destroyed. Destruction happens in
    // the same thread and can take a while to commit and close index writers. This can seriously impact searching if
    // search has to wait for writers to be destroyed.
    private final Map<Long, IndexShardWriter> openWritersByShardId = new ConcurrentHashMap<>();

    private final Map<IndexShardKey, IndexShardWriter> openWritersByShardKey = new ConcurrentHashMap<>();
    private volatile Settings settings;

    private final AtomicLong closing = new AtomicLong();
    private final Runner asyncRunner;
    private final Runner syncRunner;
    private final TaskContext taskContext;

    @Inject
    public IndexShardWriterCacheImpl(final NodeCache nodeCache,
                                     final IndexShardService indexShardService,
                                     final StroomPropertyService stroomPropertyService,
                                     final IndexConfigCache indexConfigCache,
                                     final IndexShardManager indexShardManager,
                                     final ExecutorProvider executorProvider,
                                     final TaskContext taskContext) {
        this.nodeCache = nodeCache;
        this.indexShardService = indexShardService;
        this.stroomPropertyService = stroomPropertyService;
        this.indexConfigCache = indexConfigCache;
        this.indexShardManager = indexShardManager;

        final ThreadPool threadPool = new ThreadPoolImpl("Index Shard Writer Cache", 3, 0, Integer.MAX_VALUE);
        final Executor executor = executorProvider.getExecutor(threadPool);
        asyncRunner = new AsyncRunner(executor);
        syncRunner = new SyncRunner();

        this.taskContext = taskContext;
    }

    @Override
    public IndexShardWriter getWriterByShardId(final Long indexShardId) {
        return openWritersByShardId.get(indexShardId);
    }

    @Override
    public IndexShardWriter getWriterByShardKey(final IndexShardKey indexShardKey) {
        return openWritersByShardKey.computeIfAbsent(indexShardKey, k -> {
            // Make sure we have room to add a new writer.
            makeRoom();

            IndexShardWriter indexShardWriter = openExistingShard(k);
            if (indexShardWriter == null) {
                indexShardWriter = openNewShard(k);
            }

            if (indexShardWriter == null) {
                throw new IndexException("Unable to create writer for " + indexShardKey);
            }

            openWritersByShardId.put(indexShardWriter.getIndexShardId(), indexShardWriter);

            return indexShardWriter;
        });
    }

    /**
     * Finds an existing shard for the specified key and opens a writer for it.
     */
    private IndexShardWriter openExistingShard(final IndexShardKey indexShardKey) {
        // Get all index shards that are owned by this node.
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getIndexIdSet().add(indexShardKey.getIndex());
        criteria.getPartition().setString(indexShardKey.getPartition());
        final List<IndexShard> list = indexShardService.find(criteria);
        for (final IndexShard indexShard : list) {
            // Look for non deleted, non full, non corrupt index shards.
            if (IndexShardStatus.CLOSED.equals(indexShard.getStatus())
                    && indexShard.getDocumentCount() < indexShard.getIndex().getMaxDocsPerShard()) {
                final IndexShardWriter indexShardWriter = openWriter(indexShardKey, indexShard);
                if (indexShardWriter != null) {
                    return indexShardWriter;
                }
            }
        }

        return null;
    }

    /**
     * Creates a new index shard writer for the specified key and opens a writer for it.
     */
    private IndexShardWriter openNewShard(final IndexShardKey indexShardKey) {
        final IndexShard indexShard = indexShardService.createIndexShard(indexShardKey, nodeCache.getDefaultNode());
        return openWriter(indexShardKey, indexShard);
    }

    private IndexShardWriter openWriter(final IndexShardKey indexShardKey, final IndexShard indexShard) {
        final long indexShardId = indexShard.getId();

        // Load the index.
        final Index index = indexShard.getIndex();

        // Get the index fields.
        final IndexConfig indexConfig = indexConfigCache.getOrCreate(DocRef.create(index));

        // Create the writer.
        final int ramBufferSizeMB = getRamBufferSize();

        // Mark the index shard as opening.
        LOGGER.debug(() -> "Opening " + indexShard);
        indexShardManager.setStatus(indexShardId, IndexShardStatus.OPENING);

        try {
            final IndexShardWriter indexShardWriter = new IndexShardWriterImpl(indexShardManager, indexConfig, indexShardKey, indexShard, ramBufferSizeMB);

            // We have opened the index so update the DB object.
            indexShardManager.setStatus(indexShardId, IndexShardStatus.OPEN);

            // Output some debug.
            LOGGER.debug(() -> "Opened " + indexShard + " in " + (System.currentTimeMillis() - indexShardWriter.getCreationTime()) + "ms");

            return indexShardWriter;

        } catch (final LockObtainFailedException t) {
            LOGGER.error(t::getMessage, t);

        } catch (final Throwable t) {
            // Something unexpected went wrong.
            LOGGER.error(() -> "Setting index shard status to corrupt because (" + t.toString() + ")", t);
            indexShardManager.setStatus(indexShardId, IndexShardStatus.CORRUPT);
        }

        return null;
    }

    private int getRamBufferSize() {
        int ramBufferSizeMB = 1024;
        if (stroomPropertyService != null) {
            try {
                final String property = stroomPropertyService.getProperty("stroom.index.ramBufferSizeMB");
                if (property != null) {
                    ramBufferSizeMB = Integer.parseInt(property);
                }
            } catch (final Exception ex) {
                LOGGER.error(() -> "connectWrapper() - Integer.parseInt stroom.index.ramBufferSizeMB", ex);
            }
        }
        return ramBufferSizeMB;
    }

    /**
     * This is called by the lifecycle service and will call flush on all open writers.
     */
    @Override
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Writer Flush", description = "Job to flush index shard data to disk")
    public void flushAll() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try {
            final Set<IndexShardWriter> openWriters = new HashSet<>(openWritersByShardKey.values());
            if (openWriters.size() > 0) {
                // Flush all writers.
                final CountDownLatch countDownLatch = new CountDownLatch(openWriters.size());
                openWriters.forEach(indexShardWriter -> flush(indexShardWriter, asyncRunner).thenAccept(isw -> countDownLatch.countDown()));
                countDownLatch.await();
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
        }

        LOGGER.debug(() -> "flushAll() - Completed in " + logExecutionTime);
    }

    /**
     * This method should ensure there is enough room in the map to add a new item by removing the LRU items until we have less items than the max capacity.
     */
    private void makeRoom() {
        removeElementsExceedingCore();
        removeElementsExceedingMax();
    }

    /**
     * This is called by the lifecycle service and remove writers that are past their TTL, TTI or LRU items that exceed the capacity.
     */
    @Override
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Writer Cache Sweep", description = "Job to remove old index shard writers from the cache")
    public void sweep() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        removeElementsExceedingTTLandTTI();
        removeElementsExceedingCore();

        LOGGER.debug(() -> "sweep() - Completed in " + logExecutionTime);
    }

    private void removeElementsExceedingTTLandTTI() {
        final Settings settings = getSettings();

        // Deal with TTL and TTI.
        long overflow = openWritersByShardKey.size() - settings.minItems;
        if (overflow > 0 && (settings.timeToLive > 0 || settings.timeToIdle > 0)) {
            final long now = System.currentTimeMillis();

            // Get a set of candidates for removal that are currently exceeding TTL or TTI.
            final Set<IndexShardWriter> candidates = Collections.newSetFromMap(new ConcurrentHashMap<>());

            // Add open writers that are currently exceeding TTL or TTI.
            openWritersByShardKey.values().parallelStream().forEach(indexShardWriter -> {
                if (settings.timeToLive > 0 && indexShardWriter.getCreationTime() < now - settings.timeToLive) {
                    candidates.add(indexShardWriter);
                } else if (settings.timeToIdle > 0 && indexShardWriter.getLastUsedTime() < now - settings.timeToIdle) {
                    candidates.add(indexShardWriter);
                }
            });

            // Close candidates in LRU order.
            final List<IndexShardWriter> lruList = getLeastRecentlyUsedList(candidates);
            while (overflow > 0 && lruList.size() > 0) {
                final IndexShardWriter indexShardWriter = lruList.remove(0);
                overflow--;
                close(indexShardWriter, asyncRunner);
            }
        }
    }

    private void removeElementsExceedingCore() {
        final Settings settings = getSettings();
        trim(settings.coreItems, asyncRunner);
    }

    private void removeElementsExceedingMax() {
        final Settings settings = getSettings();
        trim(settings.maxItems, syncRunner);
    }

    private void trim(final long trimSize, final Runner runner) {
        // Deal with exceeding trim size.
        long overflow = openWritersByShardKey.size() - trimSize;
        if (overflow > 0) {
            // Get LRU list.
            final List<IndexShardWriter> lruList = getLeastRecentlyUsedList(openWritersByShardKey.values());
            while (overflow > 0 && lruList.size() > 0) {
                final IndexShardWriter indexShardWriter = lruList.remove(0);
                overflow--;
                close(indexShardWriter, runner);
            }
        }
    }

    private List<IndexShardWriter> getLeastRecentlyUsedList(final Collection<IndexShardWriter> items) {
        return items.stream().sorted(Comparator.comparingLong(IndexShardWriter::getLastUsedTime)).collect(Collectors.toList());
    }

    @Override
    public void close(final IndexShardWriter indexShardWriter) {
        close(indexShardWriter, asyncRunner);
    }

    private CompletableFuture<IndexShardWriter> flush(final IndexShardWriter indexShardWriter, final Runner exec) {
        return exec.exec(() -> {
            try {
                taskContext.setName("Flushing writer");
                taskContext.info("Flushing writer for index shard " + indexShardWriter.getIndexShardId());

                // Flush the shard.
                indexShardWriter.flush();
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }

            return indexShardWriter;
        });
    }

    private CompletableFuture<IndexShardWriter> close(final IndexShardWriter indexShardWriter, final Runner exec) {
        final long indexShardId = indexShardWriter.getIndexShardId();

        // Set the status of the shard to closing so it won't be used again immediately if removed from the map.
        indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSING);
        // Remove the shard from the map.
        openWritersByShardKey.remove(indexShardWriter.getIndexShardKey());

        closing.incrementAndGet();
        return exec.exec(() -> {
            try {
                try {
                    taskContext.setName("Closing writer");
                    taskContext.info("Closing writer for index shard " + indexShardWriter.getIndexShardId());

                    // Close the shard.
                    indexShardWriter.close();
                } finally {
                    // Remove the writer from ones that cen be used by readers.
                    openWritersByShardId.remove(indexShardWriter.getIndexShardId());

                    // Update the shard status.
                    indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSED);
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            } finally {
                closing.decrementAndGet();
            }

            return indexShardWriter;
        });
    }

    @StroomStartup
    public synchronized void startup() {
        LOGGER.info(() -> "Index shard writer cache startup");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // Make sure all open shards are marked as closed.
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getIndexShardStatusSet().add(IndexShardStatus.OPEN);
        criteria.getIndexShardStatusSet().add(IndexShardStatus.OPENING);
        criteria.getIndexShardStatusSet().add(IndexShardStatus.CLOSING);
        final List<IndexShard> list = indexShardService.find(criteria);
        for (final IndexShard indexShard : list) {
            clean(indexShard);
        }

        LOGGER.info(() -> "Index shard writer cache startup completed in " + logExecutionTime);
    }

    private void clean(final IndexShard indexShard) {
        try {
            LOGGER.info(() -> "Changing shard status to closed (" + indexShard + ")");
            indexShard.setStatus(IndexShardStatus.CLOSED);
            indexShardService.save(indexShard);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }

        try {
            LOGGER.info(() -> "Clearing any lingering locks (" + indexShard + ")");
            final Path dir = IndexShardUtil.getIndexDir(indexShard).toPath();
            LockFactoryUtil.clean(dir);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @StroomShutdown
    public synchronized void shutdown() {
        LOGGER.info(() -> "Index shard writer cache shutdown");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        ScheduledExecutorService executor = null;

        try {
            // Close any remaining writers.
            openWritersByShardKey.values().forEach(indexShardWriter -> close(indexShardWriter, asyncRunner));

            // Report on closing progress.
            if (closing.get() > 0) {
                // Create a scheduled executor for us to continually log index shard writer action progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + closing.get() + " index shards to close"), 10, 10, TimeUnit.SECONDS);

                while (closing.get() > 0) {
                    ThreadUtil.sleep(500);
                }
            }

        } finally {
            if (executor != null) {
                // Shut down the progress logging executor.
                executor.shutdown();
            }
        }

        LOGGER.info(() -> "Index shard writer cache shutdown completed in " + logExecutionTime);
    }

    private Settings getSettings() {
        if (settings == null || settings.creationTime < (System.currentTimeMillis() - 60000)) {
            final long timeToLive = Math.max(0, getDuration("stroom.index.writer.cache.timeToLive", 0));
            final long timeToIdle = Math.max(0, getDuration("stroom.index.writer.cache.timeToIdle", 0));
            final long minItems = Math.max(0, stroomPropertyService.getLongProperty("stroom.index.writer.cache.minItems", 0));
            final long coreItems = Math.max(minItems, stroomPropertyService.getLongProperty("stroom.index.writer.cache.coreItems", 50));
            final long maxItems = Math.max(coreItems, stroomPropertyService.getLongProperty("stroom.index.writer.cache.maxItems", 100));

            settings = new Settings(System.currentTimeMillis(), timeToLive, timeToIdle, minItems, coreItems, maxItems);
        }

        return settings;
    }

    private long getDuration(final String propertyName, final long defaultValue) {
        final String propertyValue = stroomPropertyService.getProperty(propertyName);
        Long duration;
        try {
            duration = ModelStringUtil.parseDurationString(propertyValue);
            if (duration == null) {
                duration = defaultValue;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error(() -> "Unable to parse property '" + propertyName + "' value '" + propertyValue + "', using default of '" + defaultValue + "' instead", e);
            duration = defaultValue;
        }

        return duration;
    }

    private static class AsyncRunner implements Runner {
        private final Executor executor;

        AsyncRunner(final Executor executor) {
            this.executor = executor;
        }

        @Override
        public CompletableFuture<IndexShardWriter> exec(final Supplier<IndexShardWriter> supplier) {
            return CompletableFuture.supplyAsync(supplier, executor);
        }
    }

    private static class SyncRunner implements Runner {
        @Override
        public CompletableFuture<IndexShardWriter> exec(final Supplier<IndexShardWriter> supplier) {
            return CompletableFuture.completedFuture(supplier.get());
        }
    }

    private interface Runner {
        CompletableFuture<IndexShardWriter> exec(Supplier<IndexShardWriter> supplier);
    }

    private static class Settings {
        private long creationTime;
        private long timeToLive;
        private long timeToIdle;
        private long minItems;
        private long coreItems;
        private long maxItems;

        Settings(final long creationTime, final long timeToLive, final long timeToIdle, final long minItems, final long coreItems, final long maxItems) {
            this.creationTime = creationTime;
            this.timeToLive = timeToLive;
            this.timeToIdle = timeToIdle;
            this.minItems = minItems;
            this.coreItems = coreItems;
            this.maxItems = maxItems;
        }
    }
}
