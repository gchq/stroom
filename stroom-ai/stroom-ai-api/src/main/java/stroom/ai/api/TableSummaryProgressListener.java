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

package stroom.ai.api;

/**
 * Told how a summary is progressing, so that a caller with someone waiting on it can say what is happening.
 * Every method has a do-nothing default, as a caller with nobody waiting has nothing to say.
 * <p>
 * The wording is left to the caller because it depends on where the data came from, which the summariser
 * does not know.
 * </p>
 */
public interface TableSummaryProgressListener {

    TableSummaryProgressListener NO_OP = new TableSummaryProgressListener() {
    };

    /**
     * The data has been read and split, and the calls to the model are about to start.
     *
     * @param batchCount  How many batches the data was split into.
     * @param sourceCount How many sources those batches came from.
     */
    default void onBatchesBuilt(final int batchCount, final int sourceCount) {
    }

    /**
     * @param batchNumber One-based.
     */
    default void onBatchStarted(final int batchNumber, final int batchCount) {
    }

    /**
     * Cancelled, and now waiting a bounded time for the batches already in flight.
     */
    default void onCancelledAwaitingBatches() {
    }

    /**
     * Cancelled, and now merging what was produced before the cancellation.
     *
     * @param summaryCount How many batch summaries there are to merge.
     */
    default void onCancelledBeforeMerge(final int summaryCount) {
    }
}
