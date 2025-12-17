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

package stroom.util;

import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PredicateUtil {

    private static final String LIST_DELIMITER = ",";
    private static final Pattern LIST_DELIMITER_PATTERN = Pattern.compile(LIST_DELIMITER);

    private PredicateUtil() {
        // Static utils only
    }

    public static Predicate<String> caseSensitiveContainsPredicate(final String filter) {
        if (filter == null) {
            return str -> false;
        } else {
            return str ->
                    NullSafe.contains(str, filter);
        }
    }

    public static Predicate<String> caseInsensitiveContainsPredicate(final String filter) {
        if (filter == null) {
            return str -> false;
        } else {
            final String lowerFilter = filter.toLowerCase();
            return str -> {
                if (str == null) {
                    return false;
                } else {
                    return str.toLowerCase().contains(lowerFilter);
                }
            };
        }
    }

    /**
     * Creates a {@link Predicate<String>} for matching values using the supplied filter {@link String}
     */
    public static Predicate<String> createWildCardedFilterPredicate(final String filter,
                                                                    final boolean isCompleteMatch,
                                                                    final boolean isCaseSensitive) {
        Objects.requireNonNull(filter, "filter not supplied");
        if (filter.isEmpty()) {
            return str -> false;
        } else if (PatternUtil.containsWildCards(filter)) {
            final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                    filter, isCompleteMatch, isCaseSensitive);
            return isCompleteMatch
                    ? pattern.asMatchPredicate()
                    : pattern.asPredicate();
        } else {
            if (isCompleteMatch) {
                return isCaseSensitive
                        ? filter::equals
                        : filter::equalsIgnoreCase;
            } else {
                return isCaseSensitive
                        ? PredicateUtil.caseSensitiveContainsPredicate(filter)
                        : PredicateUtil.caseInsensitiveContainsPredicate(filter);
            }
        }
    }

    /**
     * Create a predicate ORing all the filter terms in inListStr together. Supports standard stroom
     * wild carding with '*'.  Matches are complete matches.
     *
     * @param inListStr List of filter terms delimited by LIST_DELIMITER
     */
    public static Predicate<String> createWildCardedInPredicate(final String inListStr,
                                                                final boolean isCaseSensitive) {
        if (inListStr == null) {
            return str -> false;
        } else {
            final String value = inListStr.trim();
            if (inListStr.isEmpty()) {
                return str -> false;
            } else {
                if (!inListStr.contains(LIST_DELIMITER)) {
                    return createWildCardedFilterPredicate(inListStr, true, isCaseSensitive);
                } else {
                    final String[] parts = LIST_DELIMITER_PATTERN.split(value);
                    return createWildCardedInPredicate(parts, isCaseSensitive);
                }
            }
        }
    }

    /**
     * Create a predicate ORing all the filter terms in filters together. Supports standard stroom
     * wild carding with '*'.  Matches are complete matches.
     */
    public static Predicate<String> createWildCardedInPredicate(final String[] filters,
                                                                final boolean isCaseSensitive) {
        if (filters == null || filters.length == 0) {
            return str -> false;
        } else {
            final List<Predicate<String>> predicates = Arrays.stream(filters)
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(part -> createWildCardedFilterPredicate(part, true, isCaseSensitive))
                    .collect(Collectors.toList());
            return orPredicates(predicates, str -> false);
        }
    }

    /**
     * Combined the passed predicates together using an OR.
     * If there are no non-null predicates then the returned predicate is an always true one.
     */
    public static <T> Predicate<T> andPredicates(final List<Predicate<T>> predicates,
                                                 final Predicate<T> fallBack) {
        final int count = NullSafe.size(predicates);
        Predicate<T> combinedPredicate = null;
        if (count == 1) {
            combinedPredicate = predicates.getFirst();
        } else if (count == 2) {
            combinedPredicate = andPredicates(predicates.get(0), predicates.get(1));
        } else if (count > 2) {
            combinedPredicate = predicates.stream()
                    .reduce(PredicateUtil::andPredicates)
                    .orElse(obj -> true); // No predicates so return everything
        }
        return Objects.requireNonNullElse(combinedPredicate, fallBack);
    }

    /**
     * Combined the passed predicates together using an OR
     * If there are no non-null predicates then the returned predicate is an always true one.
     */
    public static <T> Predicate<T> orPredicates(final List<Predicate<T>> predicates,
                                                final Predicate<T> fallBack) {
        final int count = NullSafe.size(predicates);
        Predicate<T> combinedPredicate = null;
        if (count == 1) {
            combinedPredicate = predicates.getFirst();
        } else if (count == 2) {
            combinedPredicate = orPredicates(predicates.get(0), predicates.get(1));
        } else if (count > 2) {
            combinedPredicate = predicates.stream()
                    .reduce(PredicateUtil::orPredicates)
                    .orElse(obj -> true); // No predicates so return everything
        }
        return Objects.requireNonNullElse(combinedPredicate, fallBack);
    }

    /**
     * Chains predicates together using an AND. predicate2 will be tested after predicate1.
     *
     * @return A single predicate representing predicate1 AND predicate2, or if predicate1
     * and predicate2 are null
     */
    public static <T> Predicate<T> andPredicates(final Predicate<T> predicate1,
                                                 final Predicate<T> predicate2) {
        if (predicate1 == null && predicate2 == null) {
            return null;
        } else {
            if (predicate1 == null) {
                return predicate2;
            } else if (predicate2 == null) {
                return predicate1;
            } else {
                return predicate1.and(predicate2);
            }
        }
    }

    /**
     * Chains predicates together using an OR. predicate2 will be tested after predicate1.
     *
     * @return A single predicate representing predicate1 OR predicate2.
     */
    public static <T> Predicate<T> orPredicates(final Predicate<T> predicate1,
                                                final Predicate<T> predicate2) {
        if (predicate1 == null && predicate2 == null) {
            return null;
        } else {
            if (predicate1 == null) {
                return predicate2;
            } else if (predicate2 == null) {
                return predicate1;
            } else {
                return predicate1.or(predicate2);
            }
        }
    }

    public static <T> Predicate<T> get(final Optional<Predicate<T>> optionalPredicate) {
        return optionalPredicate.orElse(x -> true);
    }

    /**
     * Creates a predicate that increments counter for each match encountered.
     *
     * @param counter   The counter to increment. It is the caller's responsibility to zero it as needed.
     * @param predicate The predicate to wrap
     * @return The wrapped predicate
     */
    public static <T> Predicate<T> countingPredicate(final LongAdder counter,
                                                     final Predicate<T> predicate) {
        return countingPredicate(counter, true, predicate);
    }

    /**
     * Creates a predicate that increments counter for each match encountered.
     *
     * @param counter     The counter to increment. It is the caller's responsibility to zero it as needed.
     * @param countIfTrue If true counter is incremented for a match. If false, counter is incremented for
     *                    a non-match.
     * @param predicate   The predicate to wrap
     * @return The wrapped predicate
     */
    public static <T> Predicate<T> countingPredicate(final LongAdder counter,
                                                     final boolean countIfTrue,
                                                     final Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(counter);
        return val -> {
            final boolean result = predicate.test(val);
            if (countIfTrue) {
                if (result) {
                    counter.increment();
                }
            } else {
                if (!result) {
                    counter.increment();
                }
            }
            return result;
        };
    }

    /**
     * Creates a {@link BiPredicate} that increments counter for each match encountered.
     *
     * @param counter   The counter to increment. It is the caller's responsibility to zero it as needed.
     * @param predicate The {@link BiPredicate} to wrap
     * @return The wrapped predicate
     */
    public static <T1, T2> BiPredicate<T1, T2> countingBiPredicate(final LongAdder counter,
                                                                   final BiPredicate<T1, T2> predicate) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(counter);
        return (t1, t2) -> {
            final boolean result = predicate.test(t1, t2);
            if (result) {
                counter.increment();
            }
            return result;
        };
    }
}
