package stroom.util.string;

import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.util.NullSafe;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringMatcher {

    private final MatchType matchType;
    private final boolean caseSensitive;
    private String pattern;
    private Pattern regexPattern;

    public StringMatcher(final StringMatch stringMatch) {
        if (stringMatch == null) {
            matchType = MatchType.ANY;
            caseSensitive = false;
        } else {
            matchType = stringMatch.getMatchType();
            caseSensitive = stringMatch.isCaseSensitive();
            pattern = stringMatch.getPattern();
            pattern = pattern == null
                    ? ""
                    : pattern;
            if (MatchType.EQUALS.equals(matchType)) {
                if (!caseSensitive) {
                    pattern = pattern.toLowerCase(Locale.ROOT);
                }
            } else if (MatchType.CONTAINS.equals(matchType)) {
                if (!caseSensitive) {
                    pattern = pattern.toLowerCase(Locale.ROOT);
                }
            } else if (pattern.length() > 0) {
                int flags = 0;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE;
                }
                regexPattern = Pattern.compile(pattern, flags);
            }
        }
    }

    public Optional<Match> match(final String string) {
        switch (matchType) {
            case ANY -> {
                return Optional.of(new Match(0, string.length()));
            }
            case NULL -> {
                if (string == null) {
                    return Optional.of(new Match(0, 0));
                }
            }
            case NON_NULL -> {
                if (string != null && string.isBlank()) {
                    return Optional.of(new Match(0, 0));
                }
            }
            case EMPTY -> {
                if (string != null && string.isEmpty()) {
                    return Optional.of(new Match(0, 0));
                }
            }
            case NULL_OR_BLANK -> {
                if (NullSafe.isBlankString(string)) {
                    return Optional.of(new Match(0, 0));
                }
            }
            case NULL_OR_EMPTY -> {
                if (NullSafe.isEmptyString(string)) {
                    return Optional.of(new Match(0, 0));
                }
            }
            case CONTAINS -> {
                return contains(string);
            }
            case EQUALS -> {
                return equals(string);
            }
            case REGEX -> {
                return regex(string);
            }
        }
        return Optional.empty();
    }

    private Optional<Match> equals(final String string) {
        if (string == null) {
            return Optional.empty();
        }
        if (caseSensitive) {
            if (string.equals(pattern)) {
                return Optional.of(new Match(0, string.length()));
            }
        } else {
            if (string.equalsIgnoreCase(pattern)) {
                return Optional.of(new Match(0, string.length()));
            }
        }
        return Optional.empty();
    }

    private Optional<Match> contains(final String string) {
        if (string == null) {
            return Optional.empty();
        }
        if (caseSensitive) {
            final int index = string.indexOf(pattern);
            if (index != -1) {
                return Optional.of(new Match(index, pattern.length()));
            }
        } else {
            final int index = string.toLowerCase(Locale.ROOT).indexOf(pattern);
            if (index != -1) {
                return Optional.of(new Match(index, pattern.length()));
            }
        }
        return Optional.empty();
    }

    private Optional<Match> regex(final String string) {
        if (string == null) {
            return Optional.empty();
        }
        if (regexPattern != null) {
            int flags = 0;
            if (!caseSensitive) {
                flags = flags | Pattern.CASE_INSENSITIVE;
            }
            final Pattern regexPattern = Pattern.compile(pattern, flags);
            final Matcher matcher = regexPattern.matcher(string);
            if (matcher.find()) {
                return Optional.of(new Match(matcher.start(), matcher.end() - matcher.start()));
            }
        }
        return Optional.empty();
    }

    public record Match(int offset, int length) {

    }
}
