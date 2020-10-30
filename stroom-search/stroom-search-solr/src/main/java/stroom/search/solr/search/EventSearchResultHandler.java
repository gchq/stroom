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

class EventSearchResultHandler implements ResultHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventSearchResultHandler.class);

    private volatile EventRefs eventRefs;

    @Override
    public boolean handle(final Map<CoprocessorKey, Payload> payloadMap) {
        try {
            boolean partialSuccess = true;
            if (payloadMap != null && payloadMap.size() > 0) {
                partialSuccess = false;
                for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                    final Payload payload = entry.getValue();
                    if (payload instanceof EventRefsPayload) {
                        final EventRefsPayload eventRefsPayload = (EventRefsPayload) payload;
                        final boolean success = add(eventRefsPayload.getEventRefs());
                        if (success) {
                            partialSuccess = true;
                        }
                    }
                }
            }
            return partialSuccess;
        } catch (final InterruptedException e) {
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();

            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // Non private for testing purposes.
    public boolean add(final EventRefs eventRefs) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (eventRefs != null) {
            // Add the new queue to the pending merge queue ready for merging.
            try {
                if (this.eventRefs == null) {
                    this.eventRefs = eventRefs;
                } else {
                    this.eventRefs.add(eventRefs);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return true;
    }

    public EventRefs getEventRefs() {
        return eventRefs;
    }

    @Override
    public Data getResultStore(final String componentId) {
        return null;
    }
}
