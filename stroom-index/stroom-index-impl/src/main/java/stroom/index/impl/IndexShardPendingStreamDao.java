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

import java.util.Collection;
import java.util.List;

/// Reads and writes the rows that record which streams have documents in an index shard that are not yet
/// known to be committed.
///
/// A task that indexes a stream adds a row for each shard it writes to. The rows are what lets a failure be
/// undone exactly: the stream's documents are removed from every shard it touched and the stream is indexed
/// again. Rows belong to a stream and the processor task that is indexing it, so the same stream indexed by
/// two pipelines is tracked twice.
public interface IndexShardPendingStreamDao {

    /// The shard id on the row that counts the attempts made to index a stream.
    long ATTEMPT_ROW_SHARD_ID = 0;

    /// Starts an attempt to index a stream. Whatever an earlier attempt by the same task left behind is
    /// marked to be removed, as the stream is about to be indexed from the start.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    /// @return The number of shards that still hold documents of an earlier attempt. The new attempt must
    /// not write until this is zero.
    int beginAttempt(long streamId, long taskId);

    /// Counts the shards that still hold documents of a stream that are to be removed.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    /// @return The number of shards.
    int countRowsToDelete(long streamId, long taskId);

    /// Records that a task is about to add a stream's documents to a shard.
    ///
    /// @param shardId  The id of the shard.
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    void addShard(long shardId, long streamId, long taskId);

    /// Forgets that a task was about to add a stream's documents to a shard, as it added none.
    ///
    /// @param shardId  The id of the shard.
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    void removeShard(long shardId, long streamId, long taskId);

    /// Ends an attempt to index a stream. Its rows become written, to be marked as committed when their
    /// shards next commit.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    /// @return False if the attempt has failed because a shard it wrote to lost its documents, in which
    /// case all the stream's documents are now marked to be removed.
    boolean endAttempt(long streamId, long taskId);

    /// Fails an attempt to index a stream. Its documents are marked to be removed from every shard it
    /// touched and the task is marked to be run again.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    void failAttempt(long streamId, long taskId);

    /// Fails an attempt whose task may carry on adding documents for a while, e.g. from later parts of the
    /// stream. Its documents are marked to be removed only once the task has stopped, which
    /// [#endAttempt(long, long)] says, and the task is marked to be run again.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    void abandonAttempt(long streamId, long taskId);

    /// Fails an attempt as [#failAttempt(long, long)] does, but only if it still has rows saying its
    /// documents are being written that have not changed since a given time.
    ///
    /// @param streamId       The id of the stream.
    /// @param taskId         The id of the processor task.
    /// @param notChangedSince The time in milliseconds.
    /// @return True if the attempt was failed.
    boolean failStaleAttempt(long streamId, long taskId, long notChangedSince);

    /// Finds the rows of a shard whose tasks have finished, which the shard's next commit will make safe.
    ///
    /// @param shardId The id of the shard.
    /// @return The database IDs of the rows.
    List<Long> findWrittenRowIds(long shardId);

    /// Marks rows as committed, if they are still written, and removes the rows of any stream that is now
    /// committed in every shard it touched.
    ///
    /// @param rowIds The database IDs of the rows, as found before the commit began.
    void markCommitted(Collection<Long> rowIds);

    /// Fails every stream that a shard holds documents for that are not known to be committed. All of
    /// those streams' documents, in every shard, are marked to be removed and their tasks to be run again.
    ///
    /// @param shardId The id of the shard, whose commit failed or whose writer stopped without one.
    /// @return The number of streams failed.
    int failShard(long shardId);

    /// Marks as committed the written rows of shards that have since been closed cleanly, which commits.
    ///
    /// @return The number of rows changed.
    int markCommittedInClosedShards();

    /// Removes the rows of shards that have been deleted. Their documents went with them.
    ///
    /// @return The number of rows removed.
    int deleteRowsOfDeletedShards();

    /// Removes the rows of streams that are committed in every shard they touched.
    ///
    /// @param limit The most streams to deal with.
    /// @return The number of streams whose rows were removed.
    int deleteCommittedStreams(int limit);

    /// Finds the shards that hold documents that are to be removed.
    ///
    /// @return The ids of the shards.
    List<Long> findShardIdsWithRowsToDelete();

    /// Finds the rows of a shard whose streams' documents can be removed now, which leaves out any whose
    /// task may still be adding documents.
    ///
    /// @param shardId The id of the shard.
    /// @return The rows.
    List<PendingStream> findRowsToDelete(long shardId);

    /// Removes rows, once the documents they record have been removed from the shard.
    ///
    /// @param rowIds The database IDs of the rows.
    void deleteRows(Collection<Long> rowIds);

    /// Finds attempts with rows saying documents are being written that have not changed for a while. The
    /// task may have ended without saying so.
    ///
    /// @param notChangedSince The time in milliseconds.
    /// @param limit           The most attempts to return.
    /// @return A row of each attempt.
    List<PendingStream> findStaleAttempts(long notChangedSince, int limit);

    /// Finds the streams whose tasks are to be run again and whose documents have all been removed.
    ///
    /// @param limit The most to return.
    /// @return The attempt row of each.
    List<PendingStream> findRetriesToFinish(int limit);

    /// Records that a stream's task has been put back to run again.
    ///
    /// @param attemptRowId The database ID of the stream's attempt row.
    void setRetrying(long attemptRowId);

    /// Removes every row of a stream.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    void deleteStream(long streamId, long taskId);

    /// Finds every row of a stream. For tests and diagnosis.
    ///
    /// @param streamId The id of the stream.
    /// @param taskId   The id of the processor task.
    /// @return The rows.
    List<PendingStream> find(long streamId, long taskId);
}
