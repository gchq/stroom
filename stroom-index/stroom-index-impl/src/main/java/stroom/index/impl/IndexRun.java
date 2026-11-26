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

import stroom.index.shared.IndexShardKey;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;

/// What an indexing filter does for the run of the processor task it is part of: it keeps the record of the
/// shards the run writes to, and says how indexing went, which decides how the run ends. A pipeline that is
/// not run by a task, e.g. when stepping, records and says nothing.
class IndexRun {

    private final IndexRunTracker tracker;
    private final RunIndexing runIndexing;
    private boolean held;

    private IndexRun(final IndexRunTracker tracker, final RunIndexing runIndexing) {
        this.tracker = tracker;
        this.runIndexing = runIndexing;
    }

    /// Starts the record keeping for the run the pipeline is part of, if it is a run of a processor task.
    ///
    /// @param holder  Holds the task the pipeline is being run for, if any.
    /// @param factory Makes the record keeping.
    /// @return What the filter is to use.
    static IndexRun start(final StreamProcessorHolder holder, final IndexRunTrackerFactory factory) {
        final ProcessorTask task = holder == null
                ? null
                : holder.getStreamTask();
        final RunIndexing runIndexing = holder == null
                ? null
                : holder.getRunIndexing();
        if (task == null || runIndexing == null || task.getMetaId() == null) {
            return new IndexRun(IndexRunTracker.NONE, null);
        }
        return new IndexRun(factory.create(task, runIndexing), runIndexing);
    }

    /// Adds a document to the index, and says how it went.
    ///
    /// @param indexer  Adds the document.
    /// @param key      Says which index and partition.
    /// @param document The document.
    void addDocument(final Indexer indexer, final IndexShardKey key, final IndexDocument document) {
        try {
            indexer.addDocument(key, document, tracker);
        } catch (final RunStoppedException e) {
            // How the run is to end has been said already.
            throw e;
        } catch (final DocumentRefusedException e) {
            // The cause is in the stream, so it would be refused every time.
            couldNotIndex(e.getMessage(), true);
            throw e;
        } catch (final RuntimeException e) {
            // The index could not be written to, which every task would hit.
            couldNotIndex(e.getMessage(), false);
            throw e;
        }
        // Held as soon as anything has been written, so that the task waits for it to be committed even if
        // the pipeline goes on to fail.
        if (!held && runIndexing != null && tracker.hasWritten()) {
            runIndexing.hold();
            held = true;
        }
    }

    private void couldNotIndex(final String reason, final boolean counted) {
        if (runIndexing != null) {
            runIndexing.couldNotIndex(reason, counted);
        }
    }
}
