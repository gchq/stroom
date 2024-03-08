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

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.language.functions.Val;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class CompiledSorter<E extends Item> implements Comparator<E>, Function<Stream<E>, Stream<E>> {

    private final List<CompiledSort> compiledSorts = new ArrayList<>();

    private CompiledSorter() {
    }

    public List<CompiledSort> getCompiledSorts() {
        return compiledSorts;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Item> CompiledSorter<E>[] create(final int maxDepth,
                                                              final CompiledColumn[] compiledColumns) {
        final CompiledSorter<E>[] sorters = new CompiledSorter[maxDepth + 1];

        if (compiledColumns != null) {
            for (int depth = 0; depth <= maxDepth; depth++) {
                for (int columnIndex = 0; columnIndex < compiledColumns.length; columnIndex++) {
                    final CompiledColumn compiledColumn = compiledColumns[columnIndex];
                    final Column column = compiledColumn.getColumn();
                    if (column.getSort() != null && (column.getGroup() == null || column.getGroup() >= depth)) {
                        // Get an appropriate comparator.
                        final Comparator<Val> comparator = ComparatorFactory.create(compiledColumn);

                        // Remember sorting info.
                        final Sort sort = column.getSort();
                        final CompiledSort compiledSort = new CompiledSort(columnIndex, sort, comparator);

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
        for (final CompiledSort compiledSort : compiledSorts) {
            final int fieldPos = compiledSort.getFieldIndex();

            final Val v1 = o1.getValue(fieldPos);
            final Val v2 = o2.getValue(fieldPos);

            int res = 0;
            if (v1 != null && v2 != null) {
                res = compiledSort.getComparator().compare(v1, v2);
            } else if (v1 != null) {
                res = 1;
            } else if (v2 != null) {
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
