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

package stroom.index.impl;

import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/// The runs of processor tasks that are indexing on this node, so that a run that has been replaced can be
/// stopped before what it wrote is purged. A run that is never taken out, e.g. as its pipeline failed, goes
/// once nothing else refers to it.
@Singleton
public class IndexRunRegistry {

    private final Set<TaskIndexRunTracker> runs = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    /// Adds a run that has started.
    ///
    /// @param run The run.
    void add(final TaskIndexRunTracker run) {
        runs.add(run);
    }

    /// Takes out the run of a task that has ended.
    ///
    /// @param taskId The id of the task.
    /// @param run    The task's run count for the run.
    void remove(final long taskId, final int run) {
        runs.removeIf(tracker -> tracker.getTaskId() == taskId && tracker.getRun() == run);
    }

    /// Finds the runs that have written a stream to a shard.
    ///
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream.
    /// @return The runs.
    List<TaskIndexRunTracker> findWriting(final long indexShardId, final long streamId) {
        synchronized (runs) {
            return runs.stream()
                    .filter(run -> run.getStreamId() == streamId && run.hasWrittenTo(indexShardId))
                    .toList();
        }
    }
}
