package stroom.util;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Util methods for safely getting properties (or properties of properties of ...) from
 * a value with protection from null values anywhere in the chain.
 */
public class NullSafe {

    private NullSafe() {
    }

    public static <T> boolean isEmpty(final Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    public static <T> boolean isNotEmpty(final Collection<T> collection) {
        return collection != null && !collection.isEmpty();
    }

    public static <T1, R> R get(final T1 value,
                                final Function<T1, R> getter) {
        if (value == null) {
            return null;
        } else {
            return Objects.requireNonNull(getter).apply(value);
        }
    }

    public static <T1, R> R getOrElse(final T1 value,
                                      final Function<T1, R> getter,
                                      final R other) {
        return Objects.requireNonNullElse(get(value, getter), other);
    }

    public static <T1, R> R getOrElseGet(final T1 value,
                                         final Function<T1, R> getter,
                                         final Supplier<R> otherSupplier) {
        return Objects.requireNonNullElseGet(get(value, getter), otherSupplier);
    }

    public static <T1, R> Optional<R> getAsOptional(final T1 value,
                                                    final Function<T1, R> getter) {
        if (value == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(Objects.requireNonNull(getter).apply(value));
        }
    }

    public static <T> void consume(final T value,
                                   final Consumer<T> consumer) {
        if (value != null && consumer != null) {
            consumer.accept(value);
        }
    }

    /**
     * Allows you to test some property of a value without worrying if the value is null, e.g.
     * <pre><code>
     *    List<Sting> list = null;
     *    boolean hasValues = NullSafe.test(list, list -> list.size > 0);
     * </code></pre>
     *
     * @return false if value is null, else return the value of the predicate when applied
     * to the result of the getter.
     */
    public static <T1, R> boolean test(final T1 value,
                                       final Function<T1, R> getter,
                                       final Predicate<R> predicate) {
        if (value == null) {
            return false;
        } else {
            final R result = Objects.requireNonNull(getter)
                    .apply(value);
            return result != null
                    && Objects.requireNonNull(predicate)
                    .test(result);
        }
    }

    public static <T1> String toString(final T1 value,
                                       final Function<T1, Object> getter) {
        return toStringOrElse(value, getter, null);
    }

    public static <T1> String toStringOrElse(final T1 value,
                                             final Function<T1, Object> getter,
                                             final String other) {
        if (value == null) {
            return other;
        } else {
            final Object value2 = Objects.requireNonNull(getter).apply(value);
            return convertToString(value2, other);
        }
    }

    public static <T1> String toStringOrElseGet(final T1 value,
                                                final Function<T1, Object> getter,
                                                final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final Object value2 = Objects.requireNonNull(getter).apply(value);
            return convertToString(value2, otherSupplier);
        }
    }

