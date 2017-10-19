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

package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.NodeCache;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeService;
import stroom.task.server.AsyncTaskHelper;
import stroom.task.server.TaskCallbackAdaptor;
import stroom.task.server.TaskManager;
import stroom.util.config.PropertyUtil;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task to clean the stream store.
 */
@Component
@Scope(value = StroomScope.TASK)
public class FileSystemCleanExecutor {
    public static final String DELETE_OUT = "delete.out";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemCleanExecutor.class);
    private final VolumeService volumeService;
    private final TaskMonitor taskMonitor;
    private final TaskManager taskManager;
    private final NodeCache nodeCache;
    private final int batchSize;
    private final long oldAge;
    private final boolean deleteOut;

    private AsyncTaskHelper<VoidResult> asyncTaskHelper;

    @Inject
    public FileSystemCleanExecutor(final VolumeService volumeService,
                                   final TaskMonitor taskMonitor,
                                   final TaskManager taskManager,
                                   final NodeCache nodeCache,
                                   @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanBatchSize')}") final String fileSystemCleanBatchSize,
                                   @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanOldAge')}") final String oldFileAge,
                                   @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanDeleteOut')}") final String matchFile) {
        this.volumeService = volumeService;
        this.taskMonitor = taskMonitor;
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
        this.batchSize = PropertyUtil.toInt(fileSystemCleanBatchSize, 20);

        Long age;
        try {
            age = ModelStringUtil.parseDurationString(oldFileAge);
            if (age == null) {
                age = ModelStringUtil.parseDurationString("1d");
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.fileSystemCleanOldAge' value '" + oldFileAge
                    + "', using default of '1d' instead", e);
            age = ModelStringUtil.parseDurationString("1d");
        }
        this.oldAge = age;

        this.deleteOut = PropertyUtil.toBoolean(matchFile, false);
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
        taskMonitor.info(args);
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
        asyncTaskHelper = new AsyncTaskHelper<>(null, taskMonitor, taskManager, batchSize);

        logInfo("Starting file system clean task. oldAge = {}", ModelStringUtil.formatDurationString(oldAge));

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // Get the progress monitor and add a stop listener.
        taskMonitor.addTerminateHandler(() -> {
            logInfo("Stopping file system clean task.");
            asyncTaskHelper.clear();
        });

        final FindVolumeCriteria criteria = new FindVolumeCriteria();
        criteria.getNodeIdSet().add(nodeId);
        final List<Volume> volumeList = volumeService.find(criteria);

        try {
            if (volumeList != null && volumeList.size() > 0) {
                // Add to the task steps remaining.

                for (final Volume volume : volumeList) {
                    final FileSystemCleanProgress taskProgress = new FileSystemCleanProgress();
                    if (deleteOut) {
                        final File dir = new File(volume.getPath());
                        if (dir.isDirectory()) {
                            try {
                                printWriterMap
                                        .put(volume,
                                                new PrintWriter(new OutputStreamWriter(
                                                        new FileOutputStream(new File(dir, DELETE_OUT)),
                                                        StreamUtil.DEFAULT_CHARSET)));
                            } catch (final Exception ex) {
                                LOGGER.error("exec() - Error opening file", ex);
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
                    // Wait for all task steps to complete.
                    ThreadUtil.sleep(500);

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
                        } catch (final Exception ex) {
                            LOGGER.error("exec() - Error writing " + DELETE_OUT, ex);
                            task.terminate();
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
