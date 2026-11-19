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

/// Starts the record kept of each stream's uncommitted documents while a task indexes it.
public interface IndexShardPendingStreams {

    /// Begins an attempt to index a stream. Anything an earlier attempt by the same task left in the index
    /// is removed first, which this waits for.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task that is indexing it.
    /// @return What to tell as documents are added, or null if no record is to be kept.
    /// @throws stroom.index.shared.IndexException If an earlier attempt's documents could not be removed
    ///                                            in a reasonable time.
    PendingStreamTracker begin(long streamId, long taskId);
}
