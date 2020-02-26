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

package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.impl.DataStoreMaintenanceService;
import stroom.data.store.impl.ScanVolumePathResult;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskCallbackAdaptor;
import stroom.task.api.TaskContext;
import stroom.util.shared.ModelStringUtil;
import stroom.task.api.VoidResult;

import javax.inject.Inject;

/**
 * Task to clean the stream store.
 */

class FsCleanSubTaskHandler extends AbstractTaskHandler<FsCleanSubTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsCleanSubTaskHandler.class);

    private final DataStoreMaintenanceService streamMaintenanceService;
    private final TaskContext taskContext;
    private final SecurityContext securityContext;

    @Inject
    FsCleanSubTaskHandler(final DataStoreMaintenanceService streamMaintenanceService,
                          final TaskContext taskContext,
                          final SecurityContext securityContext) {
        this.streamMaintenanceService = streamMaintenanceService;
        this.taskContext = taskContext;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final FsCleanSubTask task) {
        return securityContext.secureResult(() -> {
            taskContext.info(() -> "Cleaning: " + task.getVolume().getPath() + " - " + task.getPath());

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("exec() - Been asked to Quit");
                return VoidResult.INSTANCE;
            }

            final ScanVolumePathResult result = streamMaintenanceService.scanVolumePath(
                task.getVolume(),
                task.getParentHandler().isDelete(),
                task.getPath(),
                task.getParentHandler().getOldAge());

            task.getTaskProgress().addResult(result);

            // Add a log line to indicate progress 1/3,44/100
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("createRunableForPath() -" + task.getLogPrefix() + "  - " + task.getPath() + ".  Scanned "
                        + ModelStringUtil.formatCsv(result.getFileCount()) + " files, deleted "
                        + ModelStringUtil.formatCsv(result.getDeleteList().size()) + ", too new to delete "
                        + ModelStringUtil.formatCsv(result.getTooNewToDeleteCount()) + ".  Totals "
                        + task.getTaskProgress().traceInfo());
            }

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("exec() - Been asked to Quit");
                return VoidResult.INSTANCE;
            }

            if (result.getChildDirectoryList() != null && result.getChildDirectoryList().size() > 0) {
                // Add to the task steps remaining.
                task.getTaskProgress().addScanPending(result.getChildDirectoryList().size());

                for (final String subPath : result.getChildDirectoryList()) {
                    final FsCleanSubTask subTask = new FsCleanSubTask(task.getParentHandler(),
                            task.getParentTask(), task.getTaskProgress(), task.getVolume(), subPath, task.getLogPrefix());
                    if (!Thread.currentThread().isInterrupted()) {
                        task.getParentHandler().getAsyncTaskHelper().fork(subTask,
                                new FileSystemCleanProgressCallback(task.getTaskProgress()));
                    }
                }
            }

            return VoidResult.INSTANCE;
        });
    }

    private static class FileSystemCleanProgressCallback extends TaskCallbackAdaptor<VoidResult> {
        private final FsCleanProgress taskProgress;

        FileSystemCleanProgressCallback(final FsCleanProgress taskProgress) {
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
}
