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

package stroom.query.language.functions;

import stroom.util.NullSafe;
import stroom.util.shared.query.FieldNames;
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

    private final Map<CIKey, Integer> fieldToPos = new ConcurrentHashMap<>();

    private final List<CIKey> fieldList = new ArrayList<>();

    private Integer timeFieldIndex;
    private Integer streamIdFieldIndex;
    private Integer eventIdFieldIndex;

    public int create(final String fieldName) {
        final CIKey caseInsensitiveFieldName = CIKey.of(fieldName);
        return fieldToPos.computeIfAbsent(caseInsensitiveFieldName, k ->
                addField(caseInsensitiveFieldName));
    }

    public int create(final CIKey caseInsensitiveFieldName) {
        return fieldToPos.computeIfAbsent(caseInsensitiveFieldName, k ->
                addField(caseInsensitiveFieldName));
    }

    private synchronized int addField(final CIKey fieldName) {
        fieldList.add(fieldName);
        return fieldList.size() - 1;
    }

    public Integer getPos(final String fieldName) {
        return fieldToPos.get(CIKey.of(fieldName));
    }

    public Integer getPos(final CIKey caseInsensitiveFieldName) {
        return fieldToPos.get(caseInsensitiveFieldName);
    }

    public String getField(final int pos) {
        return NullSafe.get(getFieldAsCIKey(pos), CIKey::get);
    }

    public CIKey getFieldAsCIKey(final int pos) {
//        final CIKey[] arr = posToField;
        if (pos >= 0 && pos < fieldList.size()) {
            return fieldList.get(pos);
        }
        return null;
    }

    public List<String> getFields() {
        return fieldList.stream()
                .map(CIKey::get)
                .toList();
    }

    public List<CIKey> getFieldsAsCIKeys() {
        return fieldList;
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
            final int idx =
                    Optional.ofNullable(getPos(FieldNames.DEFAULT_TIME_FIELD_KEY))
                            .or(() -> Optional.ofNullable(getPos(FieldNames.FALLBACK_TIME_FIELD_KEY)))
                            .orElse(-1);
            timeFieldIndex = idx;
            return idx;
        }
        return timeFieldIndex;
    }

    public int getStreamIdFieldIndex() {
        if (streamIdFieldIndex == null) {
            final int idx =
                    Optional.ofNullable(getPos(FieldNames.DEFAULT_STREAM_ID_FIELD_KEY))
                            .or(() -> Optional.ofNullable(
                                    getPos(FieldNames.FALLBACK_STREAM_ID_FIELD_KEY)))
                            .or(() -> {
                                create(FieldNames.FALLBACK_STREAM_ID_FIELD_KEY);
                                return Optional.ofNullable(
                                        getPos(FieldNames.FALLBACK_STREAM_ID_FIELD_KEY));
                            })
                            .orElse(-1);
            this.streamIdFieldIndex = idx;
            return idx;
        }
        return streamIdFieldIndex;
    }

    public int getEventIdFieldIndex() {
        if (eventIdFieldIndex == null) {
            final int idx =
                    Optional.ofNullable(getPos(FieldNames.DEFAULT_EVENT_ID_FIELD_KEY))
                            .or(() -> Optional.ofNullable(
                                    getPos(FieldNames.FALLBACK_EVENT_ID_FIELD_KEY)))
                            .or(() -> {
                                create(FieldNames.FALLBACK_EVENT_ID_FIELD_KEY);
                                return Optional.ofNullable(
                                        getPos(FieldNames.FALLBACK_EVENT_ID_FIELD_KEY));
                            }).orElse(-1);
            eventIdFieldIndex = idx;
            return idx;
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
