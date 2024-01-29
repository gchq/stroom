package stroom.query.language.functions;

import stroom.util.logging.LogUtil;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ValComparators {

    public static Comparator<Val> GENERIC_CASE_INSENSITIVE_COMPARATOR = getComparator(false);
    public static Comparator<Val> GENERIC_CASE_SENSITIVE_COMPARATOR = getComparator(true);

    // Comparators for comparing Val instances in the different ways you can get a value out of them.
    // They assume the Vals being compared are non-null as null protection is handled by
    // the generic comparator.
    public static final Comparator<Val> AS_BOOLEAN_COMPARATOR = Comparator.comparing(
            Val::toBoolean, Comparator.nullsLast(Boolean::compareTo));
    public static final Comparator<Val> AS_INTEGER_COMPARATOR = Comparator.comparing(
            Val::toInteger, Comparator.nullsLast(Integer::compareTo));
    public static final Comparator<Val> AS_LONG_COMPARATOR = Comparator.comparing(
            Val::toLong, Comparator.nullsLast(Long::compareTo));
    public static final Comparator<Val> AS_FLOAT_COMPARATOR = Comparator.comparing(
            Val::toFloat, Comparator.nullsLast(Float::compareTo));
    public static final Comparator<Val> AS_DOUBLE_COMPARATOR = Comparator.comparing(
            Val::toDouble, Comparator.nullsLast(Double::compareTo));
    /**
     * Case-insensitive, nulls last.
     */
    public static final Comparator<Val> AS_CASE_INSENSITIVE_STRING_COMPARATOR = Comparator.comparing(
            Val::toString, Comparator.nullsLast(String::compareToIgnoreCase));
    public static final Comparator<Val> AS_CASE_SENSITIVE_STRING_COMPARATOR = Comparator.comparing(
            Val::toString, Comparator.nullsLast(String::compareTo));
    private static final ValComparatorFactory AS_STRING_COMPARATOR_FACTORY = DualValComparatorFactory.create(
            AS_CASE_SENSITIVE_STRING_COMPARATOR, AS_CASE_INSENSITIVE_STRING_COMPARATOR);
    // String is an odd one. If both values are numeric then we want to compare them as numbers
    public static final Comparator<Val> AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR =
            AS_DOUBLE_COMPARATOR.thenComparing(AS_CASE_INSENSITIVE_STRING_COMPARATOR);
    public static final Comparator<Val> AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR =
            AS_DOUBLE_COMPARATOR.thenComparing(AS_CASE_SENSITIVE_STRING_COMPARATOR);
    private static final ValComparatorFactory AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY =
            DualValComparatorFactory.create(AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
                    AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR);

    public static final Comparator<Val> AS_LONG_THEN_CASE_INSENSITIVE_STRING_COMPARATOR =
            AS_LONG_COMPARATOR.thenComparing(AS_CASE_INSENSITIVE_STRING_COMPARATOR);
    public static final Comparator<Val> AS_LONG_THEN_CASE_SENSITIVE_STRING_COMPARATOR =
            AS_LONG_COMPARATOR.thenComparing(AS_CASE_SENSITIVE_STRING_COMPARATOR);
    private static final ValComparatorFactory AS_LONG_THEN_STRING_COMPARATOR_FACTORY =
            DualValComparatorFactory.create(AS_LONG_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
                    AS_LONG_THEN_CASE_INSENSITIVE_STRING_COMPARATOR);

    // Nested map to map pairs of types to a comparator, pairs are added both ways round
    private static final Map<Type, Map<Type, ValComparatorFactory>> COMPARATOR_NESTED_MAP = new EnumMap<>(Type.class);

    static {
        // NOTE: Types get added both ways round by the method, so order doesn't matter.
        // Don't need to define pairs with the same type as the default comparator
        // on the Val instance handles that.
        addTypePair(Type.BOOLEAN, Type.LONG, AS_LONG_COMPARATOR);
        addTypePair(Type.BOOLEAN, Type.INTEGER, AS_INTEGER_COMPARATOR);
        addTypePair(Type.BOOLEAN, Type.STRING, AS_STRING_COMPARATOR_FACTORY);

        // May be comparing "1.23" with 10
        addTypePair(Type.STRING, Type.LONG, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.INTEGER, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.DOUBLE, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);
        addTypePair(Type.STRING, Type.FLOAT, AS_DOUBLE_THEN_STRING_COMPARATOR_FACTORY);

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

    private static void addTypePair(final Type type1,
                                    final Type type2,
                                    final Comparator<Val> caseSensitiveComparator,
                                    final Comparator<Val> caseInsensitiveComparator) {
        Objects.requireNonNull(type1);
        Objects.requireNonNull(type2);
        Objects.requireNonNull(caseSensitiveComparator);
        Objects.requireNonNull(caseInsensitiveComparator);
        final ValComparatorFactory comparatorFactory = DualValComparatorFactory.create(
                caseSensitiveComparator,
                caseInsensitiveComparator);
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
    public static <V extends Val, T> Comparator<Val> asGenericComparator(
            final Class<V> type,
            final java.util.function.Function<Val, T> keyExtractor,
            final Comparator<T> comparator) {

        final Comparator<Val> delegateComparator = Comparator.comparing(
                keyExtractor,
                Comparator.nullsLast(comparator));

        return (val1, val2) -> {
            if (type.isInstance(val1) && type.isInstance(val2)) {
                // Both non-null and same type so use type's own comparator
                return delegateComparator.compare(val1, val2);
            } else {
                // Fall back to our generic one to deal with mixed types
                return GENERIC_CASE_INSENSITIVE_COMPARATOR.compare(val1, val2);
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
                // so do the comparison as double, with a tolerance
                final Double dbl1 = val1.toDouble();
                final Double dbl2 = val2.toDouble();

                if (dbl1 != null
                        && dbl2 != null
                        && Math.abs(dbl1 - dbl2) < Val.FLOATING_POINT_EQUALITY_TOLERANCE) {
                    return 0;
                } else {
                    return AS_DOUBLE_COMPARATOR.compare(val1, val2);
                }
            }
        } else {
            // Get the appropriate comparator for the mixed types or fallback to a string
            // comparison if
            final Comparator<Val> comparator = getComparatorForTypes(val1.type(), val2.type(), isCaseSensitive)
                    .orElseGet(() -> ValComparators.getAsStringComparator(isCaseSensitive));
            // e.g. comparing ValString to ValInteger, which doesn't really make any sense
            // but, we might as well compare on something, so compare on the value as a string
//            return AS_TYPE_COMPARATOR.compare(val1, val2);
            return comparator.compare(val1, val2);
        }
    }

    public static boolean hasBothTypes(final Object val1,
                                       final Object val2,
                                       final Class<?> typeA,
                                       final Class<?> typeB) {
        return (typeA.isInstance(val1) && typeB.isInstance(val2))
                || (typeA.isInstance(val2) && typeB.isInstance(val1));
    }

    public static boolean atLeastOneHasType(final Object val1,
                                            final Object val2,
                                            final Class<?> type) {
        return type.isInstance(val1) || type.isInstance(val2);
    }

    public static boolean haveType(final Object val1,
                                   final Object val2,
                                   final Class<?> type) {
        return type.isInstance(val1) && type.isInstance(val2);
    }

    public static boolean bothAreNumeric(final Val a, final Val b) {
        return a != null
                && b != null
                && a.hasNumericValue()
                && b.hasNumericValue();
    }

    public static boolean atLeastOneIsNumeric(final Val a, final Val b) {
        return (a != null && a.hasNumericValue())
                || (b != null && b.hasNumericValue());
    }

    /**
     * @return True if either value has a decimal part.
     */
    static boolean haveFractionalParts(final Val a, final Val b) {
        return hasFractionalPart(a) || hasFractionalPart(b);
    }

    /**
     * @return True if neither value has a decimal part.
     */
    static boolean noFractionalParts(final Val a, final Val b) {
        return !hasFractionalPart(a)
                && !hasFractionalPart(b);
    }

    static boolean hasFractionalPart(final Val val) {
        return val != null && val.hasFractionalPart();
    }

    public static Comparator<Val> getAsStringComparator(final boolean isCaseSensitive) {
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
