package stroom.util.string;

import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.docref.StringMatchLocation;
import stroom.util.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            if (MatchType.EQUALS.equals(matchType) ||
                    MatchType.NOT_EQUALS.equals(matchType) ||
                    MatchType.CONTAINS.equals(matchType)) {
                if (!caseSensitive) {
                    pattern = pattern.toLowerCase(Locale.ROOT);
                }
            } else if (!pattern.isEmpty()) {
                int flags = 0;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE;
                }
                regexPattern = Pattern.compile(pattern, flags);
            }
        }
    }

    public Optional<StringMatchLocation> match(final String string) {
        final List<StringMatchLocation> matches = match(string, 1);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    public List<StringMatchLocation> match(final String string, final int limit) {
        switch (matchType) {
            case ANY -> {
                return Collections.singletonList(new StringMatchLocation(0, string.length()));
            }
            case NULL -> {
                if (string == null) {
                    return Collections.singletonList(new StringMatchLocation(0, 0));
                }
            }
            case NON_NULL -> {
                if (string != null && string.isBlank()) {
                    return Collections.singletonList(new StringMatchLocation(0, 0));
                }
            }
            case EMPTY -> {
                if (string != null && string.isEmpty()) {
                    return Collections.singletonList(new StringMatchLocation(0, 0));
                }
            }
            case NULL_OR_BLANK -> {
                if (NullSafe.isBlankString(string)) {
                    return Collections.singletonList(new StringMatchLocation(0, 0));
                }
            }
            case NULL_OR_EMPTY -> {
                if (NullSafe.isEmptyString(string)) {
                    return Collections.singletonList(new StringMatchLocation(0, 0));
                }
            }
            case CONTAINS -> {
                return contains(string, limit);
            }
            case EQUALS -> {
                return equals(string);
            }
            case NOT_EQUALS -> {
                return notEquals(string);
            }
            case STARTS_WITH -> {
                return startsWith(string);
            }
            case ENDS_WITH -> {
                return endsWith(string);
            }
            case REGEX -> {
                return regex(string, limit);
            }
        }
        return Collections.emptyList();
    }

    private List<StringMatchLocation> equals(final String string) {
        if (string == null) {
            return Collections.emptyList();
        }
        if (caseSensitive) {
            if (string.equals(pattern)) {
                return Collections.singletonList(new StringMatchLocation(0, string.length()));
            }
        } else {
            if (string.equalsIgnoreCase(pattern)) {
                return Collections.singletonList(new StringMatchLocation(0, string.length()));
            }
        }
        return Collections.emptyList();
    }

    private List<StringMatchLocation> notEquals(final String string) {
        if (string == null) {
            return Collections.singletonList(new StringMatchLocation(0, 0));
        }
        if (caseSensitive) {
            if (!string.equals(pattern)) {
                return Collections.singletonList(new StringMatchLocation(0, string.length()));
            }
        } else {
            if (!string.equalsIgnoreCase(pattern)) {
                return Collections.singletonList(new StringMatchLocation(0, string.length()));
            }
        }
        return Collections.emptyList();
    }

    private List<StringMatchLocation> contains(final String string, final int limit) {
        if (string == null) {
            return Collections.emptyList();
        }
        final String alteredString = changeCase(string);
        int index = alteredString.indexOf(pattern);
        final List<StringMatchLocation> matches = new ArrayList<>();
        for (int i = 0; i < limit && index != -1; i++) {
            matches.add(new StringMatchLocation(index, pattern.length()));
            index = alteredString.indexOf(pattern, index + pattern.length());
        }
        return matches;
    }

    private List<StringMatchLocation> startsWith(final String string) {
        if (string == null) {
            return Collections.emptyList();
        }
        final String alteredString = changeCase(string);
        if (alteredString.startsWith(pattern)) {
            return Collections.singletonList(new StringMatchLocation(0, pattern.length()));
        }
        return Collections.emptyList();
    }

    private List<StringMatchLocation> endsWith(final String string) {
        if (string == null) {
            return Collections.emptyList();
        }
        final String alteredString = changeCase(string);
        if (alteredString.endsWith(pattern)) {
            return Collections.singletonList(new StringMatchLocation(string.length() - pattern.length(),
                    pattern.length()));
        }
        return Collections.emptyList();
    }

    private String changeCase(final String string) {
        if (!caseSensitive) {
            return string.toLowerCase(Locale.ROOT);
        }
        return string;
    }

    private List<StringMatchLocation> regex(final String string, final int limit) {
        if (string == null) {
            return Collections.emptyList();
        }
        if (regexPattern != null) {
            int flags = 0;
            if (!caseSensitive) {
                flags = flags | Pattern.CASE_INSENSITIVE;
            }
            final Pattern regexPattern = Pattern.compile(pattern, flags);
            final Matcher matcher = regexPattern.matcher(string);
            final List<StringMatchLocation> list = new ArrayList<>();
            for (int i = 0; i < limit && matcher.find(); i++) {
                list.add(new StringMatchLocation(matcher.start(), matcher.end() - matcher.start()));
            }
            return list;
        }
        return Collections.emptyList();
    }

    public MatchType getMatchType() {
        return matchType;
    }
}
