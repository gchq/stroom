/*
 * Copyright 2026 Crown Copyright
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
import stroom.explorer.shared.TagsPatternParser;

import org.apache.lucene.util.automaton.RegExp;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Checks that a find in content regex is one that both the index (Lucene) and the highlighter
/// (Java) can use, so that a mistake in it can be reported to the user rather than just matching
/// nothing.
final class RegexPatternValidator {

    static final String INVALID_REGEX_MESSAGE_PREFIX = "Invalid regular expression: ";

    private RegexPatternValidator() {
        // Static utility
    }

    /// Checks the regex of a find in content filter. Filters that aren't regexes are not checked.
    ///
    /// @param stringMatch The filter, or null for none.
    /// @throws IllegalArgumentException If the filter is a regex that is invalid, saying why.
    static void validate(final StringMatch stringMatch) {
        if (stringMatch == null || !MatchType.REGEX.equals(stringMatch.getMatchType())) {
            return;
        }
        // Tags (tag:xxx) aren't part of the regex
        final String regex = new TagsPatternParser(stringMatch.getPattern()).getText();
        try {
            // As the index matches it, against the whole of the content (see LuceneContentIndex)
            new RegExp(".*" + regex + ".*");
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(INVALID_REGEX_MESSAGE_PREFIX + e.getMessage(), e);
        }
        try {
            // As the highlighter matches it (see StringMatcher)
            Pattern.compile(regex);
        } catch (final PatternSyntaxException e) {
            throw new IllegalArgumentException(INVALID_REGEX_MESSAGE_PREFIX + e.getDescription(), e);
        }
    }
}
