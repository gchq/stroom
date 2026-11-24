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

import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.shared.ProcessorTask;
import stroom.util.pipeline.scope.PipelineScoped;

import jakarta.inject.Inject;

/// The record of the stream being indexed by the pipeline that is running, shared by every indexing filter
/// in the pipeline. The record belongs to the stream and its task, not to a filter, so it begins when the
/// first filter starts and ends when the last one finishes.
///
/// Nothing is recorded when the pipeline is not running a processor task on a stream, as there is then no
/// task to run again.
@PipelineScoped
public class PipelinePendingStreams {

    private final IndexShardPendingStreams indexShardPendingStreams;
    private final MetaHolder metaHolder;
    private final StreamProcessorHolder streamProcessorHolder;

    private PendingStreamTracker tracker;
    private int users;
    private boolean failed;

    @Inject
    PipelinePendingStreams(final IndexShardPendingStreams indexShardPendingStreams,
                           final MetaHolder metaHolder,
                           final StreamProcessorHolder streamProcessorHolder) {
        this.indexShardPendingStreams = indexShardPendingStreams;
        this.metaHolder = metaHolder;
        this.streamProcessorHolder = streamProcessorHolder;
    }

    /// Called by an indexing filter when it starts processing.
    ///
    /// @return What the filter must pass on with each document it indexes, or null if nothing is being
    /// recorded.
    /// @throws stroom.index.shared.IndexException If an earlier attempt's documents could not be removed.
    public PendingStreamTracker start() {
        users++;
        if (tracker == null && !failed) {
            final Meta meta = metaHolder == null
                    ? null
                    : metaHolder.getMeta();
            final ProcessorTask task = streamProcessorHolder == null
                    ? null
                    : streamProcessorHolder.getStreamTask();
            if (meta != null && task != null) {
                try {
                    tracker = indexShardPendingStreams.begin(meta.getId(), task.getId());
                } catch (final RuntimeException e) {
                    failed = true;
                    throw e;
                }
            }
        }
        return tracker;
    }

    /// Called by an indexing filter that could not index a document. The stream's documents will be
    /// removed from the index and its task run again.
    public void fail() {
        failed = true;
        if (tracker != null) {
            tracker.fail();
        }
    }

    /// Called by an indexing filter when it has finished processing.
    ///
    /// @return False if the stream has not been indexed in full, because a shard it was written to lost
    /// its documents. They will be removed from the index and the task run again.
    public boolean end() {
        users--;
        if (tracker != null && users <= 0) {
            // The failure has been reported already by the filter that had it.
            final boolean complete = tracker.end();
            return complete || failed;
        }
        return true;
    }
}
