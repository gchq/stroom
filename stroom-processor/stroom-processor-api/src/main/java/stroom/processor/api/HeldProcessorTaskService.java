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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Ends processor tasks that a run has held as `Committing` with [RunIndexing#hold], and tells what state tasks are
/// in, so that an index can put right anything it has missed. Any of these can be called at any time, from any node,
/// but only as the processing user.
///
/// A `Committing` task is left alone by everything else: the reaper, the heartbeat, a node starting, aborting,
/// deleting its filter, and every limit on how many tasks can be processed. The one exception is a filter that was
/// deleted longer ago than `stroom.processor.deleteAge`, whose tasks are all set to `Deleted`, so a held task can
/// still become `Deleted`, after which it can't be completed or put back.
public interface HeldProcessorTaskService {

    /// Sets a task that is `Committing` to `Complete`.
    ///
    /// @param taskId   The id of the task.
    /// @param runCount The run that held it.
    /// @return False if the task isn't `Committing` from that run, e.g. as it has already been completed or put back.
    boolean completeHeld(long taskId, int runCount);

    /// Puts back a task that is `Committing`, so that it is run again, without counting a fault, e.g. as documents
    /// its run indexed were lost before they were committed.
    ///
    /// @param taskId   The id of the task.
    /// @param runCount The run that held it.
    /// @param reason   Why it was put back, to be shown with the task.
    /// @return False if the task isn't `Committing` from that run.
    boolean putBack(long taskId, int runCount, String reason);

    /// Gets the status of tasks, and the run each is on.
    ///
    /// @param taskIds The ids of the tasks. Any number can be asked for, as they are looked up in batches.
    /// @return The state of each task, by its id. A task that no longer exists is `Deleted`.
    Map<Long, ProcessorTaskState> getStates(Collection<Long> taskIds);

    /// Finds tasks that are `Committing`, e.g. so that an index can complete any that it has nothing left to commit
    /// for, which would otherwise be left `Committing` for ever.
    ///
    /// @param olderThan Only tasks that were held before this are found.
    /// @param afterId   Only tasks with a higher id than this are found, to page through them.
    /// @param limit     The most to find.
    /// @return The tasks, in order of their ids.
    List<ProcessorTaskState> findHeld(Instant olderThan, long afterId, int limit);
}
