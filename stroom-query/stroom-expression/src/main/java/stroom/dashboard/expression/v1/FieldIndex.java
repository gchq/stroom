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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class FieldIndex {
    private final Map<String, Integer> fieldToPos = new HashMap<>();
    private final Map<Integer, String> posToField = new HashMap<>();
    private int index;

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

    public void forEach(final BiConsumer<String, Integer> consumer) {
        fieldToPos.forEach(consumer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FieldIndex that = (FieldIndex) o;
        return fieldToPos.equals(that.fieldToPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldToPos);
    }
}
