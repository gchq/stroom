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

package stroom.search.solr.search;

import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.search.api.EventRefs;
import stroom.search.coprocessor.EventRefsPayload;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class EventSearchResultHandler implements ResultHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventSearchResultHandler.class);

    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final Lock lock = new ReentrantLock();

    private volatile EventRefs eventRefs;

    @Override
    public void handle(final Map<CoprocessorKey, Payload> payloadMap) {
        try {
            if (payloadMap != null) {
                for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                    final Payload payload = entry.getValue();
                    if (payload instanceof EventRefsPayload) {
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
                pendingMerges.clear();
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
                tryMergePending();
            }
        }
    }

    private void tryMergePending() throws InterruptedException {
        // Only 1 thread will get to do a merge.
        if (lock.tryLock()) {
            try {
                mergePending();
            } finally {
                lock.unlock();
            }
        } else {
            LOGGER.trace("Another thread is busy merging, so will let it merge my items");
        }
    }

    private void mergePending() throws InterruptedException {
        EventRefs eventRefs = pendingMerges.poll();
        while (eventRefs != null) {
            if (Thread.currentThread().isInterrupted()) {
                // Clear the queue if we are done.
                pendingMerges.clear();
                throw new InterruptedException();

            } else {
                try {
                    mergeRefs(eventRefs);
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw e;
                }
            }

            // Poll the next item.
            eventRefs = pendingMerges.poll();
        }
    }

    private void mergeRefs(final EventRefs newEventRefs) {
        if (eventRefs == null) {
            eventRefs = newEventRefs;
        } else {
            eventRefs.add(newEventRefs);
        }
    }

    public EventRefs getEventRefs() {
        return eventRefs;
    }

    @Override
    public Data getResultStore(final String componentId) {
        return null;
    }

    @Override
    public void waitForPendingWork() throws InterruptedException {
        // This assumes that when this method has been called, all calls to addQueue
        // have been made, thus we will lock and perform a final merge.
        lock.lock();
        try {
            // Perform final merge.
            mergePending();
        } finally {
            lock.unlock();
        }
    }
}
