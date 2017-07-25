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

import net.sf.ehcache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.shared.DocRefUtil;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Profile(StroomSpringProfiles.PROD)
public class IndexShardWriterCacheImpl extends AbstractCacheBean<Long, IndexShardWriter> implements IndexShardWriterCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterCacheImpl.class);
    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final IndexConfigCache indexConfigCache;
    private final IndexShardManager indexShardManager;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    IndexShardWriterCacheImpl(final CacheManager cacheManager,
                              final StroomPropertyService stroomPropertyService,
                              final IndexConfigCache indexConfigCache,
                              final IndexShardManager indexShardManager) {
        super(cacheManager, "Index Shard Writer Cache", MAX_CACHE_ENTRIES);
        this.stroomPropertyService = stroomPropertyService;
        this.indexConfigCache = indexConfigCache;
        this.indexShardManager = indexShardManager;

        setMaxIdleTime(10, TimeUnit.SECONDS);
    }

    @Override
    public IndexShardWriter getOrCreate(final Long indexShardId) {
        return computeIfAbsent(indexShardId, this::create);
    }

    @Override
    public IndexShardWriter getQuiet(final Long indexShardId) {
        return super.getQuiet(indexShardId);
    }

    private IndexShardWriter create(final Long indexShardId) {
        // Load the current index shard.
        final IndexShard loaded = indexShardManager.load(indexShardId);

        if (loaded == null) {
            throw new IndexException("Index shard is no longer in the database");
        }

        // Load the index.
        final Index index = loaded.getIndex();

        // Get the index fields.
        final IndexConfig indexConfig = indexConfigCache.getOrCreate(DocRefUtil.create(index));

        // Create the writer.
        final int ramBufferSizeMB = getRamBufferSize();
        return new IndexShardWriterImpl(indexShardManager, indexConfig, loaded, ramBufferSizeMB);
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

    @Override
    public void clear() {
        LOGGER.info(() -> "Clearing index shard writer cache");
        ScheduledExecutorService executor = null;

        try {
            if (size() > 0) {
                // Create a scheduled executor for us to continually log index shard writer action progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + size() + " index shards to close"), 10, 10, TimeUnit.SECONDS);
            }

            super.clear();

        } finally {
            if (executor != null) {
                // Shut down the progress logging executor.
                executor.shutdown();
            }
        }

        LOGGER.info(() -> "Finished clearing index shard writer cache");
    }

    /**
     * This is called by the lifecycle service and will call flush on all open writers.
     */
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Writer Flush", description = "Job to flush index shard data to disk")
    @Override
    public void flushAll() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final List<Long> keys = getKeys();
        keys.forEach(key -> {
            final IndexShardWriter indexShardWriter = getQuiet(key);
            if (indexShardWriter != null) {
                indexShardWriter.flush();
            }
        });

        LOGGER.debug(() -> "flushAll() - Completed in " + logExecutionTime);
    }
}