    public static <T1, T2, R> R get(final T1 value,
                                    final Function<T1, T2> getter1,
                                    final Function<T2, R> getter2) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                return Objects.requireNonNull(getter2).apply(value2);
            }
        }
    }

    public static <T1, T2, R> R getOrElse(final T1 value,
                                          final Function<T1, T2> getter1,
                                          final Function<T2, R> getter2,
                                          final R other) {
        return Objects.requireNonNullElse(get(value, getter1, getter2), other);
    }

    public static <T1, T2, R> R getOrElseGet(final T1 value,
                                             final Function<T1, T2> getter1,
                                             final Function<T2, R> getter2,
                                             final Supplier<R> otherSupplier) {
        return Objects.requireNonNullElseGet(get(value, getter1, getter2), otherSupplier);
    }

    public static <T1, T2, R> Optional<R> getAsOptional(final T1 value,
                                                        final Function<T1, T2> getter1,
                                                        final Function<T2, R> getter2) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(Objects.requireNonNull(getter2).apply(value2));
            }
        }
    }

    public static <T1, T2> void consume(final T1 value,
                                        final Function<T1, T2> getter1,
                                        final Consumer<T2> consumer) {
        if (value != null && consumer != null) {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 != null) {
                consumer.accept(value2);
            }
        }
    }

    /**
     * Allows you to test some property of a value without worrying if the value is null, e.g.
     * <pre><code>
     *    List<Sting> list = null;
     *    boolean hasValues = NullSafe.test(list, list -> list.size > 0);
     * </code></pre>
     *
     * @return false if value is null, else return the value of the predicate when applied
     * to the result of the getter.
     */
    public static <T1, T2, R> boolean test(final T1 value,
                                           final Function<T1, T2> getter1,
                                           final Function<T2, R> getter2,
                                           final Predicate<R> predicate) {
        if (value == null) {
            return false;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1)
                    .apply(value);
            if (value2 == null) {
                return false;
            } else {
                final R result = Objects.requireNonNull(getter2).apply(value2);
                return result != null
                        && Objects.requireNonNull(predicate)
                        .test(result);
            }
        }
    }

    public static <T1, T2> String toString(final T1 value,
                                           final Function<T1, T2> getter1,
                                           final Function<T2, Object> getter2) {
        return toStringOrElse(value, getter1, getter2, null);
    }

    public static <T1, T2> String toStringOrElse(final T1 value,
                                                 final Function<T1, T2> getter1,
                                                 final Function<T2, Object> getter2,
                                                 final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final Object value3 = Objects.requireNonNull(getter2).apply(value2);
                return convertToString(value3, otherSupplier);
            }
        }
    }

    public static <T1, T2, T3, R> R get(final T1 value,
                                        final Function<T1, T2> getter1,
                                        final Function<T2, T3> getter2,
                                        final Function<T3, R> getter3) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return null;
                } else {
                    return Objects.requireNonNull(getter3).apply(value3);
                }
            }
        }
    }

    public static <T1, T2, T3, R> R getOrElse(final T1 value,
                                              final Function<T1, T2> getter1,
                                              final Function<T2, T3> getter2,
                                              final Function<T3, R> getter3,
                                              final R other) {
        return Objects.requireNonNullElse(get(value, getter1, getter2, getter3), other);
    }

    public static <T1, T2, T3, R> R getOrElseGet(final T1 value,
                                                 final Function<T1, T2> getter1,
                                                 final Function<T2, T3> getter2,
                                                 final Function<T3, R> getter3,
                                                 final Supplier<R> otherSupplier) {
        return Objects.requireNonNullElseGet(get(value, getter1, getter2, getter3), otherSupplier);
    }

    public static <T1, T2, T3, R> Optional<R> getAsOptional(final T1 value,
                                                            final Function<T1, T2> getter1,
                                                            final Function<T2, T3> getter2,
                                                            final Function<T3, R> getter3) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return Optional.empty();
                } else {
                    return Optional.ofNullable(Objects.requireNonNull(getter3).apply(value3));
                }
            }
        }
    }

    public static <T1, T2, T3> String toString(final T1 value,
                                               final Function<T1, T2> getter1,
                                               final Function<T2, T3> getter2,
                                               final Function<T3, Object> getter3) {
        return toStringOrElse(value, getter1, getter2, getter3, null);
    }

    public static <T1, T2, T3> String toStringOrElse(final T1 value,
                                                     final Function<T1, T2> getter1,
                                                     final Function<T2, T3> getter2,
                                                     final Function<T3, Object> getter3,
                                                     final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return handleNull(otherSupplier);
                } else {
                    final Object value4 = Objects.requireNonNull(getter3).apply(value3);
                    return convertToString(value4, otherSupplier);
                }
            }
        }
    }

    public static <T1, T2, T3, T4, R> R get(final T1 value,
                                            final Function<T1, T2> getter1,
                                            final Function<T2, T3> getter2,
                                            final Function<T3, T4> getter3,
                                            final Function<T4, R> getter4) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return null;
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return null;
                    } else {
                        return Objects.requireNonNull(getter4).apply(value4);
                    }
                }
            }
        }
    }

    public static <T1, T2, T3, T4, R> R getOrElse(final T1 value,
                                                  final Function<T1, T2> getter1,
                                                  final Function<T2, T3> getter2,
                                                  final Function<T3, T4> getter3,
                                                  final Function<T4, R> getter4,
                                                  final R other) {
        return Objects.requireNonNullElse(get(value, getter1, getter2, getter3, getter4), other);
    }

    public static <T1, T2, T3, T4, R> R getOrElseGet(final T1 value,
                                                     final Function<T1, T2> getter1,
                                                     final Function<T2, T3> getter2,
                                                     final Function<T3, T4> getter3,
                                                     final Function<T4, R> getter4,
                                                     final Supplier<R> otherSupplier) {
        return Objects.requireNonNullElseGet(get(value, getter1, getter2, getter3, getter4), otherSupplier);
    }

    public static <T1, T2, T3, T4, R> Optional<R> getAsOptional(final T1 value,
                                                                final Function<T1, T2> getter1,
                                                                final Function<T2, T3> getter2,
                                                                final Function<T3, T4> getter3,
                                                                final Function<T4, R> getter4) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return Optional.empty();
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return Optional.empty();
                    } else {
                        return Optional.ofNullable(Objects.requireNonNull(getter4).apply(value4));
                    }
                }
            }
        }
    }

    public static <T1, T2, T3, T4> String toStringOrElse(final T1 value,
                                                         final Function<T1, T2> getter1,
                                                         final Function<T2, T3> getter2,
                                                         final Function<T3, T4> getter3,
                                                         final Function<T4, Object> getter4,
                                                         final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return handleNull(otherSupplier);
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return handleNull(otherSupplier);
                    } else {
                        final Object value5 = Objects.requireNonNull(getter4).apply(value4);
                        return convertToString(value5, otherSupplier);
                    }
                }
            }
        }
    }

    private static <T> T handleNull(final Supplier<T> otherSupplier) {
        if (otherSupplier != null) {
            return otherSupplier.get();
        } else {
            return null;
        }
    }

    private static String convertToString(final Object value, final Supplier<String> otherSupplier) {
        if (value != null) {
            return value.toString();
        } else {
            if (otherSupplier != null) {
                return Objects.requireNonNull(otherSupplier).get();
            } else {
                return null;
            }
        }
    }

    private static String convertToString(final Object value, final String other) {
        if (value != null) {
            return value.toString();
        } else {
            return other;
        }
    }
}
