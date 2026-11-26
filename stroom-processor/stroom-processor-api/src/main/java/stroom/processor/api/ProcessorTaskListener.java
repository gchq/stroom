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

import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

/// Told how each run of a processor task goes, e.g. by an index that keeps a row for each document a run indexes.
/// Listeners are bound in a Guice set, and are called as the processing user on the node running the task.
///
/// Every call after a status has been set may be missed, e.g. if the node dies, or if a listener throws an
/// exception, which is logged and changes nothing. A listener has to find any it has missed with
/// [HeldProcessorTaskService#getStates] and put things right.
///
/// The task passed to each call is the task as the run knows it, with the run's run count.
public interface ProcessorTaskListener {

    /// Called on the second and later runs of a task, before its pipeline is run and before the run has opened
    /// anything, once the output of earlier runs has been deleted.
    ///
    /// @param task The task.
    /// @throws RuntimeException If the listener could not get ready for the run, in which case the run ends as if it
    ///                          had hit a fault that isn't counted, and the task is tried again later.
    void beforeRerun(ProcessorTask task);

    /// Called once the task has been set to `Committing`, as the run was held with [RunIndexing#hold].
    ///
    /// @param task The task.
    void held(ProcessorTask task);

    /// Called once the task has been set to any status other than `Committing` at the end of a run.
    ///
    /// @param task   The task.
    /// @param status The status it has been set to.
    void ended(ProcessorTask task, TaskStatus status);

    /// Called when a run finds that its task has been taken from it, e.g. as it has been aborted, deleted or put back
    /// and run again. Never called if it isn't known whether the run's last change to its task was made.
    ///
    /// @param task The task.
    void replaced(ProcessorTask task);
}
