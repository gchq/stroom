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
import java.util.function.Function;

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

    public static int compareDouble(final Double val1, final Double val2) {
        if (val1 == null && val2 == null) {
            return 0;
        }
        if (val1 == null) {
            return -1;
        }
        if (val2 == null) {
            return +1;
        }
        return val1.compareTo(val2);
    }

    /**
     * Convert a BaseCriteria into a Comparator
     * <p>
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
            final BaseCriteria criteria,
            final String... defaultSortFields) {

        Objects.requireNonNull(fieldComparatorsMap);
        Objects.requireNonNull(criteria);

        Comparator<T> comparator = null;

        if (GwtNullSafe.hasItems(criteria.getSortList())) {
            for (final CriteriaFieldSort sort : criteria.getSortList()) {
                final String field = sort.getId();

                // TODO we are ignoring sort.isIgnoreCase() so are comparing with whatever
                //  comparators are in the map. Might be be better to have a Map<String, ComparatorPair<T>>
                //  such that the pair contains both case sesnse and insense versions or for non-strings
                //  just one comparator.
                Comparator<T> fieldComparator = fieldComparatorsMap.get(field);

                Objects.requireNonNull(fieldComparator, () ->
                        "Missing comparator for field " + field);

                fieldComparator = CompareUtil.reverseIf(fieldComparator, sort.isDesc());

                comparator = CompareUtil.combine(comparator, fieldComparator);
            }
        } else {
            for (final String defaultField : defaultSortFields) {
                final Comparator<T> fieldComparator = fieldComparatorsMap.get(defaultField);

                Objects.requireNonNull(fieldComparator, () ->
                        "Missing comparator for field " + defaultField);

                comparator = CompareUtil.combine(comparator, fieldComparator);
            }
        }
        return CompareUtil.nonNull(comparator);
    }

    /**
     * Creates a null safe case-insensitive comparator that puts nulls first.
     */
    public static <T> Comparator<T> getNullSafeCaseInsensitiveComparator(final Function<T, String> extractor) {
        return Comparator.nullsFirst(
                Comparator.comparing(
                        extractor,
                        Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
    }

    /**
     * Creates a null safe case-insensitive comparator that puts nulls last.
     */
    public static <T> Comparator<T> getNullSafeCaseInsensitiveNullsLastComparator(
            final Function<T, String> extractor) {
        return Comparator.nullsLast(
                Comparator.comparing(extractor, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    }

    /**
     * Creates a null safe case-insensitive comparator that can work with stuff like
     * getDocRef().getName()
     */
    public static <T1, T2> Comparator<T1> getNullSafeCaseInsensitiveComparator(
            final Function<T1, T2> extractor1,
            final Function<T2, String> extractor2) {
        return getNullSafeComparator(
                extractor1,
                extractor2,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
    }

    public static <T1, T2 extends Comparable<T2>> Comparator<T1> getNullSafeComparator(
            final Function<T1, T2> extractor) {

        // Sort with nulls first but also handle null intermediate values
        return Comparator.nullsFirst(Comparator.comparing(
                extractor,
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public static <T1, T2, T3 extends Comparable<T3>> Comparator<T1> getNullSafeComparator(
            final Function<T1, T2> extractor1,
            final Function<T2, T3> extractor2) {
        return getNullSafeComparator(
                extractor1,
                extractor2,
                Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    public static <T1, T2, T3 extends Comparable<T3>> Comparator<T1> getNullSafeComparator(
            final Function<T1, T2> extractor1,
            final Function<T2, T3> extractor2,
            final Comparator<T3> comparator) {

        // Sort with nulls first but also handle null intermediate values
        return Comparator.comparing(
                extractor1,
                Comparator.nullsFirst(
                        Comparator.comparing(
                                extractor2,
                                Comparator.nullsFirst(comparator))));
    }

    /**
     * Creates a null safe case insensitive comparator that can work with stuff like
     * getDocRef().getName().substring(1,3)
     */
    public static <T1, T2, T3> Comparator<T1> getNullSafeCaseInsensitiveComparator(
            final Function<T1, T2> extractor1,
            final Function<T2, T3> extractor2,
            final Function<T3, String> extractor3) {
        return getNullSafeComparator(
                extractor1,
                extractor2,
                extractor3,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
    }

    public static <T1, T2, T3, T4 extends Comparable<T4>> Comparator<T1> getNullSafeComparator(
            final Function<T1, T2> extractor1,
            final Function<T2, T3> extractor2,
            final Function<T3, T4> extractor3,
            final Comparator<T4> comparator) {

        // Sort with nulls first but also handle deps with null intermediate values
        return Comparator.comparing(
                extractor1,
                Comparator.nullsFirst(
                        Comparator.comparing(
                                extractor2,
                                Comparator.nullsFirst(
                                        Comparator.comparing(
                                                extractor3,
                                                Comparator.nullsFirst(comparator))))));
    }

    /**
     * Provides a way to name a {@link Comparator} to aid with debugging/logging
     *
     * @param name       The name for the comparator.
     * @param comparator The comparator that is delegated to.
     */
    public static <T> Comparator<T> name(final String name, final Comparator<T> comparator) {
        return new NamedComparator<>(name, comparator);
    }

    /**
     * Combine two comparators in a null safe way.
     * If one arg is null, the other arg is returned.
     * If both are null, null is returned.
     * If both args are non-null it is equivalent to:
     * <pre>{@code
     * comparator1.thenComparing(comparator2);
     * }</pre>
     */
    public static <T> Comparator<T> combine(final Comparator<T> comparator1, final Comparator<T> comparator2) {
        if (comparator1 == null) {
            return comparator2;
        } else if (comparator2 == null) {
            return comparator1;
        } else {
            return comparator1.thenComparing(comparator2);
        }
    }

    /**
     * Reverse the comparator if isReversed is true, else just return comparator unchanged.
     * Null-safe.
     */
    public static <T> Comparator<T> reverseIf(final Comparator<T> comparator, final boolean isReversed) {
        if (comparator == null) {
            return comparator;
        } else {
            if (isReversed) {
                return comparator.reversed();
            } else {
                return comparator;
            }
        }
    }

    /**
     * A comparator that always returns zero and thus does not change the order
     */
    public static <T> Comparator<T> noOpComparator() {
        return (o1, o2) -> 0;
    }

    /**
     * Returns a non-null comparator, either comparator if non-null, else a no-op
     * comparator ({@link CompareUtil#noOpComparator()}) that does nothing.
     */
    public static <T> Comparator<T> nonNull(final Comparator<T> comparator) {
        return Objects.requireNonNullElseGet(comparator, CompareUtil::noOpComparator);
    }
}
