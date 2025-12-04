/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class LmdbPutFilterFactory {

    static LmdbPutFilter create(final CompiledSorters compiledSorters,
                                final CompiledDepths compiledDepths,
                                final Sizes maxResultSizes,
                                final AtomicLong totalResultCount,
                                final CompletionState completionState) {

        // Find out if we have any sorting.
        final boolean hasSort = compiledSorters.hasSort();
        final long maxResults = maxResultSizes.size(0);

        // Determine if we are going to limit the result count.
        final boolean limitResultCount = maxResults < Sizes.MAX_SIZE &&
                                         !hasSort &&
                                         !compiledDepths.hasGroup();
        if (limitResultCount) {
            return new LimitedPutFilter(
                    totalResultCount,
                    maxResults,
                    completionState);
        }

        return new BasicPutFilter(totalResultCount);
    }

    private static class BasicPutFilter implements LmdbPutFilter {

        private final AtomicLong totalResultCount;

        public BasicPutFilter(final AtomicLong totalResultCount) {
            this.totalResultCount = totalResultCount;
        }

        @Override
        public void put(final LmdbQueueItem queueItem, final Consumer<LmdbQueueItem> consumer) {
            totalResultCount.getAndIncrement();
            consumer.accept(queueItem);
        }
    }

    private static class LimitedPutFilter implements LmdbPutFilter {

        private final AtomicLong totalResultCount;
        private final AtomicBoolean hasEnoughData = new AtomicBoolean();
        private final long maxResults;
        private final CompletionState completionState;

        public LimitedPutFilter(final AtomicLong totalResultCount,
                                final long maxResults,
                                final CompletionState completionState) {
            this.totalResultCount = totalResultCount;
            this.maxResults = maxResults;
            this.completionState = completionState;
        }

        @Override
        public void put(final LmdbQueueItem queueItem, final Consumer<LmdbQueueItem> consumer) {
            // Some searches can be terminated early if the user is not sorting or grouping.
            boolean allow;
            // No sorting or grouping, so we can stop the search as soon as we have the number of results requested by
            // the client
            allow = !hasEnoughData.get();
            if (allow) {
                final long currentResultCount = totalResultCount.getAndIncrement();
                if (currentResultCount >= maxResults) {
                    allow = false;

                    // If we have enough data then we can stop transferring data and complete.
                    if (hasEnoughData.compareAndSet(false, true)) {
                        completionState.signalComplete();
                    }
                }
            }

            if (allow) {
                consumer.accept(queueItem);
            }
        }
    }
}
