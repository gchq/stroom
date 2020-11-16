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
import stroom.index.VolumeCreator;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.selection.VolumeConfig;
import stroom.processor.impl.ProcessorTaskManager;
import stroom.util.io.PathCreator;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
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

    private static boolean needsCleanup;

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
                              final PathCreator pathCreator) {
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
    }

    @Override
    public void setup(final Path tempDir) {
        LOGGER.debug("temp dir: {}", tempDir);
        Instant startTime = Instant.now();
        Path fsVolDir;
        Path indexVolDir;
        if (tempDir == null) {
            final List<String> fsVolPathStr = fsVolumeConfig.getDefaultStreamVolumePaths();
            fsVolDir = Paths.get(pathCreator.replaceSystemProperties(fsVolPathStr.get(0)));
            final List<String> volGroupPathStr = volumeConfig.getDefaultIndexVolumeGroupPaths();
            indexVolDir = Paths.get(pathCreator.replaceSystemProperties(volGroupPathStr.get(0)));
        } else {
            fsVolDir = tempDir.resolve("volumes/defaultStreamVolume").toAbsolutePath();
            indexVolDir = tempDir;
        }

        LOGGER.debug("Creating stream volumes in {}", fsVolDir.toAbsolutePath().normalize().toString());
        fsVolumeConfig.setDefaultStreamVolumePaths(List.of(fsVolDir.toString()));

        LOGGER.debug("Creating index volume groups in {}", indexVolDir.toAbsolutePath().normalize().toString());
        volumeCreator.setup(indexVolDir);

        // Ensure we can create tasks.
        processorTaskManager.startup();
        // Only allow tasks to be created synchronously for the purposes of testing.
        processorTaskManager.setAllowAsyncTaskCreation(false);
        LOGGER.info("test environment setup completed in {}", Duration.between(startTime, Instant.now()));

        needsCleanup = true;
    }

    @Override
    public void cleanup() {
        if (needsCleanup) {
            clear();
            needsCleanup = false;
        }
    }

    /**
     * Clear down the database.
     */
    @Override
    public void clear() {
        Instant startTime = Instant.now();
        // Make sure we are no longer creating tasks.
        processorTaskManager.shutdown();

        // Make sure we don't delete database entries without clearing the pool.
        indexShardWriterCache.shutdown();
        indexShardManager.deleteFromDisk();

        // Delete the contents of all stream store volumes.
        fsVolumeService.clear();

        // Clear all caches or files that might have been created by previous tests.
        clearables.forEach(Clearable::clear);

        LOGGER.info("test environment teardown completed in {}", Duration.between(startTime, Instant.now()));
    }

    @Override
    public void createRequiredXMLSchemas() {
        contentImportService.importStandardPacks();
    }
}
