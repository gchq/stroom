/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.statistics.impl.sql.client.presenter;

import stroom.statistics.impl.sql.shared.CustomRollUpMask.IntegerListComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class State {

    private final List<Field> fields = new ArrayList<>();
    private final List<Mask> masks = new ArrayList<>();

    private int fieldId;
    private int maskId;

    public List<Field> getFields() {
        return fields;
    }

    public Field createField(final String name) {
        return new Field(fieldId++, name);
    }

    public void addField(final Field field) {
        fields.add(field);
    }

    public void removeFields(final Collection<Field> list) {
        fields.removeAll(list);
    }

    public void sortFields() {
        fields.sort(Field::compareTo);
    }

    public List<Mask> getMasks() {
        return masks;
    }

    public boolean addMask(final Set<Field> mask) {
        final Mask holder = new Mask(maskId++, mask);
        return masks.add(holder);
    }

    public void addNoRollUpPerm() {
        // add a line with no rollups as a starting point
        if (masks.size() == 0) {
            addMask(new HashSet<>());
        }
    }

    public void removeMasks(final Collection<Mask> list) {
        masks.removeAll(list);
    }

    public void clear() {
        fields.clear();
        masks.clear();
    }

    public Set<List<Integer>> getIntegerMaskSet() {
        return masks
                .stream()
                .map(mask -> toMask(mask.getMask()))
                .collect(Collectors.toSet());
    }

    private List<Integer> toMask(final Set<Field> statisticFields) {
        return statisticFields
                .stream()
                .map(fields::indexOf)
                .sorted()
                .collect(Collectors.toList());
    }

    public void allPermutations() {
        // Create a list of true index permutations.
        final List<List<Integer>> list = createPermutations();

        // Ensure consistent sort order.
        list.sort(IntegerListComparator.INSTANCE);

        // Convert to masks.
        masks.clear();
        list.forEach(trueIndices -> {
            final Set<Field> fieldSet = trueIndices
                    .stream()
                    .map(fields::get)
                    .collect(Collectors.toSet());
            final Mask holder = new Mask(maskId++, fieldSet);
            masks.add(holder);
        });
    }

    private List<List<Integer>> createPermutations() {
        final int size = fields.size();
        final int count = 1 << size;
        final List<List<Integer>> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final List<Integer> trueIndices = new ArrayList<>();
            for (int bit = 0; bit < size; bit++) {
                if ((i & (1 << bit)) != 0) {
                    trueIndices.add(bit);
                }
            }
            list.add(trueIndices);
        }
        return list;
    }

    public static class Field implements Comparable<Field> {

        private final int id;
        private String name;

        public Field(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        public Field(final int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Field field = (Field) o;
            return id == field.id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public int compareTo(final Field o) {
            return name.compareTo(o.name);
        }
    }

    public static class Mask {

        private final int id;
        private final Set<Field> mask;

        public Mask(final int id, final Set<Field> mask) {
            this.id = id;
            this.mask = mask;
        }

        public Set<Field> getMask() {
            return mask;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Mask that = (Mask) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}
