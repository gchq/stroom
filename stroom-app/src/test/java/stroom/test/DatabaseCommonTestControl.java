/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.impl.CacheManagerService;
import stroom.entity.StroomEntityManager;
import stroom.index.service.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.Clearable;
import stroom.index.IndexShardManager;
import stroom.index.IndexShardWriterCache;
import stroom.node.impl.NodeCreator;
import stroom.streamtask.StreamTaskCreator;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
public class DatabaseCommonTestControl implements CommonTestControl {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCommonTestControl.class);

    private final StroomEntityManager entityManager;
    private final IndexVolumeService volumeService;
    private final ContentImportService contentImportService;
    private final IndexShardManager indexShardManager;
    private final IndexShardWriterCache indexShardWriterCache;
    private final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper;
    private final NodeCreator nodeConfig;
    private final StreamTaskCreator streamTaskCreator;
    private final CacheManagerService stroomCacheManager;
    private final Set<Clearable> clearables;

    @Inject
    DatabaseCommonTestControl(final StroomEntityManager entityManager,
                              final IndexVolumeService volumeService,
                              final ContentImportService contentImportService,
                              final IndexShardManager indexShardManager,
                              final IndexShardWriterCache indexShardWriterCache,
                              final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper,
                              final NodeCreator nodeConfig,
                              final StreamTaskCreator streamTaskCreator,
                              final CacheManagerService stroomCacheManager,
                              final Set<Clearable> clearables) {
        this.entityManager = entityManager;
        this.volumeService = volumeService;
        this.contentImportService = contentImportService;
        this.indexShardManager = indexShardManager;
        this.indexShardWriterCache = indexShardWriterCache;
        this.databaseCommonTestControlTransactionHelper = databaseCommonTestControlTransactionHelper;
        this.nodeConfig = nodeConfig;
        this.streamTaskCreator = streamTaskCreator;
        this.stroomCacheManager = stroomCacheManager;
        this.clearables = clearables;
    }

    @Override
    public void setup() {
        Instant startTime = Instant.now();
        nodeConfig.setup();

        // Ensure we can create tasks.
        streamTaskCreator.startup();
        LOGGER.info("test environment setup completed in {}", Duration.between(startTime, Instant.now()));
    }

    /**
     * Clear down the database.
     */
    @Override
    public void teardown() {
        Instant startTime = Instant.now();
        // Make sure we are no longer creating tasks.
        streamTaskCreator.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        indexShardManager.deleteFromDisk();

        // Delete the contents of all volumes.
        final List<IndexVolume> volumes = volumeService.getAll();
        for (final IndexVolume volume : volumes) {
            // The parent will also pick up the index shard (as well as the
            // store)
            FileUtil.deleteContents(Paths.get(volume.getPath()));
        }

        //ensure any hibernate entities are flushed down before we clear the tables
        entityManager.clearContext();

        // Clear all the tables using direct sql on a different connection
        // in theory truncating the tables should be quicker but it was taking 1.5s to truncate all the tables
        // so used delete with no constraint checks instead
        databaseCommonTestControlTransactionHelper.clearAllTables();

        // ensure all the caches are empty
        stroomCacheManager.clear();

        clearables.forEach(Clearable::clear);
        LOGGER.info("test environment teardown completed in {}", Duration.between(startTime, Instant.now()));
    }

    @Override
    public int countEntity(final String tableName) {
        return databaseCommonTestControlTransactionHelper.countEntity(tableName);
    }

    @Override
    public void createRequiredXMLSchemas() {
        contentImportService.importXmlSchemas();
    }
}
