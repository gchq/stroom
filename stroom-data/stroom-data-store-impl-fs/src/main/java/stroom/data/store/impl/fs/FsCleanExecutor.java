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
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Task to clean the stream store.
 */
class FsCleanExecutor {
    private static final String DELETE_OUT = "delete.out";

    private static final Logger LOGGER = LoggerFactory.getLogger(FsCleanExecutor.class);
    private final FsVolumeService volumeService;
    private final Duration oldAge;
    private final boolean deleteOut;

    private final Provider<FsCleanSubTaskHandler> fsCleanSubTaskHandlerProvider;
    private final ExecutorProvider executorProvider;
    private final Provider<TaskContext> taskContextProvider;
    private final DataStoreServiceConfig config;

    @Inject
    FsCleanExecutor(final FsVolumeService volumeService,
                    final Provider<FsCleanSubTaskHandler> fsCleanSubTaskHandlerProvider,
                    final ExecutorProvider executorProvider,
                    final Provider<TaskContext> taskContextProvider,
                    final DataStoreServiceConfig config) {
        this.volumeService = volumeService;
        this.fsCleanSubTaskHandlerProvider = fsCleanSubTaskHandlerProvider;
        this.executorProvider = executorProvider;
        this.taskContextProvider = taskContextProvider;
        this.config = config;

        Duration age;
        age = config.getFileSystemCleanOldAge().getDuration();
        if (age == null) {
            age = Duration.ofDays(1);
        }
        this.oldAge = age;
        this.deleteOut = config.isFileSystemCleanDeleteOut();
    }

    public void clean() {
        final TaskContext taskContext = taskContextProvider.get();
        taskContext.info(() -> "Starting file system clean task. oldAge = " + oldAge);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final List<FsVolume> volumeList = volumeService.find(new FindFsVolumeCriteria()).getValues();
        if (volumeList != null && volumeList.size() > 0) {
            // Add to the task steps remaining.
            final ThreadPool threadPool = new ThreadPoolImpl("File System Clean#", 1, 1, config.getFileSystemCleanBatchSize(), Integer.MAX_VALUE);
            final Executor executor = executorProvider.get(threadPool);

            final CompletableFuture<?>[] completableFutures = new CompletableFuture<?>[volumeList.size()];
            int i = 0;
            for (final FsVolume volume : volumeList) {
                Runnable runnable = () -> cleanVolume(volume);
                runnable = taskContext.sub(runnable);
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                completableFutures[i++] = completableFuture;
            }
            CompletableFuture.allOf(completableFutures).join();
        }

        taskContext.info(() -> "start() - Completed file system clean task in " + logExecutionTime);
    }

    private void cleanVolume(final FsVolume volume) {
        final FsCleanProgress taskProgress = new FsCleanProgress();
        if (deleteOut) {
            final Path dir = Paths.get(volume.getPath());
            if (!Files.isDirectory(dir)) {
                LOGGER.error("Directory for file delete list does not exist '" + FileUtil.getCanonicalPath(dir) + "'");
            } else {
                final Path deleteListFile = dir.resolve(DELETE_OUT);
                try {
                    try (final PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(deleteListFile, StreamUtil.DEFAULT_CHARSET))) {
                        final Consumer<List<String>> deleteListConsumer = list -> {
                            synchronized (printWriter) {
                                list.forEach(printWriter::println);
                            }
                        };

                        final FsCleanSubTask subTask = new FsCleanSubTask(taskProgress, volume, "", "", oldAge, false);
                        fsCleanSubTaskHandlerProvider.get().exec(subTask, deleteListConsumer);
                    }
                } catch (final IOException e) {
                    LOGGER.error("exec() - Error writing " + DELETE_OUT, e);
                }
            }
        } else {
            final FsCleanSubTask subTask = new FsCleanSubTask(taskProgress, volume, "", "", oldAge, true);
            fsCleanSubTaskHandlerProvider.get().exec(subTask, null);
        }
    }
}
