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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class EventSearchResultHandler implements ResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchResultHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(EventSearchResultHandler.class);

    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final AtomicBoolean merging = new AtomicBoolean();
    private volatile EventRefs streamReferences;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

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

                    boolean didMergeItems = false;
                    if (eventRefs != null) {
                        didMergeItems = true;
                    }

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

                    if (didMergeItems) {
                        lock.lock();
                        try {
                            // signal any thread waiting on the condition to check the busy state
                            LOGGER.trace("Signal all threads to check busy state");
                            condition.signalAll();
                        } finally {
                            lock.unlock();
                        }
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
        } else {
            LOGGER.trace("Another thread is busy merging, so will let it merge my items");
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

    public boolean busy() {
        boolean isBusy = pendingMerges.size() > 0 || merging.get();
        LAMBDA_LOGGER.trace(() ->
                LambdaLogger.buildMessage("busy() called, pendingMerges: {}, merging: {}, returning {}",
                        pendingMerges.size(), merging.get(), isBusy));
        return isBusy;
    }

    /**
     * Will block until all pending work that the {@link ResultHandler} has is complete.
     */
    @Override
    public void waitForPendingWork() throws InterruptedException {
        // this assumes that when this method has been called, all calls to addQueue
        // have been made, thus we will wait for the queue to empty and an marge activity
        // to finish.

        lock.lock();
        try {

            while (busy()) {
                try {
                    // we have been signalled to wake up and check the busy state
                    condition.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.debug("Thread interrupted");
                    throw e;
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
