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

import stroom.data.store.impl.fs.FsVolumeService;
import stroom.index.VolumeCreator;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexVolumeService;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
public class DatabaseCommonTestControl implements CommonTestControl {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseCommonTestControl.class);

    private final IndexVolumeService indexVolumeService;
    private final ContentImportService contentImportService;
    private final IndexShardManager indexShardManager;
    private final IndexShardWriterCache indexShardWriterCache;
    private final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper;
    private final VolumeCreator nodeConfig;
    private final ProcessorTaskManager processorTaskManager;
    private final Set<Clearable> clearables;
    private final FsVolumeService fsVolumeService;

    @Inject
    DatabaseCommonTestControl(final IndexVolumeService indexVolumeService,
                              final ContentImportService contentImportService,
                              final IndexShardManager indexShardManager,
                              final IndexShardWriterCache indexShardWriterCache,
                              final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper,
                              final VolumeCreator nodeConfig,
                              final ProcessorTaskManager processorTaskManager,
                              final Set<Clearable> clearables,
                              final FsVolumeService fsVolumeService) {
        this.indexVolumeService = indexVolumeService;
        this.contentImportService = contentImportService;
        this.indexShardManager = indexShardManager;
        this.indexShardWriterCache = indexShardWriterCache;
        this.databaseCommonTestControlTransactionHelper = databaseCommonTestControlTransactionHelper;
        this.nodeConfig = nodeConfig;
        this.processorTaskManager = processorTaskManager;
        this.clearables = clearables;
        this.fsVolumeService = fsVolumeService;
    }

    @Override
    public void setup() {
        Instant startTime = Instant.now();
        nodeConfig.setup();

        // Ensure we can create tasks.
        processorTaskManager.startup();
        // Only allow tasks to be created synchronously for the purposes of testing.
        processorTaskManager.setAllowAsyncTaskCreation(false);
        LOGGER.info("test environment setup completed in {}", Duration.between(startTime, Instant.now()));
    }

    /**
     * Clear down the database.
     */
    @Override
    public void teardown() {
        Instant startTime = Instant.now();
        // Make sure we are no longer creating tasks.
        processorTaskManager.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        indexShardManager.deleteFromDisk();

        // Delete the contents of all index volumes.
        indexVolumeService.clear();

        // Delete the contents of all stream store volumes.
        fsVolumeService.clear();

        // Clear all the tables using direct sql on a different connection
        // in theory truncating the tables should be quicker but it was taking 1.5s to truncate all the tables
        // so used delete with no constraint checks instead
        databaseCommonTestControlTransactionHelper.clearAllTables();

        // Clear all caches or files that might have been created by previous tests.
        clearables.forEach(Clearable::clear);

        LOGGER.info("test environment teardown completed in {}", Duration.between(startTime, Instant.now()));
    }

    @Override
    public void createRequiredXMLSchemas() {
        contentImportService.importStandardPacks();
    }
}
