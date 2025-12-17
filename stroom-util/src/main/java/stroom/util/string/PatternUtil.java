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

package stroom.util.string;

import stroom.util.PredicateUtil;
import stroom.util.shared.NullSafe;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PatternUtil {

    public static final String STROOM_WILD_CARD_CHAR = "*";
    public static final Pattern STROOM_WILD_CARD_CHAR_PATTERN = Pattern.compile(
            Pattern.quote(STROOM_WILD_CARD_CHAR));
    private static final String WILD_CARD_REGEX = ".*";

    private PatternUtil() {
    }

    public static boolean containsWildCards(final String filter) {
        return NullSafe.test(filter, str -> str.contains(STROOM_WILD_CARD_CHAR));
    }

    /**
     * Creates a regex {@link Pattern} from the supplied filter string that may include
     * '*' as a 0-many char whild card. E.g. 'Jan*ry`. Any regex meta/escape chars are
     * escaped. Escaping the '*' is not supported. Pattern is case-sensitive and is for
     * a complete match.
     */
    public static Pattern createPatternFromWildCardFilter(final String filter,
                                                          final boolean isCompleteMatch) {
        return createPatternFromWildCardFilter(filter, isCompleteMatch, true);
    }

    /**
     * Creates a regex {@link Pattern} from the supplied filter string that may include
     * '*' as a 0-many char whild card. E.g. 'Jan*ry`. Any regex meta/escape chars are
     * escaped. Escaping the '*' is not supported. Pattern is for a complete match.
     *
     * @param isCaseSensitive False if case is to be ignored.
     */
    public static Pattern createPatternFromWildCardFilter(final String filter,
                                                          final boolean isCompleteMatch,
                                                          final boolean isCaseSensitive) {
        Objects.requireNonNull(filter, "filter not supplied");
        final String[] parts = STROOM_WILD_CARD_CHAR_PATTERN.split(filter);
        final StringBuilder patternStringBuilder = new StringBuilder();

        if (isCompleteMatch) {
            patternStringBuilder.append("^");
        }
        if (filter.startsWith(STROOM_WILD_CARD_CHAR)) {
            patternStringBuilder.append(WILD_CARD_REGEX);
        }
        int addedPartsCount = 0;
        for (final String part : parts) {
            if (!part.isEmpty()) {
                final String quotedPart = Pattern.quote(part);
                if (addedPartsCount != 0) {
                    patternStringBuilder.append(WILD_CARD_REGEX);
                }
                patternStringBuilder.append(quotedPart);
                addedPartsCount++;
            }
        }
        if (filter.endsWith(STROOM_WILD_CARD_CHAR)) {
            patternStringBuilder.append(WILD_CARD_REGEX);
        }
        if (isCompleteMatch) {
            patternStringBuilder.append("$");
        }
        final String patternStr = patternStringBuilder.toString();
        return isCaseSensitive
                ? Pattern.compile(patternStr)
                : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
    }

    public static <T> Predicate<T> createPredicate(final List<String> nameFilters,
                                                   final Function<T, String> toStringFunc,
                                                   final boolean allowWildCards,
                                                   final boolean isCompleteMatch,
                                                   final boolean isCaseSensitive) {

        if (NullSafe.isEmptyCollection(nameFilters)) {
            return t -> false;
        } else {
            Objects.requireNonNull(toStringFunc);
            return nameFilters.stream()
                    .map(nameFilter -> {
                        final Predicate<T> predicate;
                        if (allowWildCards && PatternUtil.containsWildCards(nameFilter)) {
                            final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                                    nameFilter, isCompleteMatch, isCaseSensitive);
                            predicate = item ->
                                    pattern.matcher(NullSafe.get(item, toStringFunc)).matches();
                        } else {
                            if (isCompleteMatch) {
                                if (isCaseSensitive) {
                                    predicate = item ->
                                            nameFilter.equals(NullSafe.get(item, toStringFunc));
                                } else {
                                    predicate = item ->
                                            nameFilter.equalsIgnoreCase(NullSafe.get(item, toStringFunc));
                                }
                            } else {
                                if (isCaseSensitive) {
                                    predicate = item ->
                                            NullSafe.test(item, toStringFunc, str -> str.contains(nameFilter));
                                } else {
                                    predicate = item ->
                                            NullSafe.test(item,
                                                    toStringFunc,
                                                    str -> StringUtils.containsIgnoreCase(str, nameFilter));
                                }
                            }
                        }
                        return predicate;
                    })
                    .reduce(PredicateUtil::orPredicates)
                    .orElse(val -> false);
        }
    }

    /**
     * Convert a Stroom wild carded string into a string suitable for use in SQL LIKE commands.
     * SQL wild card chars '%' and '_' will be escaped by prefixing with a '\'. All instances
     * of '*' will be replaced with '%'. Escaping the '*' is not supported. The return value should
     * be used in a bind variable to remove the risk of SQL injection.
     */
    public static String createSqlLikeStringFromWildCardFilter(final String filter) {
        Objects.requireNonNull(filter);
        if (filter.isBlank()) {
            return filter;
        } else {
            return filter
                    .replace("%", "\\%") // escape sql multi-char wild card
                    .replace("_", "\\_") // escape sql single-char wild card
                    .replace(STROOM_WILD_CARD_CHAR, "%"); // now replace stroom wild cards with SQL equivalent
        }
    }
}
