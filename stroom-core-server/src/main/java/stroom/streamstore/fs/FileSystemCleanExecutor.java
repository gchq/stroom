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

package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Volume;
import stroom.properties.StroomPropertyService;
import stroom.task.AsyncTaskHelper;
import stroom.task.TaskCallbackAdaptor;
import stroom.task.TaskContext;
import stroom.task.TaskManager;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task to clean the stream store.
 */
public class FileSystemCleanExecutor {
    private static final String DELETE_OUT = "delete.out";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemCleanExecutor.class);
    private final VolumeService volumeService;
    private final TaskContext taskContext;
    private final TaskManager taskManager;
    private final NodeCache nodeCache;
    private final int batchSize;
    private final long oldAge;
    private final boolean deleteOut;

    private AsyncTaskHelper<VoidResult> asyncTaskHelper;

    @Inject
    FileSystemCleanExecutor(final VolumeService volumeService,
                            final TaskContext taskContext,
                            final TaskManager taskManager,
                            final NodeCache nodeCache,
                            final StroomPropertyService propertyService) {
        this.volumeService = volumeService;
        this.taskContext = taskContext;
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
        this.batchSize = propertyService.getIntProperty("stroom.fileSystemCleanBatchSize", 20);

        Long age;
        try {
            age = ModelStringUtil.parseDurationString(propertyService.getProperty("stroom.fileSystemCleanOldAge"));
            if (age == null) {
                age = ModelStringUtil.parseDurationString("1d");
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.fileSystemCleanOldAge' value '" + propertyService.getProperty("stroom.fileSystemCleanOldAge")
                    + "', using default of '1d' instead", e);
            age = ModelStringUtil.parseDurationString("1d");
        }
        this.oldAge = age;

        this.deleteOut = propertyService.getBooleanProperty("stroom.fileSystemCleanDeleteOut", false);
    }

    public Long getOldAge() {
        return oldAge;
    }

    public boolean isDelete() {
        return !deleteOut;
    }

    public AsyncTaskHelper<VoidResult> getAsyncTaskHelper() {
        return asyncTaskHelper;
    }

    private void logInfo(final Object... args) {
        Arrays.asList(args).forEach(arg -> LOGGER.info(arg.toString()));
        taskContext.info(args);
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "File System Clean", advanced = false, description = "Job to process a volume deleting files that are no longer indexed (maybe the retention period has past or they have been deleted)")
    public void exec(final Task<?> task) {
        final long nodeId = nodeCache.getDefaultNode().getId();
        clean(task, nodeId);
    }

    public void clean(final Task<?> task, final long nodeId) {
        final Map<Volume, FileSystemCleanProgress> taskProgressMap = new HashMap<>();
        final Map<Volume, PrintWriter> printWriterMap = new HashMap<>();

        // Load the node.
        asyncTaskHelper = new AsyncTaskHelper<>(null, taskContext, taskManager, batchSize);

        logInfo("Starting file system clean task. oldAge = {}", ModelStringUtil.formatDurationString(oldAge));

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final FindVolumeCriteria criteria = new FindVolumeCriteria();
        criteria.getNodeIdSet().add(nodeId);
        final List<Volume> volumeList = volumeService.find(criteria);

        try {
            if (volumeList != null && volumeList.size() > 0) {
                // Add to the task steps remaining.

                for (final Volume volume : volumeList) {
                    final FileSystemCleanProgress taskProgress = new FileSystemCleanProgress();
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
                    final FileSystemCleanSubTask subTask = new FileSystemCleanSubTask(this, task, taskProgress, volume,
                            "", "");

                    asyncTaskHelper.fork(subTask, new TaskCallbackAdaptor<VoidResult>() {
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
                        logInfo("Stopping file system clean task.");
                        asyncTaskHelper.clear();
                    }

                    final StringBuilder trace = new StringBuilder();

                    for (final Volume volume : volumeList) {
                        final FileSystemCleanProgress taskProgress = taskProgressMap.get(volume);

                        trace.append(volume.getPath());
                        trace.append(" (Scan Dir/File ");
                        trace.append(taskProgress.getScanDirCount());
                        trace.append("/");
                        trace.append(taskProgress.getScanFileCount());
                        trace.append(", Del ");
                        trace.append(taskProgress.getScanDeleteCount());
                        trace.append(") ");

                        String line = null;
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
                    logInfo(trace.toString());
                }
            }
        } finally {
            printWriterMap.values().forEach(CloseableUtil::closeLogAndIgnoreException);
        }

        logInfo("start() - Completed file system clean task in {}", logExecutionTime);
    }
}
