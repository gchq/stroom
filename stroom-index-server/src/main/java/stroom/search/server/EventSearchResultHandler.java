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
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.search.coprocessor.EventRefsPayload;
import stroom.util.shared.HasTerminate;

import java.util.Map;
import java.util.Map.Entry;

public class EventSearchResultHandler implements ResultHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchResultHandler.class);

    private volatile EventRefs eventRefs;

    @Override
    public boolean handle(final Map<CoprocessorKey, Payload> payloadMap, final HasTerminate hasTerminate) {
        boolean partialSuccess = true;
        if (payloadMap != null && payloadMap.size() > 0) {
            partialSuccess = false;
            for (final Entry<CoprocessorKey, Payload> entry : payloadMap.entrySet()) {
                final Payload payload = entry.getValue();
                if (payload instanceof EventRefsPayload) {
                    final EventRefsPayload eventRefsPayload = (EventRefsPayload) payload;
                    final boolean success = add(eventRefsPayload.getEventRefs(), hasTerminate);
                    if (success) {
                        partialSuccess = true;
                    }
                }
            }
        }
        return partialSuccess;
    }

    // Non private for testing purposes.
    public boolean add(final EventRefs eventRefs, final HasTerminate hasTerminate) {
        if (hasTerminate.isTerminated()) {
            return false;
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
