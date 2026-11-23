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

/// A row that records a stream with documents in an index shard that are not yet known to be committed, or
/// the row that counts the attempts made to index a stream.
///
/// @param id       The database ID of the row.
/// @param shardId  The id of the shard, or [IndexShardPendingStreamDao#ATTEMPT_ROW_SHARD_ID] for the row
///                 that counts attempts.
/// @param streamId The id of the stream.
/// @param taskId   The id of the processor task that is indexing the stream.
/// @param attempt  The number of attempts that have failed. Only held on the attempt row.
/// @param state    The state of the row.
public record PendingStream(long id,
                            long shardId,
                            long streamId,
                            long taskId,
                            int attempt,
                            PendingStreamState state) {

}
