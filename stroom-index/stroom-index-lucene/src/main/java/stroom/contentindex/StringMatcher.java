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

package stroom.contentindex;

import stroom.explorer.shared.StringMatch;
import stroom.explorer.shared.StringMatch.MatchType;
import stroom.explorer.shared.StringMatchLocation;
import stroom.explorer.shared.TagsPatternParser;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringMatcher {

    /**
     * Types that will need the case of pattern to be normalised if caseSensitive is true.
     */
    private static final Set<MatchType> TYPES_NEEDING_CASE_CHANGE = EnumSet.of(
            MatchType.EQUALS,
            MatchType.NOT_EQUALS,
            MatchType.CONTAINS,
            MatchType.STARTS_WITH,
            MatchType.ENDS_WITH);

    private static final List<StringMatchLocation> LOCATION_ZERO = Collections.singletonList(
            StringMatchLocation.zero());

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
            pattern = new TagsPatternParser(stringMatch.getPattern()).getText();
            pattern = NullSafe.string(pattern);
            if (!caseSensitive && TYPES_NEEDING_CASE_CHANGE.contains(matchType)) {
                pattern = normaliseCase(pattern);
            }
//            else if (!pattern.isEmpty()) {
//                int flags = 0;
//                if (!caseSensitive) {
//                    flags = flags | Pattern.CASE_INSENSITIVE;
//                }
//                regexPattern = Pattern.compile(pattern, flags);
//            }
        }
    }

    public Optional<StringMatchLocation> match(final String string) {
        final List<StringMatchLocation> matches = match(string, 1);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    public List<StringMatchLocation> createZeroSingletonList(final boolean isMatch) {
        return isMatch
                ? LOCATION_ZERO
                : Collections.emptyList();
    }

    public List<StringMatchLocation> match(final String string, final int limit) {
        return switch (matchType) {
            case ANY -> Collections.singletonList(new StringMatchLocation(0, string.length()));
            case NULL -> createZeroSingletonList(string == null);
            case NON_NULL -> createZeroSingletonList(string != null);
            case BLANK -> createZeroSingletonList(string != null && string.isBlank());
            case NON_BLANK -> createZeroSingletonList(string != null && !string.isBlank());
            case EMPTY -> createZeroSingletonList(string != null && string.isEmpty());
            case NON_EMPTY -> createZeroSingletonList(string != null && !string.isEmpty());
            case NULL_OR_BLANK -> createZeroSingletonList(NullSafe.isBlankString(string));
            case NULL_OR_EMPTY -> createZeroSingletonList(NullSafe.isEmptyString(string));
            case CONTAINS -> contains(string, limit);
            case EQUALS -> equals(string);
            case NOT_EQUALS -> notEquals(string);
            case STARTS_WITH -> startsWith(string);
            case ENDS_WITH -> endsWith(string);
            case REGEX -> regex(string, limit);
            case CHARS_ANYWHERE -> charsAnywhere(string, limit);
        };
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
            return LOCATION_ZERO;
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
        final String alteredString = normaliseCaseIfRequired(string);
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
        final String alteredString = normaliseCaseIfRequired(string);
        if (alteredString.startsWith(pattern)) {
            return Collections.singletonList(new StringMatchLocation(0, pattern.length()));
        }
        return Collections.emptyList();
    }

    private List<StringMatchLocation> endsWith(final String string) {
        if (string == null) {
            return Collections.emptyList();
        }
        final String alteredString = normaliseCaseIfRequired(string);
        if (alteredString.endsWith(pattern)) {
            return Collections.singletonList(new StringMatchLocation(string.length() - pattern.length(),
                    pattern.length()));
        }
        return Collections.emptyList();
    }

    private String normaliseCaseIfRequired(final String string) {
        if (!caseSensitive) {
            return normaliseCase(string);
        }
        return string;
    }

    private String normaliseCase(final String string) {
        return string.toLowerCase(Locale.ROOT);
    }

    private List<StringMatchLocation> regex(final String string, final int limit) {
        if (string == null) {
            return Collections.emptyList();
        }
        // Compile the pattern on first use
        if (pattern != null) {
            if (regexPattern == null) {
                int flags = 0;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE;
                }
                regexPattern = Pattern.compile(pattern, flags);
            }

            final Matcher matcher = regexPattern.matcher(string);
            final List<StringMatchLocation> list = new ArrayList<>();
            for (int i = 0; i < limit && matcher.find(); i++) {
                list.add(new StringMatchLocation(matcher.start(), matcher.end() - matcher.start()));
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    private List<StringMatchLocation> charsAnywhere(final String string, final int limit) {
        if (string == null) {
            return Collections.emptyList();
        }
        // Compile the pattern on first use
        if (pattern != null) {
            if (regexPattern == null) {
                int flags = 0;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE;
                }
                // 'abc' -> 'a.*b.*c'
                final String effectivePattern = pattern.chars()
                        .mapToObj(i -> (char) i)
                        .map(chr -> Pattern.quote(String.valueOf(chr)))
                        .collect(Collectors.joining(".*"));

                regexPattern = Pattern.compile(".*" + effectivePattern + ".*", flags);
            }

            final Matcher matcher = regexPattern.matcher(string);
            final List<StringMatchLocation> list = new ArrayList<>();
            for (int i = 0; i < limit && matcher.find(); i++) {
                list.add(new StringMatchLocation(matcher.start(), matcher.end() - matcher.start()));
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    public MatchType getMatchType() {
        return matchType;
    }
}
