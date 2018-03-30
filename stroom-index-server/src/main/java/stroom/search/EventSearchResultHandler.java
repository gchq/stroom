/*
 * Copyright 2017 Crown Copyright
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

package stroom.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.util.shared.HasTerminate;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class EventSearchResultHandler implements ResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchResultHandler.class);

    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final AtomicBoolean merging = new AtomicBoolean();
    private volatile EventRefs streamReferences;

    @Override
    public void handle(final Map<CoprocessorKey, Payload> payloadMap) {
        try {
            if (payloadMap != null) {
                for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                    final Payload payload = entry.getValue();
                    if (payload != null && payload instanceof EventRefsPayload) {
                        final EventRefsPayload eventRefsPayload = (EventRefsPayload) payload;
                        add(eventRefsPayload.getEventRefs());
                    }
                }
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();

            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // Non private for testing purposes.
    public void add(final EventRefs eventRefs) throws InterruptedException {
        if (eventRefs != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();

            } else {
                // Add the new queue to the pending merge queue ready for merging.
                try {
                    pendingMerges.put(eventRefs);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();

                    throw new RuntimeException(e.getMessage(), e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                // Try and merge all of the items on the pending merge queue.
                mergePending();
            }
        }
    }

    private void mergePending() throws InterruptedException {
        // Only 1 thread will get to do a merge.
        if (merging.compareAndSet(false, true)) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();

                } else {
                    EventRefs eventRefs = pendingMerges.poll();
                    while (eventRefs != null) {
                        try {
                            mergeRefs(eventRefs);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw e;
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException();
                        }

                        eventRefs = pendingMerges.poll();
                    }
                }
            } finally {
                merging.set(false);
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            // Make sure we don't fail to merge items from the queue that have
            // just been added by another thread that didn't get to do the
            // merge.
            if (pendingMerges.peek() != null) {
                mergePending();
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

    public EventRefs getStreamReferences() {
        return streamReferences;
    }

    @Override
    public Data getResultStore(final String componentId) {
        return null;
    }
}
