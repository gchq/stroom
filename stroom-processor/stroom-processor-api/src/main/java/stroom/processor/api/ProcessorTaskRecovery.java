/*
 * Copyright 2026 Crown Copyright
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


package stroom.processor.api;

import stroom.processor.shared.TaskStatus;

import java.util.Optional;

/// Lets something that stores what processor tasks write, such as the index, deal with a task whose output
/// it has lost and removed. The task is run again, or given up on, and an error stream is written against
/// the source stream to say what happened.
public interface ProcessorTaskRecovery {

    /// Gets the status of a task.
    ///
    /// @param taskId The id of the task.
    /// @return The status, or empty if there is no such task.
    Optional<TaskStatus> getStatus(long taskId);

    /// Puts a task back to be run again and writes an error stream saying why.
    ///
    /// @param taskId  The id of the task.
    /// @param message Says what happened to the task's output.
    /// @return False if the task is running, when nothing has been done and this should be tried again
    /// later. True otherwise, which includes there being no such task.
    boolean retry(long taskId, String message);

    /// Sets a task to failed, so that it is not run again, and writes an error stream saying why.
    ///
    /// @param taskId  The id of the task.
    /// @param message Says what happened to the task's output.
    /// @return False if the task is running, when nothing has been done and this should be tried again
    /// later. True otherwise, which includes there being no such task.
    boolean fail(long taskId, String message);
}
