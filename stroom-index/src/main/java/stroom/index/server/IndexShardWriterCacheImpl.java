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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong currentlyClosing = new AtomicLong();
    private volatile ExecutorService closeExecutorService;
    private volatile Settings settings;

    @Inject
    public IndexShardWriterCacheImpl(final NodeCache nodeCache,
                                     final IndexShardService indexShardService,
                                     final StroomPropertyService stroomPropertyService,
                                     final IndexConfigCache indexConfigCache,
                                     final IndexShardManager indexShardManager) {
        this.nodeCache = nodeCache;
        this.indexShardService = indexShardService;
        this.stroomPropertyService = stroomPropertyService;
        this.indexConfigCache = indexConfigCache;
        this.indexShardManager = indexShardManager;
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
            LOGGER.warn(t::getMessage);
            // Something went wrong.
            indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSED);
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
            // Something went wrong.
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

        openWritersByShardKey.values().parallelStream().forEach(IndexShardWriter::flush);

        LOGGER.debug(() -> "flushAll() - Completed in " + logExecutionTime);
    }

    /**
     * This method should ensure there is enough room in the map to add a new item by removing the LRU items until we have less items than the max capacity.
     */
    private void makeRoom() {
        final Settings settings = getSettings();

        // Deal with exceeding max items.
        while (openWritersByShardKey.size() >= settings.maxItems) {
            // get the least recently used item.
            final Optional<IndexShardWriter> leastRecentlyUsed = openWritersByShardKey.values().parallelStream().min(Comparator.comparingLong(IndexShardWriter::getLastUsedTime));
            leastRecentlyUsed.ifPresent(this::closeSync);
        }
    }

    /**
     * This is called by the lifecycle service and remove writers that are past their TTL, TTI or LRU items that exceed the capacity.
     */
    @Override
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Writer Cache Sweep", description = "Job to remove old index shard writers from the cache")
    public void sweep() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final Settings settings = getSettings();
        final long now = System.currentTimeMillis();
        final AtomicLong maxRemovals = new AtomicLong(Math.max(0, openWritersByShardKey.size() - settings.minItems - currentlyClosing.get()));

        if (maxRemovals.get() > 0) {
            // Deal with TTL and TTI.
            if (settings.timeToLive > 0 || settings.timeToIdle > 0) {
                openWritersByShardKey.values().parallelStream().forEach(indexShardWriter -> {
                    if (openWritersByShardKey.size() > settings.minItems) {
                        if (settings.timeToLive > 0 && indexShardWriter.getCreationTime() < now - settings.timeToLive) {
                            tryCloseAsync(indexShardWriter, maxRemovals);
                        } else if (settings.timeToIdle > 0 && indexShardWriter.getLastUsedTime() < now - settings.timeToIdle) {
                            tryCloseAsync(indexShardWriter, maxRemovals);
                        }
                    }
                });
            }

            // Deal with exceeding core items.
            while (maxRemovals.get() > 0 && openWritersByShardKey.size() > settings.coreItems) {
                // get the least recently used item.
                final Optional<IndexShardWriter> leastRecentlyUsed = openWritersByShardKey.values().parallelStream().min(Comparator.comparingLong(IndexShardWriter::getLastUsedTime));
                leastRecentlyUsed.ifPresent(indexShardWriter -> tryCloseAsync(indexShardWriter, maxRemovals));
            }
        }

        LOGGER.debug(() -> "sweep() - Completed in " + logExecutionTime);
    }

    private void tryCloseAsync(final IndexShardWriter indexShardWriter, final AtomicLong maxRemovals) {
        if (maxRemovals.decrementAndGet() >= 0) {
            closeAsync(indexShardWriter);
        }
    }

    @Override
    public void close(final IndexShardWriter indexShardWriter) {
        closeAsync(indexShardWriter);
    }

    private void closeSync(final IndexShardWriter indexShardWriter) {
        final long indexShardId = indexShardWriter.getIndexShardId();

        // Set the status of the shard to closing so it won't be used again immediately if removed from the map.
        indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSING);
        // Remove the shard from the map.
        openWritersByShardKey.remove(indexShardWriter.getIndexShardKey());

        try {
            // Close the shard.
            indexShardWriter.close();
        } finally {
            openWritersByShardId.remove(indexShardWriter.getIndexShardId());

            // Update the shard status.
            indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSED);
        }
    }

    private CompletableFuture<IndexShardWriter> closeAsync(final IndexShardWriter indexShardWriter) {
        final long indexShardId = indexShardWriter.getIndexShardId();

        // Set the status of the shard to closing so it won't be used again immediately if removed from the map.
        indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSING);
        // Remove the shard from the map.
        openWritersByShardKey.remove(indexShardWriter.getIndexShardKey());

        currentlyClosing.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                try {
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
                currentlyClosing.decrementAndGet();
            }

            return indexShardWriter;
        }, getCloseExecutorService());
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
            LOGGER.info(() -> "Changing shard status to closed (" + indexShard + ")");
            indexShard.setStatus(IndexShardStatus.CLOSED);
            indexShardService.save(indexShard);
        }

        LOGGER.info(() -> "Index shard writer cache startup completed in " + logExecutionTime);
    }

    @StroomShutdown
    public synchronized void shutdown() {
        LOGGER.info(() -> "Index shard writer cache shutdown");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        ScheduledExecutorService executor = null;

        try {
            if (openWritersByShardId.size() > 0) {
                // Create a scheduled executor for us to continually log index shard writer action progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + openWritersByShardId.size() + " index shards to close"), 10, 10, TimeUnit.SECONDS);
                // Close all writers.
                openWritersByShardId.values().forEach(this::closeAsync);
            }

        } finally {
            try {
                destroyCloseExecutorService();
            } finally {
                if (executor != null) {
                    // Shut down the progress logging executor.
                    executor.shutdown();
                }
            }
        }

        LOGGER.info(() -> "Index shard writer cache shutdown completed in " + logExecutionTime);
    }

//    @Override
//    public void clear() {
//        // Close all writers synchronously.
//        openWritersByShardId.values().parallelStream().forEach(this::closeSync);
//    }

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

    private ExecutorService getCloseExecutorService() {
        if (closeExecutorService == null) {
            synchronized (this) {
                if (closeExecutorService == null) {
                    closeExecutorService = Executors.newCachedThreadPool();
                }
            }
        }
        return closeExecutorService;
    }

    private void destroyCloseExecutorService() {
        final ExecutorService closeExecutorService = this.closeExecutorService;
        this.closeExecutorService = null;

        if (closeExecutorService != null) {
            try {
                closeExecutorService.shutdown();
                closeExecutorService.awaitTermination(60, TimeUnit.MINUTES);
            } catch (final InterruptedException e) {
                LOGGER.error(e::getMessage);
            }
        }
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
