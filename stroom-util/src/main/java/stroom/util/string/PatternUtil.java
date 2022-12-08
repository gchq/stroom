package stroom.util.string;

import stroom.util.NullSafe;

import java.util.Objects;
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
