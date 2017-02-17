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

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import stroom.streamstore.server.ScanVolumePathResult;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskCallbackAdaptor;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.VoidResult;
import stroom.util.task.TaskMonitor;

/**
 * Task to clean the stream store.
 */
@TaskHandlerBean(task = FileSystemCleanSubTask.class)
@Scope(value = StroomScope.TASK)
public class FileSystemCleanSubTaskHandler extends AbstractTaskHandler<FileSystemCleanSubTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemCleanSubTaskHandler.class);

    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private TaskMonitor taskMonitor;

    private static class FileSystemCleanProgressCallback extends TaskCallbackAdaptor<VoidResult> {
        private final FileSystemCleanProgress taskProgress;

        public FileSystemCleanProgressCallback(final FileSystemCleanProgress taskProgress) {
            this.taskProgress = taskProgress;
        }

        @Override
        public void onSuccess(final VoidResult result) {
            taskProgress.addScanComplete();
        }

        @Override
        public void onFailure(final Throwable t) {
            taskProgress.addScanComplete();
        }

    }

    @Override
    public VoidResult exec(final FileSystemCleanSubTask task) {
        taskMonitor.info("Cleaning: {} - {}", task.getVolume().getPath(), task.getPath());

        if (taskMonitor.isTerminated() || task.getParentTask().isTerminated()) {
            LOGGER.info("exec() - Been asked to Quit");
            return VoidResult.INSTANCE;
        }

        final ScanVolumePathResult result = streamMaintenanceService.scanVolumePath(task.getVolume(),
                task.getParentHandler().isDelete(), task.getPath(), task.getParentHandler().getOldAge());

        task.getTaskProgress().addResult(result);

        // Add a log line to indicate progress 1/3,44/100
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("createRunableForPath() -" + task.getLogPrefix() + "  - " + task.getPath() + ".  Scanned "
                    + ModelStringUtil.formatCsv(result.getFileCount()) + " files, deleted "
                    + ModelStringUtil.formatCsv(result.getDeleteList().size()) + ", too new to delete "
                    + ModelStringUtil.formatCsv(result.getTooNewToDeleteCount()) + ".  Totals "
                    + task.getTaskProgress().traceInfo());
        }

        if (taskMonitor.isTerminated() || task.getParentTask().isTerminated()) {
            LOGGER.info("exec() - Been asked to Quit");
            return VoidResult.INSTANCE;
        }

        if (result.getChildDirectoryList() != null && result.getChildDirectoryList().size() > 0) {
            // Add to the task steps remaining.
            task.getTaskProgress().addScanPending(result.getChildDirectoryList().size());

            for (final String subPath : result.getChildDirectoryList()) {
                final FileSystemCleanSubTask subTask = new FileSystemCleanSubTask(task.getParentHandler(),
                        task.getParentTask(), task.getTaskProgress(), task.getVolume(), subPath, task.getLogPrefix());
                if (!taskMonitor.isTerminated()) {
                    task.getParentHandler().getAsyncTaskHelper().fork(subTask,
                            new FileSystemCleanProgressCallback(task.getTaskProgress()));
                }
            }
        }

        return VoidResult.INSTANCE;
    }
}
