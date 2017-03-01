/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server.extraction;

import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexField;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.search.server.Event;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamPermissionException;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class StreamMapCreator {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StreamMapCreator.class);

    private final ErrorReceiver errorReceiver;
    private final StreamStore streamStore;

    private final int streamIdIndex;
    private final int eventIdIndex;

    private final SecurityContext securityContext;
    private Map<Long, Stream> fiteredStreamCache;

    public StreamMapCreator(final IndexField[] storedFields, final ErrorReceiver errorReceiver,
                            final StreamStore streamStore, final SecurityContext securityContext) {
        this.errorReceiver = errorReceiver;
        this.streamStore = streamStore;
        this.securityContext = securityContext;

        // First get the index in the stored data of the stream and event id
        // fields.
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

    public HashMap<Long, List<Event>> createEventMap(final List<String[]> storedDataList) {
        // Put the events into a map to group them by stream id.
        final Map<Long, List<Event>> storedDataMap = new HashMap<Long, List<Event>>();
        for (final String[] storedData : storedDataList) {
            final Long longStreamId = getLong(storedData, streamIdIndex);
            final Long longEventId = getLong(storedData, eventIdIndex);

            final boolean include = true;

            if (longStreamId != null && longEventId != null && include) {
                List<Event> events = storedDataMap.get(longStreamId);
                if (events == null) {
                    events = new ArrayList<Event>();
                    storedDataMap.put(longStreamId, events);
                }
                events.add(new Event(longEventId, storedData));
            }
        }

        // Filter the streams by ones that should be visible to the current
        // user.
        final HashMap<Long, List<Event>> filteredDataMap = new HashMap<Long, List<Event>>();
        for (final Entry<Long, List<Event>> entry : storedDataMap.entrySet()) {
            final Long streamId = entry.getKey();
            Stream stream = null;

            stream = getStreamById(streamId);

            // If the stream's id is undefined then it is a dummy we either
            // couldn't find it or are not allowed to use it.
            if (stream.isPersistent()) {
                filteredDataMap.put(stream.getId(), entry.getValue());
            }
        }

        return filteredDataMap;
    }

    private Stream getStreamById(final long streamId) {
        // Create a map to cache stream lookups. If we have cached more than a
        // million streams then discard
        // the map and start again to avoid using too much memory.
        if (fiteredStreamCache == null || fiteredStreamCache.size() > 1000000) {
            fiteredStreamCache = new HashMap<>();
        }

        Stream stream = fiteredStreamCache.get(streamId);
        if (stream == null) {
            try {
                // Make sure we are allowed to see this stream. If we aren't
                // then set the stream to a dummy stream
                // just to put an item in the map.

                // See if we can load the stream. We might get a StreamPermissionException if we aren't allowed to read from this stream.
                stream = streamStore.loadStreamById(streamId);
            } catch (final StreamPermissionException e) {
                stream = new Stream();
                LOGGER.debug(e.getMessage(), e);
            } catch (final RuntimeException e) {
                stream = new Stream();
                LOGGER.error(e.getMessage(), e);
            }

            // Create a dummy stream and cache it if we can't get a stream for
            // the stream id so that we don't
            // keep trying to get a stream for the id.
            if (stream == null) {
                stream = new Stream();
            }

            fiteredStreamCache.put(streamId, stream);
        }

        return stream;
    }

    private Long getLong(final String[] storedData, final int index) {
        try {
            if (index >= 0 && storedData.length > index) {
                final String value = storedData[index];
                return Long.parseLong(value);
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
