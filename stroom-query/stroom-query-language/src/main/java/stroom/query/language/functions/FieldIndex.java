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

package stroom.query.language.functions;

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
    public static final String DEFAULT_STREAM_ID_FIELD_NAME = "__stream_id__";
    public static final String DEFAULT_EVENT_ID_FIELD_NAME = "__event_id__";
    public static final String FALLBACK_TIME_FIELD_NAME = "EventTime";
    public static final String FALLBACK_STREAM_ID_FIELD_NAME = "StreamId";
    public static final String FALLBACK_EVENT_ID_FIELD_NAME = "EventId";

    private final Map<String, Integer> fieldToPos = new ConcurrentHashMap<>();

    private final List<String> fieldList = new ArrayList<>();
    private volatile String[] posToField = new String[0];

    private Integer timeFieldIndex;
    private Integer streamIdFieldIndex;
    private Integer eventIdFieldIndex;

    public int create(final String fieldName) {
        return fieldToPos.computeIfAbsent(fieldName, k -> addField(fieldName));
    }

    private synchronized int addField(final String fieldName) {
        fieldList.add(fieldName);
        posToField = fieldList.toArray(new String[0]);
        return posToField.length - 1;
    }

    public Integer getPos(final String fieldName) {
        return fieldToPos.get(fieldName);
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
        return fieldToPos.entrySet().stream();
    }

    public int getTimeFieldIndex() {
        if (timeFieldIndex == null) {
            timeFieldIndex =
                    Optional.ofNullable(getPos(DEFAULT_TIME_FIELD_NAME))
                            .or(() -> Optional.ofNullable(getPos(FALLBACK_TIME_FIELD_NAME)))
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
                    Optional.ofNullable(getPos(DEFAULT_STREAM_ID_FIELD_NAME))
                            .or(() -> Optional.ofNullable(
                                    getPos(FALLBACK_STREAM_ID_FIELD_NAME)))
                            .or(() -> {
                                create(FALLBACK_STREAM_ID_FIELD_NAME);
                                return Optional.ofNullable(
                                        getPos(FALLBACK_STREAM_ID_FIELD_NAME));
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
                    Optional.ofNullable(getPos(DEFAULT_EVENT_ID_FIELD_NAME))
                            .or(() -> Optional.ofNullable(
                                    getPos(FALLBACK_EVENT_ID_FIELD_NAME)))
                            .or(() -> {
                                create(FALLBACK_EVENT_ID_FIELD_NAME);
                                return Optional.ofNullable(
                                        getPos(FALLBACK_EVENT_ID_FIELD_NAME));
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
