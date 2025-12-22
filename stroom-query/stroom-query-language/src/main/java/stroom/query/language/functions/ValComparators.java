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

package stroom.query.language.functions;

import stroom.util.logging.LogUtil;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.NullSafe;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Class for providing comparators for comparing {@link Val} instances.
 * Comparators are null/ValNull last.
 */
public class ValComparators {

    /**
     * This comparator is likely not transitive so breaks the compare contract, so should not be used
     * for sorting purposes.
     */
    public static Comparator<Val> GENERIC_CASE_INSENSITIVE_COMPARATOR = CompareUtil.name(
            "GenericCaseInsensitiveValComparator", getComparator(false));
    /**
     * This comparator is likely not transitive so breaks the compare contract, so should not be used
     * for sorting purposes.
     */
    public static Comparator<Val> GENERIC_CASE_SENSITIVE_COMPARATOR = CompareUtil.name(
            "GenericCaseSensitiveValComparator", getComparator(true));

    // Comparators for comparing Val instances in the different ways you can get a value out of them.
    // They assume the Vals being compared are non-null as null protection is handled by
    // the generic comparator.
    public static final Comparator<Val> AS_BOOLEAN_COMPARATOR = CompareUtil.name("ValAsBoolean",
            Comparator.comparing(Val::toBoolean, Comparator.nullsLast(Boolean::compareTo)));
    public static final Comparator<Val> AS_INTEGER_COMPARATOR = CompareUtil.name("ValAsInteger",
            Comparator.comparing(Val::toInteger, Comparator.nullsLast(Integer::compareTo)));
    public static final Comparator<Val> AS_LONG_COMPARATOR = CompareUtil.name("ValAsLong",
            Comparator.comparing(Val::toLong, Comparator.nullsLast(Long::compareTo)));
    public static final Comparator<Val> AS_FLOAT_COMPARATOR = CompareUtil.name("ValAsFloat",
            Comparator.comparing(Val::toFloat, Comparator.nullsLast(Float::compareTo)));
    public static final Comparator<Val> AS_DOUBLE_COMPARATOR = CompareUtil.name("ValAsDouble",
            Comparator.comparing(Val::toDouble, Comparator.nullsLast(Double::compareTo)));
    public static final Comparator<Val> AS_CASE_INSENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsCaseInsensitiveString",
            Comparator.comparing(Val::toString, Comparator.nullsLast(String::compareToIgnoreCase)));
    public static final Comparator<Val> AS_CASE_SENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsCaseSensitiveString",
            Comparator.comparing(Val::toString, Comparator.nullsLast(String::compareTo)));

    // String is an odd one. If both values are numeric then we want to compare them as numbers
    public static final Comparator<Val> AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsDoubleThenCaseInsensitiveString",
            AS_DOUBLE_COMPARATOR.thenComparing(AS_CASE_INSENSITIVE_STRING_COMPARATOR));
    public static final Comparator<Val> AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsDoubleThenCaseSensitiveString",
            AS_DOUBLE_COMPARATOR.thenComparing(AS_CASE_SENSITIVE_STRING_COMPARATOR));

    public static final Comparator<Val> AS_LONG_THEN_CASE_INSENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsLongThenCaseInsensitiveString",
            AS_LONG_COMPARATOR.thenComparing(AS_CASE_INSENSITIVE_STRING_COMPARATOR));
    public static final Comparator<Val> AS_LONG_THEN_CASE_SENSITIVE_STRING_COMPARATOR = CompareUtil.name(
            "ValAsLongThenCaseSensitiveString",
            AS_LONG_COMPARATOR.thenComparing(AS_CASE_SENSITIVE_STRING_COMPARATOR));

    private static final ValComparatorFactory AS_STRING_COMPARATOR_FACTORY = DualValComparatorFactory.create(
            AS_CASE_SENSITIVE_STRING_COMPARATOR, AS_CASE_INSENSITIVE_STRING_COMPARATOR);
    private static final ValComparatorFactory AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY =
            DualValComparatorFactory.create(AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
                    AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR);
    private static final ValComparatorFactory AS_LONG_THEN_STRING_COMPARATOR_FACTORY =
            DualValComparatorFactory.create(AS_LONG_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
                    AS_LONG_THEN_CASE_INSENSITIVE_STRING_COMPARATOR);

    // Nested map to map pairs of types to a comparatorFactory, pairs are added both ways round
    private static final Map<Type, Map<Type, ValComparatorFactory>> COMPARATOR_NESTED_MAP = new EnumMap<>(Type.class);

    private static final Map<Type, Comparator<Val>> SORT_COMPARATORS_BY_TYPE_MAP = new EnumMap<>(Type.class);

    private static final double DOUBLE_TOLERANCE_FRACTION = 0.000001d;

    static {
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.NULL, (o1, o2) -> 0);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.BOOLEAN, AS_BOOLEAN_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.BYTE, AS_INTEGER_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.SHORT, AS_INTEGER_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.INTEGER, AS_INTEGER_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.LONG, AS_LONG_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.DOUBLE, AS_DOUBLE_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.FLOAT, AS_FLOAT_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.DATE, AS_LONG_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.DURATION, AS_LONG_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.STRING, AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.XML, AS_CASE_INSENSITIVE_STRING_COMPARATOR);
        SORT_COMPARATORS_BY_TYPE_MAP.put(Type.ERR, AS_CASE_INSENSITIVE_STRING_COMPARATOR); // always prefixed with Err:

        // NOTE: Types get added both ways round by the method, so order doesn't matter.
        // Don't need to define pairs with the same type as the default comparator
        // on the Val instance handles that.
        addTypePair(Type.BOOLEAN, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.BOOLEAN, Type.INTEGER, AS_INTEGER_COMPARATOR);
        addTypePair(Type.BOOLEAN, Type.STRING, AS_BOOLEAN_COMPARATOR);

        // May be comparing "1.23" with 10
        addTypePair(Type.STRING, Type.LONG, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.INTEGER, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.DOUBLE, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.FLOAT, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);

        // May be comparing "1.23" with 10
        addTypePair(Type.XML, Type.LONG, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.XML, Type.INTEGER, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.XML, Type.DOUBLE, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.XML, Type.FLOAT, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);

        addTypePair(Type.DURATION, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.DURATION, Type.INTEGER, AS_LONG_COMPARATOR);
        addTypePair(Type.DURATION, Type.FLOAT, AS_LONG_COMPARATOR);
        addTypePair(Type.DURATION, Type.DOUBLE, AS_LONG_COMPARATOR);
        addTypePair(Type.DURATION, Type.STRING, AS_LONG_THEN_STRING_COMPARATOR_FACTORY);

        addTypePair(Type.DATE, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.DATE, Type.INTEGER, AS_LONG_COMPARATOR);
        addTypePair(Type.DATE, Type.FLOAT, AS_LONG_COMPARATOR);
        addTypePair(Type.DATE, Type.DOUBLE, AS_LONG_COMPARATOR);
        addTypePair(Type.DATE, Type.STRING, AS_LONG_THEN_STRING_COMPARATOR_FACTORY);

        addTypePair(Type.BYTE, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.BYTE, Type.FLOAT, AS_DOUBLE_COMPARATOR);
        addTypePair(Type.BYTE, Type.DOUBLE, AS_DOUBLE_COMPARATOR);

        addTypePair(Type.SHORT, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.SHORT, Type.FLOAT, AS_DOUBLE_COMPARATOR);
        addTypePair(Type.SHORT, Type.DOUBLE, AS_DOUBLE_COMPARATOR);

        addTypePair(Type.INTEGER, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.INTEGER, Type.FLOAT, AS_DOUBLE_COMPARATOR);
        addTypePair(Type.INTEGER, Type.DOUBLE, AS_DOUBLE_COMPARATOR);

        addTypePair(Type.LONG, Type.FLOAT, AS_DOUBLE_COMPARATOR);
        addTypePair(Type.LONG, Type.DOUBLE, AS_DOUBLE_COMPARATOR);

        addTypePair(Type.FLOAT, Type.DOUBLE, AS_DOUBLE_COMPARATOR);

        addTypePair(Type.ERR, Type.STRING, AS_STRING_COMPARATOR_FACTORY);

        addTypePair(Type.NULL, Type.STRING, AS_STRING_COMPARATOR_FACTORY);
    }

    private ValComparators() {
        // Statics only
    }

    /**
     * type1 and type2 can be in any order
     */
    public static Optional<Comparator<Val>> getComparatorForTypes(final Type type1,
                                                                  final Type type2,
                                                                  final boolean isCaseInsensitive) {
        if (type1 == null || type2 == null) {
            return Optional.empty();
        } else {
            return getComparatorFactory(type1, type2)
                    .map(comparatorFactory -> comparatorFactory.getComparator(isCaseInsensitive));
        }
    }

    /**
     * type1 and type2 can be in any order
     */
    private static Optional<ValComparatorFactory> getComparatorFactory(final Type type1, final Type type2) {
        if (type1 == null || type2 == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(COMPARATOR_NESTED_MAP.get(type1))
                    .map(subMap -> subMap.get(type2));
        }
    }

    private static void addTypePair(final Type type1,
                                    final Type type2,
                                    final Comparator<Val> comparator) {
        Objects.requireNonNull(type1);
        Objects.requireNonNull(type2);
        Objects.requireNonNull(comparator);
        final ValComparatorFactory comparatorFactory = SingleValComparatorFactory.create(comparator);
        doOrderedPut(type1, type2, comparatorFactory);
        // Now put the same but with the types reversed, so you can look up either way
        doOrderedPut(type2, type1, comparatorFactory);
    }

    private static void addTypePair(final Type type1,
                                    final Type type2,
                                    final ValComparatorFactory comparatorFactory) {
        Objects.requireNonNull(type1);
        Objects.requireNonNull(type2);
        Objects.requireNonNull(comparatorFactory);
        doOrderedPut(type1, type2, comparatorFactory);
        // Now put the same but with the types reversed, so you can look up either way
        doOrderedPut(type2, type1, comparatorFactory);
    }

    private static void doOrderedPut(final Type type1,
                                     final Type type2,
                                     final ValComparatorFactory comparatorFactory) {
        // Little check to stop us accidentally defining a comparator for a pair twice, but
        // with a different comparator each time
        final ValComparatorFactory existingComparatorFactory = getComparatorFactory(type1, type2)
                .orElse(null);
        if (existingComparatorFactory != null
            && !Objects.equals(existingComparatorFactory, comparatorFactory)) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Trying to put a different comparator factory for types {} & {}",
                    type1, type2));
        }

        final Map<Type, ValComparatorFactory> subMap = COMPARATOR_NESTED_MAP.computeIfAbsent(type1,
                k -> new EnumMap<>(Type.class));
        subMap.put(type2, comparatorFactory);
    }

    /**
     * @param comparator A comparator that will only be comparing non-null {@link Val} instances
     *                   of the same type.
     * @return A generic comparator for {@link Val} instances that will defer to a type specific
     * comparator if the values being compared are both match type, if not it will use a generic
     * comparator.
     */
    static <V extends Val> Comparator<Val> asGenericComparator(final Class<V> type,
                                                               final Comparator<Val> comparator) {

        // If this comparator is called from ValComparators.GENERIC_COMPARATOR then it means
        // we do the isInstance check in there and in here, but it gives some protection
        // if this comparator is called directly for a Val type that is not appropriate
        // for the delegate comparator.
        return (val1, val2) -> {
            if (type.isInstance(val1) && type.isInstance(val2)) {
                // Both non-null and same type so use type's own comparator
                return comparator.compare(val1, val2);
            } else {
                // Fall back to our generic one to deal with mixed types
                return GENERIC_CASE_SENSITIVE_COMPARATOR.compare(val1, val2);
            }
        };
    }

    /**
     * @param comparator A comparator that will only be comparing non-null {@link Val} instances
     *                   of the same type.
     * @return A generic comparator for {@link Val} instances that will defer to a type specific
     * comparator if the values being compared are both match type, if not it will use a generic
     * comparator.
     */
    public static <V extends Val> Comparator<Val> asGenericComparator(final Class<V> type,
                                                                      final Comparator<Val> comparator,
                                                                      final Comparator<Val> genericComparator) {

        // If this comparator is called from ValComparators.GENERIC_COMPARATOR then it means
        // we do the isInstance check in there and in here, but it gives some protection
        // if this comparator is called directly for a Val type that is not appropriate
        // for the delegate comparator.
        return (val1, val2) -> {
            if (type.isInstance(val1) && type.isInstance(val2)) {
                // Both non-null and same type so use type's own comparator
                return comparator.compare(val1, val2);
            } else {
                // Fall back to our generic one to deal with mixed types
                return GENERIC_CASE_SENSITIVE_COMPARATOR.compare(val1, val2);
            }
        };
    }

    /**
     * @return True if val is null or is {@link ValNull#INSTANCE}
     */
    public static boolean isNull(final Val val) {
        return val == null || val == ValNull.INSTANCE;
    }

    /**
     * @return True if val is not null and is not {@link ValNull#INSTANCE}
     */
    public static boolean isNotNull(final Val val) {
        return val != null && val != ValNull.INSTANCE;
    }

    public static Optional<Comparator<Val>> getSortComparator(final Type valType) {
        return NullSafe.getAsOptional(
                valType,
                SORT_COMPARATORS_BY_TYPE_MAP::get);
    }

    public static Comparator<Val> getComparator(final boolean isCaseSensitive) {
        return (o1, o2) ->
                compare(o1, o2, isCaseSensitive);
    }

    /**
     * A generic comparison method for two {@link Val} instances.
     * null or ValNull values are considered greater than non-null or
     * non ValNull values.
     * If val1 and val2 are the same subclass then the specific comparator
     * for that type will be delegated to.
     */
    private static int compare(final Val val1, final Val val2, final boolean isCaseSensitive) {
        if (isNull(val1) && isNull(val2)) {
            return 0;
        } else if (isNull(val1)) {
            return 1;
        } else if (isNull(val2)) {
            return -1;
        } else if (Objects.equals(val1.getClass(), val2.getClass())) {
            // Same type so delegate to the type's own comparator
            return val1.getDefaultComparator(isCaseSensitive).compare(val1, val2);
        } else if (bothAreNumeric(val1, val2)) {
            // Mixed number types so need to be a bit careful
            // Could be comparing valString("123") with ValDouble(123.456)
            if (noFractionalParts(val1, val2)) {
                // No fractional parts so compare as long which avoids all precision
                // problems that come with doubles
                return AS_LONG_COMPARATOR.compare(val1, val2);
            } else {
                // Mixed decimal values, e.g. float + double
                // or one decimal one non-decimal, e.g. int + double,
                // so do the comparison as double
                if (haveTypes(val1, val2, ValDouble.class, ValFloat.class)) {
                    // Special case to deal with differing precision in floats/doubles
                    return compareAsDoublesWithTolerance(val1, val2);
                } else {
                    return AS_DOUBLE_COMPARATOR.compare(val1, val2);
                }
            }
        } else {
            // Get the appropriate comparator for the mixed types or fallback to a string
            // comparison if
            final Comparator<Val> comparator = getComparatorForTypes(val1.type(), val2.type(), isCaseSensitive)
                    .orElseGet(() -> getAsStringComparator(isCaseSensitive));
            // e.g. comparing ValString to ValInteger, which doesn't really make any sense
            // but, we might as well compare on something, so compare on the value as a string
//            return AS_TYPE_COMPARATOR.compare(val1, val2);
            return comparator.compare(val1, val2);
        }
    }

    /**
     * This is to deal with the
     *
     * @param val1
     * @param val2
     * @return
     */
    static int compareAsDoublesWithTolerance(final Val val1, final Val val2) {
        final Double d1 = val1.toDouble();
        final Double d2 = val2.toDouble();

        if (d1 != null
            && d2 != null
            && Math.abs(d1 - d2) < DOUBLE_TOLERANCE_FRACTION * Math.abs(d2)) {
            return 0;
        } else {
            return AS_DOUBLE_COMPARATOR.compare(val1, val2);
        }
    }

    static boolean haveType(final Object val1,
                            final Object val2,
                            final Class<?> type) {
        return type.isInstance(val1) && type.isInstance(val2);
    }

    static boolean haveTypes(final Object val1,
                             final Object val2,
                             final Class<?> typeA,
                             final Class<?> typeB) {
        return (typeA.isInstance(val1) && typeB.isInstance(val2))
               || (typeB.isInstance(val1) && typeA.isInstance(val2));
    }

    private static boolean bothAreNumeric(final Val a, final Val b) {
        return a != null
               && b != null
               && a.hasNumericValue()
               && b.hasNumericValue();
    }

    /**
     * @return True if neither value has a decimal part.
     */
    private static boolean noFractionalParts(final Val a, final Val b) {
        return !hasFractionalPart(a)
               && !hasFractionalPart(b);
    }

    static boolean hasFractionalPart(final Val val) {
        return val != null && val.hasFractionalPart();
    }

    private static Comparator<Val> getAsStringComparator(final boolean isCaseSensitive) {
        return isCaseSensitive
                ? AS_CASE_SENSITIVE_STRING_COMPARATOR
                : AS_CASE_INSENSITIVE_STRING_COMPARATOR;
    }


    // --------------------------------------------------------------------------------


    interface ValComparatorFactory {

        Comparator<Val> getComparator(final boolean isCaseSensitive);
    }


    // --------------------------------------------------------------------------------


    private record SingleValComparatorFactory(Comparator<Val> comparator) implements ValComparatorFactory {

        private static SingleValComparatorFactory create(final Comparator<Val> comparator) {
            return new SingleValComparatorFactory(comparator);
        }

        @Override
        public Comparator<Val> getComparator(final boolean isCaseSensitive) {
            return comparator;
        }
    }


    // --------------------------------------------------------------------------------


    private record DualValComparatorFactory(Comparator<Val> caseSensitiveComparator,
                                            Comparator<Val> caseInsensitiveComparator)
            implements ValComparatorFactory {

        private static DualValComparatorFactory create(final Comparator<Val> caseSensitiveComparator,
                                                       final Comparator<Val> caseInsensitiveComparator) {
            return new DualValComparatorFactory(caseSensitiveComparator, caseInsensitiveComparator);
        }

        @Override
        public Comparator<Val> getComparator(final boolean isCaseSensitive) {
            return isCaseSensitive
                    ? caseSensitiveComparator
                    : caseInsensitiveComparator;
        }
    }
}
