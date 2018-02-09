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

package stroom.search.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.CompletionListener;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.util.shared.HasTerminate;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventSearchResultHandler implements ResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchResultHandler.class);

    private final AtomicBoolean complete = new AtomicBoolean();
    private final LinkedBlockingQueue<EventRefs> pendingMerges = new LinkedBlockingQueue<>();
    private final AtomicBoolean merging = new AtomicBoolean();
    private volatile EventRefs streamReferences;
    private final Queue<CompletionListener> completionListeners = new ConcurrentLinkedQueue<>();

    @Override
    public void handle(final Map<CoprocessorKey, Payload> payloadMap, final HasTerminate hasTerminate) {
        if (payloadMap != null) {
            for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
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
                terminate();

            } else {
                // Add the new queue to the pending merge queue ready for merging.
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
                    terminate();

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
                            terminate();
                        }

                        eventRefs = pendingMerges.poll();
                    }
                }
            } finally {
                merging.set(false);
            }

            if (hasTerminate.isTerminated()) {
                terminate();
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
    public boolean isComplete() {
        return complete.get();
    }

    @Override
    public void setComplete(final boolean complete) {
        boolean previousValue = this.complete.get();
        this.complete.set(complete);
        if (complete && previousValue != complete) {
            //now notify any listeners
            for (CompletionListener listener; (listener = completionListeners.poll()) != null;){
                //when notified they will check isComplete
                listener.onCompletion();
            }
        }
    }

    @Override
    public void registerCompletionListener(final CompletionListener completionListener) {
        if (isComplete()) {
            //immediate notification
            completionListener.onCompletion();
        } else {
            completionListeners.add(Objects.requireNonNull(completionListener));
        }
    }

    public EventRefs getStreamReferences() {
        return streamReferences;
    }

    @Override
    public Data getResultStore(final String componentId) {
        return null;
    }

    private void terminate() {
        // Clear the queue if we should terminate.
        pendingMerges.clear();
        completionListeners.clear();
    }
}
