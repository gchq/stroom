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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/// Records, for one attempt to index a stream, each shard the stream's documents are added to. The record
/// is made before the first document goes into a shard, so that whatever happens next there is a row
/// saying the shard may hold some of the stream.
///
/// Used by the one thread that runs the pipeline.
public class PendingStreamTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PendingStreamTracker.class);

    private final IndexShardPendingStreamDao pendingStreamDao;
    private final long streamId;
    private final long taskId;
    // What the map of document counts gives for a shard that has no row.
    private static final int NO_ROW = -1;

    // The number of documents added to each shard that has a row.
    private final Long2IntMap documentCounts = new Long2IntOpenHashMap();
    private boolean finished;
    private boolean failed;

    /// Creates a tracker for an attempt that has already been begun.
    ///
    /// @param pendingStreamDao Where the rows are kept.
    /// @param streamId         The id of the stream being indexed.
    /// @param taskId           The id of the processor task that is indexing it.
    public PendingStreamTracker(final IndexShardPendingStreamDao pendingStreamDao,
                                final long streamId,
                                final long taskId) {
        this.pendingStreamDao = pendingStreamDao;
        this.streamId = streamId;
        this.taskId = taskId;
        documentCounts.defaultReturnValue(NO_ROW);
    }

    /// Called before a document is added to a shard. Records the shard if this is the first.
    ///
    /// @param shardId The id of the shard.
    public void beforeAdd(final long shardId) {
        if (!documentCounts.containsKey(shardId)) {
            pendingStreamDao.addShard(shardId, streamId, taskId);
            documentCounts.put(shardId, 0);
        }
    }

    /// Called after a document has been added to a shard.
    ///
    /// @param shardId The id of the shard.
    public void added(final long shardId) {
        documentCounts.mergeInt(shardId, 1, Integer::sum);
    }

    /// Called when a document could not be added to a shard and will be offered to another. A shard that
    /// was given nothing is forgotten, as it may never commit again to clear its row.
    ///
    /// @param shardId The id of the shard.
    public void addFailed(final long shardId) {
        if (documentCounts.get(shardId) == 0) {
            try {
                pendingStreamDao.removeShard(shardId, streamId, taskId);
                documentCounts.remove(shardId);
            } catch (final RuntimeException e) {
                // The row is harmless. It is cleared when the shard is next closed.
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    /// Fails the attempt. The stream's documents are marked to be removed from every shard, once
    /// [#end()] has said that the task has stopped adding them, and its task is marked to be run again.
    /// The task may carry on for a while yet, e.g. with later parts of the stream.
    public void fail() {
        if (!finished && !failed) {
            failed = true;
            pendingStreamDao.abandonAttempt(streamId, taskId);
        }
    }

    /// Ends the attempt, once every document of the stream has been added.
    ///
    /// @return False if the attempt has failed, because [#fail()] was called or a shard it wrote to lost
    /// its documents. The stream's documents will be removed and its task run again.
    public boolean end() {
        if (finished) {
            return !failed;
        }
        finished = true;
        final boolean complete = pendingStreamDao.endAttempt(streamId, taskId);
        return complete && !failed;
    }

    /// Gets the id of the stream being indexed.
    ///
    /// @return The id of the stream.
    public long getStreamId() {
        return streamId;
    }
}
