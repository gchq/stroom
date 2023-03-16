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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Task to clean the stream store.
 */
class FsOrphanFileFinderExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanFileFinderExecutor.class);
    private static final Logger ORPHAN_FILE_LOGGER = LoggerFactory.getLogger("orphan_file");
    public static final String TASK_NAME = "Orphan File Finder";

    private final FsVolumeService volumeService;
    private final Duration oldAge;
    private final Provider<FsOrphanFileFinder> orphanFileFinderProvider;
    //    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final PathCreator pathCreator;
//    private final DataStoreServiceConfig config;


    @Inject
    FsOrphanFileFinderExecutor(final FsVolumeService volumeService,
                               final Provider<FsOrphanFileFinder> orphanFileFinderProvider,
//                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final Provider<DataStoreServiceConfig> config,
                               final PathCreator pathCreator) {
        this.volumeService = volumeService;
        this.orphanFileFinderProvider = orphanFileFinderProvider;
//        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.pathCreator = pathCreator;
//        this.config = config;

        Duration age;
        age = config.get().getFileSystemCleanOldAge().getDuration();
        if (age == null) {
            age = Duration.ofDays(1);
        }
        this.oldAge = age;
    }

    public void scan() {
        final TaskContext taskContext = taskContextFactory.current();
        taskContext.info(() -> "Starting orphan file finder");
        LOGGER.info("{} - Starting, using logger name: '{}'", TASK_NAME, ORPHAN_FILE_LOGGER.getName());
        ORPHAN_FILE_LOGGER.info("Starting {} - {}", TASK_NAME, DateUtil.createNormalDateTimeString());
        ORPHAN_FILE_LOGGER.info("Orphaned files/directories:");
        final DurationTimer durationTimer = DurationTimer.start();

        final AtomicLong counter = new AtomicLong();
        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        final Consumer<Path> orphanConsumer = path -> {
            LOGGER.debug(() -> "Unexpected file in store: " +
                    FileUtil.getCanonicalPath(path));

            ORPHAN_FILE_LOGGER.info(FileUtil.getCanonicalPath(path));

            summary.addPath(path);
            counter.incrementAndGet();
        };

        try {
            scan(orphanConsumer, taskContext);

            if (Thread.currentThread().isInterrupted() || taskContext.isTerminated()) {
                ORPHAN_FILE_LOGGER.info("--- {} task {} ---",
                        TASK_NAME, Thread.currentThread().isInterrupted()
                                ? "interrupted"
                                : "terminated");
            } else {
                ORPHAN_FILE_LOGGER.info(summary.toString());
                ORPHAN_FILE_LOGGER.info("Completed {} in {}, found {} orphaned files/directories",
                        TASK_NAME, durationTimer, counter.get());
            }
        } catch (Exception e) {
            ORPHAN_FILE_LOGGER.info("--- {} task failed due to: {} ---",
                    TASK_NAME, e.getMessage());
            throw e;
        }
        LOGGER.info("{} - Finished, in {}", TASK_NAME, durationTimer);
    }

    public void scan(final Consumer<Path> orphanConsumer, final TaskContext parentContext) {
        parentContext.info(() -> "Starting orphan file finder task. oldAge = " + oldAge);
        final long oldestDirTime = System.currentTimeMillis() - oldAge.toMillis();

        final LogExecutionTime logExecutionTime = LogExecutionTime.start();

        final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        if (volumeList != null && volumeList.size() > 0) {
            for (final FsVolume volume : volumeList) {
                if (Thread.currentThread().isInterrupted() || parentContext.isTerminated()) {
                    LOGGER.info("{} - Task terminated", TASK_NAME);
                    break;
                }
                if (VolumeUseStatus.ACTIVE.equals(volume.getStatus())) {
                    scanVolume(volume, orphanConsumer, oldestDirTime, parentContext);
                }
            }
        }
        parentContext.info(() -> "start() - Completed orphan file finder in " + logExecutionTime);
    }

    private void scanVolume(final FsVolume volume,
                            final Consumer<Path> orphanConsumer,
                            final long oldestDirTime,
                            final TaskContext taskContext) {
        final Path absDir = Paths.get(pathCreator.makeAbsolute(
                pathCreator.replaceSystemProperties(volume.getPath())));
        if (!Files.isDirectory(absDir)) {
            LOGGER.error(() -> "Directory for file delete list does not exist '" +
                    FileUtil.getCanonicalPath(absDir) +
                    "'");
        } else {
            orphanFileFinderProvider.get().scanVolumePath(volume, orphanConsumer, oldestDirTime, taskContext);
        }
    }
}
