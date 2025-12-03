/*
 * Copyright 2016-2025 Crown Copyright
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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

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
     * e.g.
     *
     * <pre>{@code
     * private static final FieldComparators<DBTableStatus> FIELD_COMPARATORS = FieldComparators.builder(
     *     DBTableStatus.class)
     *   .addStringComparator(DBTableStatus.FIELD_DATABASE, DBTableStatus::getDb)
     *   .addStringComparator(DBTableStatus.FIELD_TABLE, DBTableStatus::getTable)
     *   .addCaseLessComparator(DBTableStatus.FIELD_ROW_COUNT, DBTableStatus::getCount)
     *   .addCaseLessComparator(DBTableStatus.FIELD_DATA_SIZE, DBTableStatus::getDataSize)
     *   .addCaseLessComparator(DBTableStatus.FIELD_INDEX_SIZE, DBTableStatus::getIndexSize)
     *   .build();
     * }</pre>
     */
    public static <T> Comparator<T> buildCriteriaComparator(
            final FieldComparators<T> fieldComparatorsMap,
            final BaseCriteria criteria,
            final String... defaultSortFields) {

        Objects.requireNonNull(fieldComparatorsMap);
        Objects.requireNonNull(criteria);

        Comparator<T> comparator = null;

        if (NullSafe.hasItems(criteria.getSortList())) {
            for (final CriteriaFieldSort sort : criteria.getSortList()) {
                final String field = sort.getId();

                Comparator<T> fieldComparator = fieldComparatorsMap.get(field, sort.isIgnoreCase());

                Objects.requireNonNull(fieldComparator, () ->
                        "Missing comparator for field " + field);

                fieldComparator = CompareUtil.reverseIf(fieldComparator, sort.isDesc());

                comparator = CompareUtil.combine(comparator, fieldComparator);
            }
        } else {
            for (final String defaultField : defaultSortFields) {

                final Comparator<T> fieldComparator = fieldComparatorsMap.get(defaultField, true);

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
     * Normalises an {@link Comparable#compareTo(Object)} result into -1, 0, or 1.
     * Useful for doing equality assertions on comparators.
     */
    public static int normalise(final int compareResult) {
        if (compareResult < 0) {
            return -1;
        } else if (compareResult > 0) {
            return 1;
        } else {
            return compareResult;
        }
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
            return null;
        } else {
            return isReversed
                    ? comparator.reversed()
                    : comparator;
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
        return NullSafe.requireNonNullElseGet(comparator, CompareUtil::noOpComparator);
    }


    // --------------------------------------------------------------------------------


    /**
     * Holds a map of fieldId to {@link CaseAwareComparator}
     *
     * @param <T>
     */
    public static class FieldComparators<T> {

        private final Map<String, CaseAwareComparator<T>> comparatorMap;

        public FieldComparators(final Map<String, CaseAwareComparator<T>> comparatorMap) {
            this.comparatorMap = Objects.requireNonNull(comparatorMap);
        }

        /**
         * Gets the comparator associated with fieldId. If the field in question
         * has string values then ignoreCase will determine whether a case-sensitive
         * or case-insensitive comprator is returned.
         *
         * @param fieldId    The ID of the field.
         * @param ignoreCase Whether case should be ignored in comparisons of string values.
         * @return The comparator or null if fieldId is not found in the map.
         */
        public Comparator<T> get(final String fieldId, final boolean ignoreCase) {
            Objects.requireNonNull(fieldId);
            return NullSafe.get(comparatorMap.get(fieldId),
                    caseAwareComparator -> caseAwareComparator.get(ignoreCase));
        }

        /**
         * @param type The class of objects that is being compared.
         */
        public static <T> Builder<T> builder(final Class<T> type) {
            return new Builder<>(type);
        }


        // --------------------------------------------------------------------------------


        public static class Builder<T> {

            private final Map<String, CaseAwareComparator<T>> comparatorMap = new HashMap<>();

            private Builder(final Class<T> type) {
            }

            /**
             * Adds a pair of null-safe comparators for fieldId, using extractor to provide the value.
             */
            public Builder<T> addStringComparator(final String fieldId,
                                                  final Function<T, String> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAwareComparatorImpl<>(
                                CompareUtil.getNullSafeComparator(extractor),
                                CompareUtil.getNullSafeCaseInsensitiveComparator(extractor)));
                return this;
            }

            /**
             * Adds a null-safe comparator for fieldId, using extractor and toString on the enum returned from
             * extractor.
             */
            public <E extends Enum<E>> Builder<T> addEnumComparator(final String fieldId,
                                                                    final Function<T, E> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(CompareUtil.getNullSafeComparator(val ->
                                extractor.apply(val).toString())));
                return this;
            }

            /**
             * Adds a null-safe comparator for fieldId, using extractor and toString on the enum returned from
             * extractor.
             */
            public <E extends Enum<E>> Builder<T> addEnumComparator(final String fieldId,
                                                                    final Function<T, E> extractor,
                                                                    final Function<E, String> toStringFunc) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(CompareUtil.getNullSafeComparator(val -> {
                            final E anEnum = extractor.apply(val);
                            return toStringFunc.apply(anEnum);
                        })));
                return this;
            }

            /**
             * Adds a single comparator for fieldId, using extractor to provide a value that has no
             * concept of case, e.g. {@link Long}, {@link Boolean}, etc.
             * For primitives, use one of:
             * <ul>
             * <li>{@link Builder#addIntComparator(String, ToIntFunction)}</li>
             * <li>{@link Builder#addLongComparator(String, ToLongFunction)}</li>
             * <li>{@link Builder#addBooleanComparator(String, ToBooleanFunction)}</li>
             * </ul>
             */
            public <R extends Comparable<R>> Builder<T> addCaseLessComparator(final String fieldId,
                                                                              final Function<T, R> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(CompareUtil.getNullSafeComparator(extractor)));
                return this;
            }

            /**
             * Adds a single comparator for fieldId, using extractor to provide the value.
             */
            public Builder<T> addIntComparator(final String fieldId,
                                               final ToIntFunction<T> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(Comparator.comparingInt(extractor)));
                return this;
            }

            /**
             * Adds a single comparator for fieldId, using extractor to provide the value.
             */
            public Builder<T> addLongComparator(final String fieldId,
                                                final ToLongFunction<T> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(Comparator.comparingLong(extractor)));
                return this;
            }

            /**
             * Adds a single comparator for fieldId, using extractor to provide the primitive boolean value.
             */
            public Builder<T> addBooleanComparator(final String fieldId,
                                                   final ToBooleanFunction<T> extractor) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(extractor);
                // Primitive boolean so no need to worry about nulls
                comparatorMap.put(
                        fieldId,
                        new CaseAgnosticComparator<>(Comparator.nullsFirst((o1, o2) ->
                                Boolean.compare(
                                        extractor.applyAsBoolean(o1),
                                        extractor.applyAsBoolean(o2)))));
                return this;
            }

            /**
             * Adds the supplied pair of comparators for fieldId.
             */
            public Builder<T> addCaseAwareComparator(final String fieldId,
                                                     final Comparator<T> caseSenseComparator,
                                                     final Comparator<T> caseInSenseComparator) {
                Objects.requireNonNull(fieldId);
                Objects.requireNonNull(caseSenseComparator);
                Objects.requireNonNull(caseInSenseComparator);
                comparatorMap.put(fieldId, new CaseAwareComparatorImpl<>(
                        caseSenseComparator,
                        caseInSenseComparator));
                return this;
            }

            public FieldComparators<T> build() {
                return new FieldComparators<>(Collections.unmodifiableMap(comparatorMap));
            }


            // --------------------------------------------------------------------------------


        }
    }


    // --------------------------------------------------------------------------------

    public interface CaseAwareComparator<T> {

        Comparator<T> get(final boolean ignoreCase);
    }


    // --------------------------------------------------------------------------------


    public static class CaseAwareComparatorImpl<T> implements CaseAwareComparator<T> {

        private final Comparator<T> caseSenseComparator;
        private final Comparator<T> caseInSenseComparator;

        public CaseAwareComparatorImpl(final Comparator<T> caseSenseComparator,
                                       final Comparator<T> caseInSenseComparator) {
            this.caseSenseComparator = Objects.requireNonNull(caseSenseComparator);
            this.caseInSenseComparator = Objects.requireNonNull(caseInSenseComparator);
        }

        @Override
        public Comparator<T> get(final boolean ignoreCase) {
            return ignoreCase
                    ? caseInSenseComparator
                    : caseSenseComparator;
        }
    }


    // --------------------------------------------------------------------------------


    public static class CaseAgnosticComparator<T> implements CaseAwareComparator<T> {

        private final Comparator<T> comparator;

        public CaseAgnosticComparator(final Comparator<T> comparator) {
            this.comparator = Objects.requireNonNull(comparator);
        }

        @Override
        public Comparator<T> get(final boolean ignoreCase) {
            return comparator;
        }
    }
}
