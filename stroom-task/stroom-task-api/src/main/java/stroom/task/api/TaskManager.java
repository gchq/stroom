/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.api;

import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;

public interface TaskManager {

    void startup();

    void shutdown();

    /**
     * Find out if the task with the given id is terminated.
     */
    boolean isTerminated(TaskId taskId);

    /**
     * Terminate a task by task id.
     *
     * @param taskId The id of the task to terminate.
     */
    void terminate(TaskId taskId);

    TaskProgress getTaskProgress(TaskContext taskContext);
}
