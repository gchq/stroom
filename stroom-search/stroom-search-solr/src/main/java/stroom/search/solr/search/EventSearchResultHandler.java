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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.search.coprocessor.EventRefsPayload;
import stroom.search.server.EventRefs;
import stroom.util.shared.HasTerminate;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventSearchResultHandler implements ResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchResultHandler.class);

    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final Lock lock = new ReentrantLock();

    private volatile EventRefs eventRefs;

    @Override
    public void handle(final Map<CoprocessorKey, Payload> payloadMap, final HasTerminate hasTerminate) {
        if (payloadMap != null) {
            for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                final Payload payload = entry.getValue();
                if (payload instanceof EventRefsPayload) {
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
                pendingMerges.clear();

            } else {
                // Add the new queue to the pending merge queue ready for merging.
                try {
                    pendingMerges.put(eventRefs);
                } catch (final InterruptedException | RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                // Try and merge all of the items on the pending merge queue.
                tryMergePending(hasTerminate);
            }
        }
    }

    private void tryMergePending(final HasTerminate hasTerminate) {
        // Only 1 thread will get to do a merge.
        if (lock.tryLock()) {
            try {
                mergePending(hasTerminate);
            } finally {
                lock.unlock();
            }
        } else {
            LOGGER.trace("Another thread is busy merging, so will let it merge my items");
        }
    }

    private void mergePending(final HasTerminate hasTerminate) {
        EventRefs eventRefs = pendingMerges.poll();
        while (eventRefs != null) {
            if (hasTerminate.isTerminated()) {
                // Clear the queue if we are done.
                pendingMerges.clear();

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
    public void waitForPendingWork(final HasTerminate hasTerminate) {
        // This assumes that when this method has been called, all calls to addQueue
        // have been made, thus we will lock and perform a final merge.
        lock.lock();
        try {
            // Perform final merge.
            mergePending(hasTerminate);
        } finally {
            lock.unlock();
        }
    }
}
