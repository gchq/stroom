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
    //    private final boolean autoCreate;
    private int index;

//    public FieldIndexMap() {
//        this(false);
//    }
//
//    public FieldIndexMap(final boolean autoCreate) {
//        this.autoCreate = autoCreate;
//    }

    public static FieldIndex forFields(final String... fieldNames) {
        final FieldIndex instance = new FieldIndex();
        Arrays.stream(fieldNames).forEach(instance::create);
        return instance;
    }

//    public void add(final FieldIndexMap fieldIndexMap) {
//        if (fieldToPos.size() == 0) {
//            fieldIndexMap.getMap().forEach((k, v) -> {
//                fieldToPos.put(k, v);
//                index = Math.max(index, v + 1);
//            });
//        } else {
//            fieldIndexMap.getMap().keySet().forEach(fieldName -> create(fieldName, true));
//        }
//    }

    public int create(final String fieldName) {
        return fieldToPos.computeIfAbsent(fieldName, k -> {
            final int pos = index++;
            posToField.put(pos, k);
            return pos;
        });
//        }
//
//        Integer currentIndex = fieldToPos.get(fieldName);
//        if (currentIndex == null) {
//            return -1;
//        }
//        return currentIndex;
    }

    public Integer getPos(final String fieldName) {
        return fieldToPos.get(fieldName);
//        if (currentIndex == null) {
//            return -1;
//        }
//        return currentIndex;
    }

    public String getField(final int pos) {
        return posToField.get(pos);
    }

    public int size() {
        return fieldToPos.size();
    }

//    /**
//     * @return An unmodifiable view of the underlying field to position map
//     */
//    public Map<String, Integer> getMap() {
//        return Collections.unmodifiableMap(fieldToPos);
//    }
//
//    @Override
//    public Iterator<String> iterator() {
//        return fieldToPos.keySet().iterator();
//    }

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
