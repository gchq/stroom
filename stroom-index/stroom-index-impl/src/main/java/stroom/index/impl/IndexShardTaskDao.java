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
import java.util.Optional;

/// Reads and writes `index_shard_task`, the record of which index shards each run of a processor task has
/// written to. Changes that depend on what a row held when it was read are only made if the row still holds
/// it, so that two nodes acting on the same row can't both succeed.
public interface IndexShardTaskDao {

    /// Gets the row for a task and shard.
    ///
    /// @param taskId       The id of the processor task.
    /// @param indexShardId The id of the shard.
    /// @return The row, if there is one.
    Optional<IndexShardTaskRow> fetch(long taskId, long indexShardId);

    /// Gets every row of a task, of every run.
    ///
    /// @param taskId The id of the processor task.
    /// @return The rows.
    List<IndexShardTaskRow> findByTask(long taskId);

    /// Gets the rows that any task has for a stream in a shard.
    ///
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream.
    /// @return The rows.
    List<IndexShardTaskRow> findByShardAndStream(long indexShardId, long streamId);

    /// Adds a row for a run that is about to write to a shard, in the *writing* state.
    ///
    /// @param taskId       The id of the processor task.
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream the task indexes.
    /// @param run          The task's run count.
    /// @param writerId     The opening of the shard the run writes to.
    /// @return The row added, or empty if the task already has a row for the shard.
    Optional<IndexShardTaskRow> insertWriting(long taskId,
                                              long indexShardId,
                                              long streamId,
                                              int run,
                                              String writerId);

    /// Changes a row, if it is still as it was when read.
    ///
    /// @param expected The row as it was read.
    /// @param run      The run it is to belong to.
    /// @param writerId The writer that is to hold its documents.
    /// @param state    The state it is to have.
    /// @return The row as changed, or empty if it had changed since it was read or has gone.
    Optional<IndexShardTaskRow> update(IndexShardTaskRow expected,
                                       int run,
                                       String writerId,
                                       IndexShardTaskState state);

    /// Removes a row, if it is still as it was when read.
    ///
    /// @param expected The row as it was read.
    /// @return True if it was removed.
    boolean delete(IndexShardTaskRow expected);

    /// Marks the *writing* rows of a run as *written*, as the run has ended.
    ///
    /// @param taskId The id of the processor task.
    /// @param run    The run.
    /// @return The number of rows marked.
    int markWritten(long taskId, int run);

    /// Lists the *written* rows of a writer, before it commits. Their runs have ended, so all their documents
    /// are in the writer, and the commit that follows makes them safe.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer.
    /// @return The rows.
    List<IndexShardTaskRow> findWritten(long indexShardId, String writerId);

    /// Records what a writer's commit has made safe, in one transaction: the listed rows are marked
    /// *committed* and the listed purges removed. Each only if it is unchanged since it was listed. Nothing
    /// is changed unless the shard still has that writer, which is checked with the shard's row locked, so a
    /// node that has lost the shard without knowing can't record anything.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer that committed.
    /// @param rows         The rows [#findWritten(long, String)] listed before the commit began.
    /// @param purges       The purges the writer had deleted when the commit began.
    /// @return True if the shard still had that writer, so the commit was recorded.
    boolean markCommitted(long indexShardId,
                          String writerId,
                          Collection<IndexShardTaskRow> rows,
                          Collection<IndexShardPurge> purges);

    /// Removes the rows of one run of a task, and no others, once the task has completed.
    ///
    /// @param taskId The id of the processor task.
    /// @param run    The run.
    /// @return The number of rows removed.
    int deleteRun(long taskId, int run);

    /// Turns rows into purges of their streams from their shards, in one transaction. Each row is removed
    /// and its purge asked for only if the row is unchanged since it was read, so a row that another node
    /// has taken over or turned into a purge already is left alone.
    ///
    /// @param rows The rows as they were read.
    /// @return The rows that were turned into purges.
    List<IndexShardTaskRow> toPurges(Collection<IndexShardTaskRow> rows);

    /// Finds the rows whose documents have been lost, or might have been: rows that are not *committed*
    /// whose writer is no longer the shard's, or whose shard is marked corrupt. Rows of deleted shards are
    /// not included. In order of task and shard.
    ///
    /// @param after The row to start after, or null to start at the first.
    /// @param limit The most rows to return.
    /// @return The rows.
    List<IndexShardTaskRow> findLost(IndexShardTaskRow after, int limit);

    /// Finds the rows that have not changed since a time. In order of task and shard.
    ///
    /// @param updateTimeMs The time, in database time.
    /// @param after        The row to start after, or null to start at the first.
    /// @param limit        The most rows to return.
    /// @return The rows.
    List<IndexShardTaskRow> findNotUpdatedSince(long updateTimeMs, IndexShardTaskRow after, int limit);

    /// Removes the rows of shards that have been marked deleted, so that deleting a shard never makes a
    /// task wait or run again.
    ///
    /// @return The number of rows removed.
    int deleteForDeletedShards();

    /// Gets the current time in the database, which is what row times are in.
    ///
    /// @return The time in milliseconds.
    long getDatabaseTimeMs();
}
