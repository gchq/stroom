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

import java.util.Objects;

/// A row of `index_shard_task`, which says that a run of a processor task has written to an index shard.
///
/// @param taskId       The id of the processor task.
/// @param indexShardId The id of the shard.
/// @param streamId     The id of the stream the task indexes, which is what is purged if it comes to that.
/// @param run          The task's run count when the row was last written to.
/// @param writerId     The opening of the shard that holds the run's documents.
/// @param state        How far the run's documents have got.
/// @param updateTimeMs When the row last changed, in database time.
public record IndexShardTaskRow(long taskId,
                                long indexShardId,
                                long streamId,
                                int run,
                                String writerId,
                                IndexShardTaskState state,
                                long updateTimeMs) {

    /// Checks that the row has a writer and a state.
    ///
    /// @throws NullPointerException If either is missing.
    public IndexShardTaskRow {
        Objects.requireNonNull(writerId);
        Objects.requireNonNull(state);
    }
}
