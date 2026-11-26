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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorTask;

import java.util.Objects;

/// The result of trying to change the status of a task.
///
/// @param result Whether it was changed.
/// @param task   The task as it now is if it was changed, otherwise null.
public record TaskStatusChange(Result result, ProcessorTask task) {

    private static final TaskStatusChange LOST = new TaskStatusChange(Result.LOST, null);
    private static final TaskStatusChange UNKNOWN = new TaskStatusChange(Result.UNKNOWN, null);

    /// Creates the result of changing a task.
    ///
    /// @param task The task as it now is.
    /// @return The result.
    public static TaskStatusChange done(final ProcessorTask task) {
        Objects.requireNonNull(task, "Null task");
        return new TaskStatusChange(Result.DONE, task);
    }

    /// Gets the result for a task that had been changed by something else, so was left as it was.
    ///
    /// @return The result.
    public static TaskStatusChange lost() {
        return LOST;
    }

    /// Gets the result when there is no way to tell whether the task was changed, e.g. as the database could not be
    /// reached.
    ///
    /// @return The result.
    public static TaskStatusChange unknown() {
        return UNKNOWN;
    }

    /// Finds out whether the task was changed.
    ///
    /// @return True if it was.
    public boolean isDone() {
        return Result.DONE.equals(result);
    }


    // --------------------------------------------------------------------------------


    public enum Result {
        DONE,
        LOST,
        UNKNOWN
    }
}
