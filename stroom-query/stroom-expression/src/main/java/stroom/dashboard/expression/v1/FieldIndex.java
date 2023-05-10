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

package stroom.dashboard.expression.v1;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class FieldIndex {

    public static final String DEFAULT_TIME_FIELD_NAME = "__time__";
    public static final String FALLBACK_TIME_FIELD_NAME = "EventTime";
    private static final String DEFAULT_STREAM_ID_FIELD_NAME = "__stream_id__";
    private static final String DEFAULT_EVENT_ID_FIELD_NAME = "__event_id__";
    private static final String FALLBACK_STREAM_ID_FIELD_NAME = "StreamId";
    private static final String FALLBACK_EVENT_ID_FIELD_NAME = "EventId";

    private final Map<String, Integer> fieldToPos = new HashMap<>();
    private final Map<Integer, String> posToField = new TreeMap<>();
    private int index;

    private int windowTimeFieldPos = -2;
    private int streamIdFieldIndex = -2;
    private int eventIdFieldIndex = -2;

    public static FieldIndex forFields(final String... fieldNames) {
        final FieldIndex instance = new FieldIndex();
        Arrays.stream(fieldNames).forEach(instance::create);
        return instance;
    }

    public int create(final String fieldName) {
        return fieldToPos.computeIfAbsent(fieldName, k -> {
            final int pos = index++;
            posToField.put(pos, k);
            return pos;
        });
    }

    public Integer getPos(final String fieldName) {
        return fieldToPos.get(fieldName);
    }

    public String getField(final int pos) {
        return posToField.get(pos);
    }

    public int size() {
        return fieldToPos.size();
    }

    public Set<String> getFieldNames() {
        return fieldToPos.keySet();
    }

    public Stream<Entry<String, Integer>> stream() {
        return fieldToPos.entrySet().stream();
    }

    public void forEach(final BiConsumer<Integer, String> consumer) {
        posToField.forEach(consumer);
    }

    public int getWindowTimeFieldPos() {
        if (windowTimeFieldPos == -2) {
            windowTimeFieldPos = Optional.ofNullable(getPos(DEFAULT_TIME_FIELD_NAME))
                    .or(() -> Optional.ofNullable(getPos(FALLBACK_TIME_FIELD_NAME)))
                    .orElseThrow(() ->
                            new RuntimeException("Cannot apply window when there is no time field"));
        }
        return windowTimeFieldPos;
    }


    public int getStreamIdFieldIndex() {
        if (streamIdFieldIndex == -2) {
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

    public int getEventIdFieldIndex() {
        if (eventIdFieldIndex == -2) {
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
