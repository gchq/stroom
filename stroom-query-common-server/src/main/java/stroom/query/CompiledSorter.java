/*
 * Copyright 2016 Crown Copyright
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

package stroom.query;

import stroom.query.shared.Field;
import stroom.query.shared.Sort;
import stroom.query.shared.Sort.SortDirection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CompiledSorter implements Serializable, Comparator<Item> {
    private static final long serialVersionUID = -64195891930546352L;

    private final List<CompiledSort> compiledSorts = new ArrayList<CompiledSort>();
    private final boolean hasSort;

    public CompiledSorter(final List<Field> fields) {
        int pos = 0;
        for (final Field field : fields) {
            if (field.getSort() != null) {
                // Remember sorting info.
                final Sort sort = field.getSort();
                final CompiledSort compiledSort = new CompiledSort(pos, sort);
                add(compiledSort);
            }

            pos++;
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public int compare(final Item o1, final Item o2) {
        for (final CompiledSort compiledSort : compiledSorts) {
            final int fieldPos = compiledSort.getFieldIndex();

            Comparable v1 = null;
            Comparable v2 = null;

            if (SortDirection.ASCENDING.equals(compiledSort.getDirection())) {
                v1 = (Comparable) o1.values[fieldPos];
                v2 = (Comparable) o2.values[fieldPos];
            } else {
                v2 = (Comparable) o1.values[fieldPos];
                v1 = (Comparable) o2.values[fieldPos];
            }

            if (v1 != null && v2 != null) {
                final int res = v1.compareTo(v2);

                // If there is a difference then return the
                // difference straight away.
                if (res != 0) {
                    return res;
                }
            } else if (v1 != null) {
                return 1;
            } else if (v2 != null) {
                return -1;
            }
        }
        return 0;
    }

    public boolean hasSort() {
        return hasSort;
    }
}
