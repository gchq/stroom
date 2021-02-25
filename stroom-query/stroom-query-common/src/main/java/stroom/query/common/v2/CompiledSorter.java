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

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class CompiledSorter<E extends HasGenerators> implements Comparator<E>, Function<Stream<E>, Stream<E>> {

    private final List<CompiledSort> compiledSorts = new ArrayList<>();

    private CompiledSorter() {
    }

    @SuppressWarnings("unchecked")
    public static <E extends HasGenerators> CompiledSorter<E>[] create(final int maxDepth,
                                                                       final CompiledField[] compiledFields) {
        final CompiledSorter<E>[] sorters = new CompiledSorter[maxDepth + 1];

        if (compiledFields != null) {
            for (int depth = 0; depth <= maxDepth; depth++) {
                for (int fieldIndex = 0; fieldIndex < compiledFields.length; fieldIndex++) {
                    final CompiledField compiledField = compiledFields[fieldIndex];
                    final Field field = compiledField.getField();
                    if (field.getSort() != null && (field.getGroup() == null || field.getGroup() >= depth)) {
                        // Get an appropriate comparator.
                        final Comparator<Val> comparator = ComparatorFactory.create(field);

                        // Remember sorting info.
                        final Sort sort = field.getSort();
                        final CompiledSort compiledSort = new CompiledSort(fieldIndex, sort, comparator);

                        CompiledSorter<E> sorter = sorters[depth];
                        if (sorter == null) {
                            sorter = new CompiledSorter<>();
                            sorters[depth] = sorter;
                        }

                        sorter.add(compiledSort);
                    }
                }
            }
        }

        return sorters;
    }

    public void add(final CompiledSort compiledSort) {
        // Add into list in sort order.
        int index = 0;
        for (final CompiledSort s : compiledSorts) {
            if (s.getOrder() > compiledSort.getOrder()) {
                break;
            }
            index++;
        }

        if (index >= compiledSorts.size()) {
            compiledSorts.add(compiledSort);
        } else {
            compiledSorts.add(index, compiledSort);
        }
    }

    @Override
    public Stream<E> apply(final Stream<E> stream) {
        return stream.sorted(this);
    }

    @Override
    public int compare(final E o1, final E o2) {
        final Generator[] generators1 = o1.getGenerators();
        final Generator[] generators2 = o2.getGenerators();
        for (final CompiledSort compiledSort : compiledSorts) {
            final int fieldPos = compiledSort.getFieldIndex();
            final Generator g1 = generators1[fieldPos];
            final Generator g2 = generators2[fieldPos];

            int res = 0;
            if (g1 != null && g2 != null) {
                final Val v1 = g1.eval();
                final Val v2 = g2.eval();
                res = compiledSort.getComparator().compare(v1, v2);
            } else if (g1 != null) {
                res = 1;
            } else if (g2 != null) {
                res = -1;
            }

            // If we already have a difference then return it rather than comparing all values.
            if (res != 0) {
                // Flip the compare direction if necessary.
                if (SortDirection.DESCENDING.equals(compiledSort.getDirection())) {
                    res = res * -1;
                }

                return res;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "CompiledSorter{" +
                "compiledSorts=" + compiledSorts +
                '}';
    }
}
