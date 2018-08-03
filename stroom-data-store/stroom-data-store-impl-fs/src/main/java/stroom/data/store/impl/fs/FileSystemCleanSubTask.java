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

import stroom.node.shared.VolumeEntity;
import stroom.task.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.task.api.ServerTask;

class FileSystemCleanSubTask extends ServerTask<VoidResult> {
    private final VolumeEntity volume;
    private final String path;
    private final String logPrefix;
    private final Task<?> parentTask;
    private final FileSystemCleanExecutor parentHandler;
    private final FileSystemCleanProgress taskProgress;

    FileSystemCleanSubTask(final FileSystemCleanExecutor parentHandler, final Task<?> parentTask,
                           final FileSystemCleanProgress taskProgress, final VolumeEntity volume, final String path,
                           final String logPrefix) {
        super(parentTask);
        this.volume = volume;
        this.path = path;
        this.logPrefix = logPrefix;
        this.parentHandler = parentHandler;
        this.parentTask = parentTask;
        this.taskProgress = taskProgress;
    }

    VolumeEntity getVolume() {
        return volume;
    }

    String getPath() {
        return path;
    }

    String getLogPrefix() {
        return logPrefix;
    }

    @Override
    public Task<?> getParentTask() {
        return parentTask;
    }

    FileSystemCleanExecutor getParentHandler() {
        return parentHandler;
    }

    FileSystemCleanProgress getTaskProgress() {
        return taskProgress;
    }
}
