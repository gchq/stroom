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

package stroom.query.language.functions;

import stroom.util.NullSafe;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class FieldIndex {

    public static final String DEFAULT_TIME_FIELD_NAME = "__time__";
    public static final String FALLBACK_TIME_FIELD_NAME = "EventTime";
    public static final String DEFAULT_STREAM_ID_FIELD_NAME = "__stream_id__";
    public static final String DEFAULT_EVENT_ID_FIELD_NAME = "__event_id__";
    public static final String FALLBACK_STREAM_ID_FIELD_NAME = "StreamId";
    public static final String FALLBACK_EVENT_ID_FIELD_NAME = "EventId";

    public static final CIKey DEFAULT_TIME_FIELD_KEY = CIKey.of(DEFAULT_TIME_FIELD_NAME);
    public static final CIKey FALLBACK_TIME_FIELD_KEY = CIKey.of(FALLBACK_TIME_FIELD_NAME);
    public static final CIKey DEFAULT_STREAM_ID_FIELD_KEY = CIKey.of(
            DEFAULT_STREAM_ID_FIELD_NAME);
    public static final CIKey DEFAULT_EVENT_ID_FIELD_KEY = CIKey.of(
            DEFAULT_EVENT_ID_FIELD_NAME);
    public static final CIKey FALLBACK_STREAM_ID_FIELD_KEY = CIKey.of(
            FALLBACK_STREAM_ID_FIELD_NAME);
    public static final CIKey FALLBACK_EVENT_ID_FIELD_KEY = CIKey.of(
            FALLBACK_EVENT_ID_FIELD_NAME);

    private final Map<CIKey, Integer> fieldToPos = new ConcurrentHashMap<>();

    private final List<String> fieldList = new ArrayList<>();
    private volatile String[] posToField = new String[0];

    private Integer timeFieldIndex;
    private Integer streamIdFieldIndex;
    private Integer eventIdFieldIndex;

    public int create(final String fieldName) {
        return fieldToPos.computeIfAbsent(CIKey.of(fieldName), k ->
                addField(fieldName));
    }

    private int create(final CIKey caseInsensitiveFieldName) {
        return fieldToPos.computeIfAbsent(caseInsensitiveFieldName, k ->
                addField(caseInsensitiveFieldName.get()));
    }

    private synchronized int addField(final String fieldName) {
        fieldList.add(fieldName);
        posToField = fieldList.toArray(new String[0]);
        return posToField.length - 1;
    }

    public Integer getPos(final String fieldName) {
        return fieldToPos.get(CIKey.of(fieldName));
    }

    private Integer getPos(final CIKey caseInsensitiveFieldName) {
        return fieldToPos.get(caseInsensitiveFieldName);
    }

    public String getField(final int pos) {
        final String[] arr = posToField;
        if (pos >= 0 && pos < arr.length) {
            return arr[pos];
        }
        return null;
    }

    public String[] getFields() {
        return posToField;
    }

    public int size() {
        return fieldToPos.size();
    }

    public Stream<Entry<String, Integer>> stream() {
        return fieldToPos.entrySet()
                .stream()
                .map(entry -> Map.entry(
                        NullSafe.get(entry.getKey(), CIKey::get),
                        entry.getValue()));
    }

    public int getWindowTimeFieldIndex() {
        final int index = getTimeFieldIndex();
        if (index == -1) {
            throw new RuntimeException("Cannot apply window when there is no time field");
        }
        return index;
    }

    public int getTimeFieldIndex() {
        if (timeFieldIndex == null) {
            timeFieldIndex =
                    Optional.ofNullable(getPos(DEFAULT_TIME_FIELD_KEY))
                            .or(() -> Optional.ofNullable(getPos(FALLBACK_TIME_FIELD_KEY)))
                            .orElse(-1);
        }
        return timeFieldIndex;
    }

    /**
     * @return True if fieldName matches the special Stream ID field.
     */
    public static boolean isStreamIdFieldName(final String fieldName) {
        return Objects.equals(DEFAULT_STREAM_ID_FIELD_NAME, fieldName)
                || Objects.equals(FALLBACK_STREAM_ID_FIELD_NAME, fieldName);
    }

    public int getStreamIdFieldIndex() {
        if (streamIdFieldIndex == null) {
            streamIdFieldIndex =
                    Optional.ofNullable(getPos(DEFAULT_STREAM_ID_FIELD_KEY))
                            .or(() -> Optional.ofNullable(
                                    getPos(FALLBACK_STREAM_ID_FIELD_KEY)))
                            .or(() -> {
                                create(FALLBACK_STREAM_ID_FIELD_KEY);
                                return Optional.ofNullable(
                                        getPos(FALLBACK_STREAM_ID_FIELD_KEY));
                            })
                            .orElse(-1);
        }
        return streamIdFieldIndex;
    }

    /**
     * @return True if fieldName matches the special Event ID field.
     */
    public static boolean isEventIdFieldName(final String fieldName) {
        return Objects.equals(DEFAULT_EVENT_ID_FIELD_NAME, fieldName)
                || Objects.equals(FALLBACK_EVENT_ID_FIELD_NAME, fieldName);
    }

    public int getEventIdFieldIndex() {
        if (eventIdFieldIndex == null) {
            eventIdFieldIndex =
                    Optional.ofNullable(getPos(DEFAULT_EVENT_ID_FIELD_KEY))
                            .or(() -> Optional.ofNullable(
                                    getPos(FALLBACK_EVENT_ID_FIELD_KEY)))
                            .or(() -> {
                                create(FALLBACK_EVENT_ID_FIELD_KEY);
                                return Optional.ofNullable(
                                        getPos(FALLBACK_EVENT_ID_FIELD_KEY));
                            }).orElse(-1);
        }
        return eventIdFieldIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FieldIndex that = (FieldIndex) o;
        return fieldToPos.equals(that.fieldToPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldToPos);
    }
}
