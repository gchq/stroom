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

package stroom.util.shared;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public final class CompareUtil {
    private CompareUtil() {
    }

    public static int compareLong(final Long l1, final Long l2) {
        if (l1 == null && l2 == null) {
            return 0;
        }
        if (l1 == null) {
            return -1;
        }
        if (l2 == null) {
            return +1;
        }
        return l1.compareTo(l2);
    }

    public static int compareInteger(final Integer l1, final Integer l2) {
        if (l1 == null && l2 == null) {
            return 0;
        }
        if (l1 == null) {
            return -1;
        }
        if (l2 == null) {
            return +1;
        }
        return l1.compareTo(l2);
    }

    public static int compareBoolean(final Boolean l1, final Boolean l2) {
        if (l1 == null && l2 == null) {
            return 0;
        }
        if (l1 == null) {
            return -1;
        }
        if (l2 == null) {
            return +1;
        }
        return l1.compareTo(l2);
    }

    public static int compareString(final String l1, final String l2) {
        if (l1 == null && l2 == null) {
            return 0;
        }
        if (l1 == null) {
            return -1;
        }
        if (l2 == null) {
            return +1;
        }
        return l1.compareToIgnoreCase(l2);
    }

    /**
     * Convert a BaseCriteria into a Comparator
     *
     * e.g. of fieldComparatorsMap
     *
     * <pre>
     * private static final Map<String, Comparator<DBTableStatus>> FIELD_COMPARATORS = Map.of(
     *   DBTableStatus.FIELD_DATABASE, Comparator.comparing(
     *     DBTableStatus::getDb,
     *     String::compareToIgnoreCase),
     *   DBTableStatus.FIELD_TABLE, Comparator.comparing(
     *     DBTableStatus::getTable,
     *     String::compareToIgnoreCase),
     *   DBTableStatus.FIELD_ROW_COUNT, Comparator.comparing(DBTableStatus::getCount),
     *   DBTableStatus.FIELD_DATA_SIZE, Comparator.comparing(DBTableStatus::getDataSize),
     *   DBTableStatus.FIELD_INDEX_SIZE, Comparator.comparing(DBTableStatus::getIndexSize));
     * </pre>
     */
    public static <T> Comparator<T> buildCriteriaComparator(
        final Map<String, Comparator<T>> fieldComparatorsMap,
        final BaseCriteria criteria) {

        Objects.requireNonNull(fieldComparatorsMap);
        Objects.requireNonNull(criteria);
        Objects.requireNonNull(criteria.getSortList());

        Comparator<T> comparator = Comparator.comparingInt(dbTableStatus -> 1);

        for (final Sort sort : criteria.getSortList()) {
            final String field = sort.getField();

            Comparator<T> fieldComparator = fieldComparatorsMap.get(field);

            Objects.requireNonNull(fieldComparator,() ->
                "Missing comparator for field " + field);

            if (sort.getDirection().equals(Sort.Direction.DESCENDING)) {
                fieldComparator = fieldComparator.reversed();
            }

            comparator = comparator.thenComparing(fieldComparator);
        }
        return comparator;
    }
}
