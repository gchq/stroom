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

/// A row of `index_shard_purge`, which says that the documents of a stream must be deleted from an index
/// shard, whichever task indexed them.
///
/// @param indexShardId The id of the shard.
/// @param streamId     The id of the stream.
/// @param writerId     The opening of the shard that has deleted the documents but not yet committed, or null
///                     while the purge is waiting.
/// @param version      Goes up with every change to the row.
/// @param createTimeMs When the purge was first asked for, in database time.
public record IndexShardPurge(long indexShardId,
                              long streamId,
                              String writerId,
                              int version,
                              long createTimeMs) {

    /// Says whether the purge is waiting to be done.
    ///
    /// @return True if no writer has deleted the documents yet.
    public boolean isWaiting() {
        return writerId == null;
    }
}
