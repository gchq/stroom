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
import stroom.dashboard.expression.v1.ValComparator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CompiledSorter implements Serializable, Comparator<Item> {
    private static final long serialVersionUID = -64195891930546352L;

    private static final ValComparator COMPARATOR = new ValComparator();

    private final List<CompiledSort> compiledSorts = new ArrayList<>();
    private final boolean hasSort;

    public CompiledSorter(final List<Field> fields) {
        int pos = 0;

        if (fields != null) {
            for (final Field field : fields) {
                if (field.getSort() != null) {
                    // Remember sorting info.
                    final Sort sort = field.getSort();
                    final CompiledSort compiledSort = new CompiledSort(pos, sort);
                    add(compiledSort);
                }

                pos++;
            }
        }

        hasSort = compiledSorts.size() > 0;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public int compare(final Item o1, final Item o2) {
        for (final CompiledSort compiledSort : compiledSorts) {
            final int fieldPos = compiledSort.getFieldIndex();
            final Generator g1 = o1.generators[fieldPos];
            final Generator g2 = o2.generators[fieldPos];

            int res = 0;
            if (g1 != null && g2 != null) {
                final Val v1 = g1.eval();
                final Val v2 = g2.eval();
                res = COMPARATOR.compare(v1, v2);
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

    boolean hasSort() {
        return hasSort;
    }

    @Override
    public String toString() {
        return "CompiledSorter{" +
                "compiledSorts=" + compiledSorts +
                ", hasSort=" + hasSort +
                '}';
    }
}
