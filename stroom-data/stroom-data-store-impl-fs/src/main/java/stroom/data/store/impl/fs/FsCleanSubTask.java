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

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.task.api.TaskContext;

import java.time.Duration;

class FsCleanSubTask {
    private final FsVolume volume;
    private final String path;
    private final String logPrefix;
    private final FsCleanProgress taskProgress;
    private final Duration oldAge;
    private final boolean delete;

    FsCleanSubTask(final FsCleanProgress taskProgress,
                   final FsVolume volume,
                   final String path,
                   final String logPrefix,
                   final Duration oldAge,
                           final boolean delete) {
        this.volume = volume;
        this.path = path;
        this.logPrefix = logPrefix;
        this.taskProgress = taskProgress;
        this.oldAge = oldAge;
        this.delete = delete;
    }

    FsVolume getVolume() {
        return volume;
    }

    String getPath() {
        return path;
    }

    String getLogPrefix() {
        return logPrefix;
    }

    FsCleanProgress getTaskProgress() {
        return taskProgress;
    }

    Duration getOldAge() {
        return oldAge;
    }

    boolean isDelete() {
        return delete;
    }
}
