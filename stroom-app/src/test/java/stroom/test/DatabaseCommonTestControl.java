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

import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.index.VolumeCreator;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import org.assertj.core.api.Assertions;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * <p>
 * Class to help with testing.
 * </p>
 */
public class DatabaseCommonTestControl implements CommonTestControl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DatabaseCommonTestControl.class);

    private final ContentImportService contentImportService;
    private final IndexShardManager indexShardManager;
    private final IndexShardWriterCache indexShardWriterCache;
    private final VolumeCreator volumeCreator;
    private final ProcessorTaskManager processorTaskManager;
    private final Set<Clearable> clearables;
    private final FsVolumeConfig fsVolumeConfig;
    private final VolumeConfig volumeConfig;
    private final FsVolumeService fsVolumeService;
    private final PathCreator pathCreator;
    private final IndexVolumeService indexVolumeService;

    //    private static boolean needsCleanup;
    // Thread local for parallel test running
    private static final ThreadLocal<Boolean> NEEDS_CLEAN_UP_THREAD_LOCAL = ThreadLocal.withInitial(() -> false);

    @Inject
    DatabaseCommonTestControl(final ContentImportService contentImportService,
                              final IndexShardManager indexShardManager,
                              final IndexShardWriterCache indexShardWriterCache,
                              final VolumeCreator volumeCreator,
                              final ProcessorTaskManager processorTaskManager,
                              final Set<Clearable> clearables,
                              final VolumeConfig volumeConfig,
                              final FsVolumeConfig fsVolumeConfig,
                              final FsVolumeService fsVolumeService,
                              final PathCreator pathCreator,
                              final IndexVolumeService indexVolumeService) {
        this.contentImportService = contentImportService;
        this.indexShardManager = indexShardManager;
        this.indexShardWriterCache = indexShardWriterCache;
        this.volumeCreator = volumeCreator;
        this.processorTaskManager = processorTaskManager;
        this.clearables = clearables;
        this.volumeConfig = volumeConfig;
        this.fsVolumeConfig = fsVolumeConfig;
        this.fsVolumeService = fsVolumeService;
        this.pathCreator = pathCreator;
        this.indexVolumeService = indexVolumeService;
    }

    @Override
    public void setup(final Path tempDir) {
        LOGGER.debug("temp dir: {}", tempDir);
        final Instant startTime = Instant.now();
        LOGGER.info(() -> LogUtil.inSeparatorLine("Starting setup of thread '{}' ({})",
                Thread.currentThread().getName(),
                Thread.currentThread().getId()));

        Path fsVolDir;
        Path indexVolDir;
        if (tempDir == null) {
            final List<String> fsVolPathStr = fsVolumeConfig.getDefaultStreamVolumePaths();
            fsVolDir = pathCreator.toAppPath(fsVolPathStr.get(0));
            final List<String> volGroupPathStr = volumeConfig.getDefaultIndexVolumeGroupPaths();
            indexVolDir = pathCreator.toAppPath(volGroupPathStr.get(0));
        } else {
            fsVolDir = tempDir.resolve("volumes/defaultStreamVolume").toAbsolutePath();
            indexVolDir = tempDir;
        }

        LOGGER.info("Creating default stream volumes in {}", fsVolDir.toAbsolutePath().normalize());
        fsVolumeConfig.setDefaultStreamVolumePaths(List.of(fsVolDir.toString()));
        fsVolumeService.ensureDefaultVolumes();
        fsVolumeService.flush();

        final FsVolume fsVolume = fsVolumeService.getVolume();
        if (fsVolume == null) {
            Assertions.fail("No active and non-full volumes found. " +
                    "Likely a problem with default volume creation in setup, or a too full disk.");
        }

        LOGGER.info("Creating index volume groups in {}", indexVolDir.toAbsolutePath().normalize());
        volumeCreator.setup(indexVolDir);

        // Ensure we can create tasks.
        processorTaskManager.startup();
        // Only allow tasks to be created synchronously for the purposes of testing.
        processorTaskManager.setAllowAsyncTaskCreation(false);

        LOGGER.info("Setting NEEDS_CLEAN_UP_THREAD_LOCAL to true");
        NEEDS_CLEAN_UP_THREAD_LOCAL.set(true);

        LOGGER.info(() -> LogUtil.inSeparatorLine(
                "Test environment setup completed in {}", Duration.between(startTime, Instant.now())));
    }

    @Override
    public void cleanup() {
        if (NEEDS_CLEAN_UP_THREAD_LOCAL.get()) {
            clear();
            LOGGER.info("Setting NEEDS_CLEAN_UP_THREAD_LOCAL to false");
            NEEDS_CLEAN_UP_THREAD_LOCAL.set(false);
        } else {
            LOGGER.info("Teardown not required");
        }
    }

    /**
     * Clear down the database.
     */
    @Override
    public void clear() {
        final Instant startTime = Instant.now();
        LOGGER.info(() -> LogUtil.inSeparatorLine("Starting teardown of thread '{}' ({})",
                Thread.currentThread().getName(),
                Thread.currentThread().getId()));
        // Make sure we are no longer creating tasks.
        processorTaskManager.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        LOGGER.info("Deleting shards from disk");
        indexShardManager.deleteFromDisk();

        // Clear all caches, files, volumes that might have been created by previous tests.

        final String clearedList = clearables.stream()
                .peek(Clearable::clear)
                .map(clearable -> clearable.getClass().getSimpleName())
                .sorted()
                .collect(Collectors.joining(", "));
        LOGGER.info("Cleared the following clearables [{}]", clearedList);

        LOGGER.info(() -> LogUtil.inSeparatorLine(
                "Test environment teardown completed in {}", Duration.between(startTime, Instant.now())));
    }

    @Override
    public void createRequiredXMLSchemas() {
        contentImportService.importStandardPacks();
    }

    /**
     * Useful if you want to manually delete all the test databases that may have been
     * left by test runs. Tests should clean up after themselves, but exceptions may stop
     * this happening.
     */
    public static void main(String[] args) {
        DbTestUtil.dropAllTestDatabases();
    }
}
