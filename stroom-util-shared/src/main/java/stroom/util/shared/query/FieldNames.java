/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared.query;

import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;

import java.util.Map;
import java.util.Objects;

public class FieldNames {

    public static final String DEFAULT_TIME_FIELD_NAME = "__time__";
    /**
     * For field '{@code __time__}'
     */
    public static final CIKey DEFAULT_TIME_FIELD_KEY = CIKey.of(DEFAULT_TIME_FIELD_NAME);

    public static final String FALLBACK_TIME_FIELD_NAME = "EventTime";
    /**
     * For field '{@code EventTime}'
     */
    public static final CIKey FALLBACK_TIME_FIELD_KEY = CIKey.of(FALLBACK_TIME_FIELD_NAME);

    public static final String DEFAULT_STREAM_ID_FIELD_NAME = "__stream_id__";
    /**
     * For field '{@code __stream_id__}'
     */
    public static final CIKey DEFAULT_STREAM_ID_FIELD_KEY = CIKey.of(
            DEFAULT_STREAM_ID_FIELD_NAME);

    public static final String FALLBACK_STREAM_ID_FIELD_NAME = "StreamId";
    /**
     * For field '{@code StreamId}'
     */
    public static final CIKey FALLBACK_STREAM_ID_FIELD_KEY = CIKey.of(
            FALLBACK_STREAM_ID_FIELD_NAME);

    public static final String DEFAULT_EVENT_ID_FIELD_NAME = "__event_id__";
    /**
     * For field '{@code __event_id__}'
     */
    public static final CIKey DEFAULT_EVENT_ID_FIELD_KEY = CIKey.of(
            DEFAULT_EVENT_ID_FIELD_NAME);

    public static final String FALLBACK_EVENT_ID_FIELD_NAME = "EventId";
    /**
     * For field '{@code EventId}'
     */
    public static final CIKey FALLBACK_EVENT_ID_FIELD_KEY = CIKey.of(
            FALLBACK_EVENT_ID_FIELD_NAME);

    private static final Map<String, CIKey> STANDARD_FIELD_NAMES_MAP = Map.of(
            DEFAULT_TIME_FIELD_NAME, DEFAULT_TIME_FIELD_KEY,
            FALLBACK_TIME_FIELD_NAME, FALLBACK_TIME_FIELD_KEY,
            DEFAULT_STREAM_ID_FIELD_NAME, DEFAULT_STREAM_ID_FIELD_KEY,
            FALLBACK_STREAM_ID_FIELD_NAME, FALLBACK_STREAM_ID_FIELD_KEY,
            DEFAULT_EVENT_ID_FIELD_NAME, DEFAULT_EVENT_ID_FIELD_KEY,
            FALLBACK_EVENT_ID_FIELD_NAME, FALLBACK_EVENT_ID_FIELD_KEY,
            "", CIKey.EMPTY_STRING);

    private FieldNames() {
        // Static stuff only
    }

    /**
     * Create a case-insensitive cache key for the passed fieldName
     */
    public static CIKey createCIKey(final String fieldName) {
        // Re-use the pre-built CIKeys for standard fieldNames if we can
        if (fieldName == null) {
            return CIKey.NULL_STRING;
        } else {
            return GwtNullSafe.requireNonNullElseGet(
                    STANDARD_FIELD_NAMES_MAP.get(fieldName),
                    () -> CIKey.of(fieldName));
        }
    }

    /**
     * @return True if fieldName matches the special Stream ID field.
     */
    public static boolean isStreamIdFieldName(final String fieldName) {
        return Objects.equals(DEFAULT_STREAM_ID_FIELD_NAME, fieldName)
                || Objects.equals(FALLBACK_STREAM_ID_FIELD_NAME, fieldName);
    }

    /**
     * @return True if fieldName matches the special Event ID field.
     */
    public static boolean isEventIdFieldName(final String fieldName) {
        return Objects.equals(DEFAULT_EVENT_ID_FIELD_NAME, fieldName)
                || Objects.equals(FALLBACK_EVENT_ID_FIELD_NAME, fieldName);
    }
}
