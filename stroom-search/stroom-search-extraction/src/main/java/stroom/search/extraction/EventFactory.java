/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.index.shared.IndexConstants;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

class EventFactory {

    private final int streamIdIndex;
    private final int eventIdIndex;

    private ExtractionException error;

    EventFactory(final FieldIndex fieldIndex) {
        // First get the index in the stored data of the stream and event id fields.
        streamIdIndex = getFieldIndex(fieldIndex, IndexConstants.STREAM_ID);
        eventIdIndex = getFieldIndex(fieldIndex, IndexConstants.EVENT_ID);
    }

    private int getFieldIndex(final FieldIndex fieldIndex, final String fieldName) {
        int index = -1;

        final Integer pos = fieldIndex.getPos(fieldName);
        if (pos == null) {
            if (error == null) {
                error = new ExtractionException("The " + fieldName + " has not been stored in this index");
            }
        } else {
            index = pos;
        }

        return index;
    }

    Event create(final Val[] storedData) {
        if (error != null) {
            throw error;
        }

        final long longStreamId = getLong(storedData, streamIdIndex);
        if (longStreamId == -1) {
            throw new ExtractionException("No stream id supplied");
        }
        final long longEventId = getLong(storedData, eventIdIndex);
        if (longEventId == -1) {
            throw new ExtractionException("No event id supplied");
        }

        return new Event(longStreamId, longEventId, storedData);
    }

    private long getLong(final Val[] storedData, final int index) {
        try {
            if (index >= 0 && storedData.length > index) {
                final Val value = storedData[index];
                return value.toLong();
            }
        } catch (final Exception e) {
            // Ignore
        }

        return -1;
    }
}
