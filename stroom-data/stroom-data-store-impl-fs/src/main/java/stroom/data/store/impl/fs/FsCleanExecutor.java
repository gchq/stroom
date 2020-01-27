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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.task.api.AsyncTaskHelper;
import stroom.task.api.TaskCallbackAdaptor;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.Task;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Task to clean the stream store.
 */
class FsCleanExecutor {
    private static final String DELETE_OUT = "delete.out";

    private static final Logger LOGGER = LoggerFactory.getLogger(FsCleanExecutor.class);
    private final FsVolumeService volumeService;
    private final TaskContext taskContext;
    private final TaskManager taskManager;
    private final int batchSize;
    private final Duration oldAge;
    private final boolean deleteOut;

    private AsyncTaskHelper<VoidResult> asyncTaskHelper;

    @Inject
    FsCleanExecutor(final FsVolumeService volumeService,
                    final TaskContext taskContext,
                    final TaskManager taskManager,
                    final DataStoreServiceConfig config) {
        this.volumeService = volumeService;
        this.taskContext = taskContext;
        this.taskManager = taskManager;
        this.batchSize = config.getDeleteBatchSize();

        Duration age;
        age = config.getFileSystemCleanOldAge().getDuration();
        if (age == null) {
            age = Duration.ofDays(1);
        }
        this.oldAge = age;
        this.deleteOut = config.isFileSystemCleanDeleteOut();
    }

    Duration getOldAge() {
        return oldAge;
    }

    public boolean isDelete() {
        return !deleteOut;
    }

    AsyncTaskHelper<VoidResult> getAsyncTaskHelper() {
        return asyncTaskHelper;
    }

    private void logInfo(final Supplier<String> messageSupplier) {
        taskContext.info(messageSupplier);
    }

    public void exec(final Task<?> task) {
        clean(task);
    }

    public void clean(final Task<?> task) {
        final Map<FsVolume, FsCleanProgress> taskProgressMap = new HashMap<>();
        final Map<FsVolume, PrintWriter> printWriterMap = new HashMap<>();

        // Load the node.
        asyncTaskHelper = new AsyncTaskHelper<>(null, taskContext, taskManager, batchSize);

        logInfo(() -> "Starting file system clean task. oldAge = " + oldAge);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final List<FsVolume> volumeList = volumeService.find(new FindFsVolumeCriteria());

        try {
            if (volumeList != null && volumeList.size() > 0) {
                // Add to the task steps remaining.

                for (final FsVolume volume : volumeList) {
                    final FsCleanProgress taskProgress = new FsCleanProgress();
                    if (deleteOut) {
                        final Path dir = Paths.get(volume.getPath());
                        if (Files.isDirectory(dir)) {
                            try {
                                printWriterMap
                                        .put(volume,
                                                new PrintWriter(Files.newBufferedWriter(dir.resolve(DELETE_OUT), StreamUtil.DEFAULT_CHARSET)));
                            } catch (final IOException e) {
                                LOGGER.error("exec() - Error opening file", e);
                            }
                        }
                    }
                    taskProgressMap.put(volume, taskProgress);
                    final FsCleanSubTask subTask = new FsCleanSubTask(this, task, taskProgress, volume,
                            "", "");

                    asyncTaskHelper.fork(subTask, new TaskCallbackAdaptor<>() {
                        @Override
                        public void onSuccess(final VoidResult result) {
                            taskProgress.addScanComplete();
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            taskProgress.addScanComplete();
                        }
                    });
                }

                while (asyncTaskHelper.busy()) {
                    try {
                        // Wait for all task steps to complete.
                        Thread.sleep(500);
                    } catch (final InterruptedException e) {
                        LOGGER.error(e.getMessage(), e);

                        // Continue to interrupt this thread.
                        Thread.currentThread().interrupt();
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        logInfo(() -> "Stopping file system clean task.");
                        asyncTaskHelper.clear();
                    }


                    for (final FsVolume volume : volumeList) {
                        final FsCleanProgress taskProgress = taskProgressMap.get(volume);

                        logInfo(() -> volume.getPath() +
                                " (Scan Dir/File " +
                                taskProgress.getScanDirCount() +
                                "/" +
                                taskProgress.getScanFileCount() +
                                ", Del " +
                                taskProgress.getScanDeleteCount() +
                                ") ");

                        String line;
                        try {
                            final PrintWriter deletePrintWriter = printWriterMap.get(volume);
                            while ((line = taskProgress.getLineQueue().poll()) != null) {
                                if (deletePrintWriter != null) {
                                    deletePrintWriter.println(line);
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error("exec() - Error writing " + DELETE_OUT, e);
                            taskContext.terminate();
                        }
                    }
                }
            }
        } finally {
            printWriterMap.values().forEach(CloseableUtil::closeLogAndIgnoreException);
        }

        logInfo(() -> "start() - Completed file system clean task in " + logExecutionTime);
    }
}
