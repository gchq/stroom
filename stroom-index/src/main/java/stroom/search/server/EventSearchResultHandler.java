/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import stroom.query.Payload;
import stroom.query.ResultHandler;
import stroom.query.ResultStore;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.HasTerminate;

public class EventSearchResultHandler implements ResultHandler {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(EventSearchResultHandler.class);

    private final AtomicBoolean complete = new AtomicBoolean();
    private volatile EventRefs streamReferences;

    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final AtomicBoolean merging = new AtomicBoolean();

    @Override
    public void handle(final Map<Integer, Payload> payloadMap, final HasTerminate hasTerminate) {
        if (payloadMap != null) {
            for (final Entry<Integer, Payload> entry : payloadMap.entrySet()) {
                final Payload payload = entry.getValue();
                if (payload != null && payload instanceof EventRefsPayload) {
                    final EventRefsPayload eventRefsPayload = (EventRefsPayload) payload;
                    add(eventRefsPayload.getEventRefs(), hasTerminate);
                }
            }
        }
    }

    // Non private for testing purposes.
    public void add(final EventRefs eventRefs, final HasTerminate hasTerminate) {
        if (eventRefs != null) {
            if (hasTerminate.isTerminated()) {
                // Clear the queue if we should terminate.
                pendingMerges.clear();

            } else {
                // Add the new queue to the pending merge queue ready for
                // merging.
                try {
                    pendingMerges.put(eventRefs);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                // Try and merge all of the items on the pending merge queue.
                mergePending(hasTerminate);
            }
        }
    }

    private void mergePending(final HasTerminate hasTerminate) {
        // Only 1 thread will get to do a merge.
        if (merging.compareAndSet(false, true)) {
            try {
                if (hasTerminate.isTerminated()) {
                    // Clear the queue if we should terminate.
                    pendingMerges.clear();

                } else {
                    EventRefs eventRefs = pendingMerges.poll();
                    while (eventRefs != null) {
                        try {
                            mergeRefs(eventRefs);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw e;
                        }

                        if (hasTerminate.isTerminated()) {
                            // Clear the queue if we should terminate.
                            pendingMerges.clear();
                        }

                        eventRefs = pendingMerges.poll();
                    }
                }
            } finally {
                merging.set(false);
            }

            if (hasTerminate.isTerminated()) {
                // Clear the queue if we should terminate.
                pendingMerges.clear();
            }

            // Make sure we don't fail to merge items from the queue that have
            // just been added by another thread that didn't get to do the
            // merge.
            if (pendingMerges.peek() != null) {
                mergePending(hasTerminate);
            }
        }
    }

    private void mergeRefs(final EventRefs newEventRefs) {
        if (streamReferences == null) {
            streamReferences = newEventRefs;
        } else {
            streamReferences.add(newEventRefs);
        }
    }

    @Override
    public boolean shouldTerminateSearch() {
        return false;
    }

    @Override
    public void setComplete(final boolean complete) {
        this.complete.set(complete);
    }

    @Override
    public boolean isComplete() {
        return complete.get();
    }

    public EventRefs getStreamReferences() {
        return streamReferences;
    }

    @Override
    public ResultStore getResultStore(final String componentId) {
        return null;
    }
}
