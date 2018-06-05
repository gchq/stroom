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

package stroom.search.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.expression.v1.Val;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexField;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.search.Event;
import stroom.security.Security;
import stroom.streamstore.meta.StreamMetaService;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamMapCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamMapCreator.class);

    private final ErrorReceiver errorReceiver;
    private final StreamMetaService streamMetaService;

    private final int streamIdIndex;
    private final int eventIdIndex;

    private final Security security;
    private Map<Long, Boolean> fiteredStreamCache;

    public StreamMapCreator(final IndexField[] storedFields,
                            final ErrorReceiver errorReceiver,
                            final StreamMetaService streamMetaService,
                            final Security security) {
        this.errorReceiver = errorReceiver;
        this.streamMetaService = streamMetaService;
        this.security = security;

        // First get the index in the stored data of the stream and event id fields.
        streamIdIndex = getFieldIndex(storedFields, IndexConstants.STREAM_ID, true);
        eventIdIndex = getFieldIndex(storedFields, IndexConstants.EVENT_ID, true);
    }

    private int getFieldIndex(final IndexField[] storedFields, final String fieldName, final boolean warn) {
        int index = -1;

        for (int i = 0; i < storedFields.length && index == -1; i++) {
            final IndexField storedField = storedFields[i];
            if (storedField.getFieldName().equals(fieldName)) {
                index = i;
            }
        }

        if (warn && index == -1) {
            warn("The " + fieldName + " has not been stored in this index", null);
        }

        return index;
    }

    void addEvent(final Map<Long, List<Event>> storedDataMap, final Val[] storedData) {
        security.useAsRead(() -> {
            final Long longStreamId = getLong(storedData, streamIdIndex);
            final Long longEventId = getLong(storedData, eventIdIndex);

            if (longStreamId != null && longEventId != null) {
                // Filter the streams by ones that should be visible to the current user.
                if (canRead(longStreamId)) {
                    storedDataMap.compute(longStreamId, (k, v) -> {
                        if (v == null) {
                            v = new ArrayList<>();
                        }
                        v.add(new Event(longEventId, storedData));
                        return v;
                    });
                }
            }
        });
    }

    private Boolean canRead(final long streamId) {
        // Create a map to cache stream lookups. If we have cached more than a million streams then discard the map and start again to avoid using too much memory.
        if (fiteredStreamCache == null || fiteredStreamCache.size() > 1000000) {
            fiteredStreamCache = new HashMap<>();
        }

        return fiteredStreamCache.computeIfAbsent(streamId, k -> {
            // See if we can read the stream.
            return streamMetaService.canReadStream(streamId);
        });
    }

    private Long getLong(final Val[] storedData, final int index) {
        try {
            if (index >= 0 && storedData.length > index) {
                final Val value = storedData[index];
                return value.toLong();
            }
        } catch (final Exception e) {
            // Ignore
        }

        return null;
    }

    private void warn(final String message, final Throwable t) {
        errorReceiver.log(Severity.WARNING, null, null, message, t);
    }
}
