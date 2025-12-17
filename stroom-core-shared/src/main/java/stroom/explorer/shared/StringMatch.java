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

package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"matchType", "caseSensitive", "pattern"})
@JsonInclude(Include.NON_NULL)
public class StringMatch {

    @JsonProperty
    private final MatchType matchType;
    @JsonProperty
    private final boolean caseSensitive;
    @JsonProperty
    private final String pattern;

    public static StringMatch any() {
        return new StringMatch(MatchType.ANY, false, null);
    }

    public static StringMatch contains(final String pattern) {
        return contains(pattern, false);
    }

    public static StringMatch contains(final String pattern, final boolean caseSensitive) {
        if (pattern == null || pattern.isEmpty()) {
            return StringMatch.any();
        }
        return new StringMatch(MatchType.CONTAINS, caseSensitive, pattern);
    }

    public static StringMatch regex(final String pattern) {
        return regex(pattern, false);
    }

    public static StringMatch regex(final String pattern, final boolean caseSensitive) {
        return new StringMatch(MatchType.REGEX, caseSensitive, pattern);
    }

    @JsonCreator
    public StringMatch(@JsonProperty("matchType") final MatchType matchType,
                       @JsonProperty("caseSensitive") final boolean caseSensitive,
                       @JsonProperty("pattern") final String pattern) {
        this.matchType = matchType;
        this.caseSensitive = caseSensitive;
        this.pattern = pattern;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StringMatch)) {
            return false;
        }
        final StringMatch stringMatch = (StringMatch) o;
        return caseSensitive == stringMatch.caseSensitive &&
                matchType == stringMatch.matchType &&
                Objects.equals(pattern, stringMatch.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchType, caseSensitive, pattern);
    }


    // --------------------------------------------------------------------------------


    public enum MatchType {
        /**
         * Matches anything, equivalent to no filter.
         */
        ANY,
        /**
         * Is null
         */
        NULL,
        /**
         * Is not null, but could be {@link MatchType#EMPTY} or {@link MatchType#BLANK}
         */
        NON_NULL,
        /**
         * Non-null and contains whitespace only
         */
        BLANK,
        /**
         * Non-null and contains at least one non-whitespace character
         */
        NON_BLANK,
        /**
         * Non-null and empty
         */
        EMPTY,
        /**
         * Non-null and not empty, may be blank or non-blank
         */
        NON_EMPTY,
        /**
         * Null OR {@link MatchType#BLANK}
         */
        NULL_OR_BLANK,
        /**
         * Null OR {@link MatchType#EMPTY}
         */
        NULL_OR_EMPTY,
        /**
         * Contains the whole pattern somewhere in the string
         */
        CONTAINS,
        /**
         * String equals the whole pattern
         */
        EQUALS,
        /**
         * String does not equal the whole pattern
         */
        NOT_EQUALS,
        /**
         * String starts with the whole pattern
         */
        STARTS_WITH,
        /**
         * String ends with the whole pattern
         */
        ENDS_WITH,
        /**
         * String matches the regex
         */
        REGEX,
        /**
         * All characters in pattern appearing anywhere in the string in the order they appear
         * in pattern.
         */
        CHARS_ANYWHERE,
        ;
    }
}
