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

package stroom.ai.impl.tools;

import java.util.List;
import java.util.regex.Pattern;

/// Turns a pipeline error message into a pattern, so the same problem met in different records, with different
/// values, collapses into one failure mode, while different problems stay apart.
///
/// The parts of a message that vary from record to record are replaced with placeholders, in this order: URLs
/// (`<url>`), UUIDs (`<uuid>`), dates and times (`<time>`), IP addresses (`<ip>`), quoted values (`"<value>"` or
/// `'<value>'`), file paths (`<path>`), long hexadecimal ids (`<hex>`) and numbers standing on their own (`<n>`).
/// Words with digits in them, such as the error code `XTDE0640` or the element `xsltFilter2`, are kept, as they
/// name the problem rather than vary with the data. Runs of white space become one space.
final class ErrorMessageNormaliser {

    /// The longest pattern returned; longer ones are cut short, as their start says what the problem is.
    static final int MAX_PATTERN_CHARS = 300;

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\b[A-Za-z][A-Za-z0-9+.-]*://\\S+"), "<url>"),
            new Rule(Pattern.compile(
                    "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"), "<uuid>"),
            new Rule(Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}(?::\\d{2}(?:[.,]\\d+)?)?"
                                     + "(?:Z|[+-]\\d{2}:?\\d{2})?)?(?![\\p{L}\\d])"), "<time>"),
            new Rule(Pattern.compile("(?<![\\p{L}\\d_])\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?![\\p{L}\\d_])"),
                    "<time>"),
            new Rule(Pattern.compile("(?<![\\d.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\d.])"), "<ip>"),
            new Rule(Pattern.compile("\"[^\"]*\""), "\"<value>\""),
            // Not the apostrophe in a word such as "don't".
            new Rule(Pattern.compile("(?<![\\p{L}\\d])'[^']*'(?![\\p{L}\\d])"), "'<value>'"),
            new Rule(Pattern.compile("(?<![\\p{L}\\d<>])(?:[A-Za-z]:)?(?:[/\\\\][\\w.$~-]+){2,}[/\\\\]?"),
                    "<path>"),
            new Rule(Pattern.compile("\\b0x[0-9a-fA-F]+\\b"), "<hex>"),
            new Rule(Pattern.compile("\\b(?=[0-9a-fA-F]*\\d)(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{8,}\\b"), "<hex>"),
            new Rule(Pattern.compile("(?<![\\p{L}\\d_])[-+]?\\d+(?:[.,]\\d+)*(?![\\p{L}\\d_])"), "<n>"));
    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");

    private ErrorMessageNormaliser() {
        // Static methods only.
    }

    /// @param message An error message, as a pipeline wrote it, or null.
    /// @return Its pattern, with the values that vary replaced by placeholders, or an empty string for a null or
    /// blank message.
    static String normalise(final String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String pattern = message;
        for (final Rule rule : RULES) {
            pattern = rule.pattern().matcher(pattern).replaceAll(rule.replacement());
        }
        pattern = WHITE_SPACE.matcher(pattern).replaceAll(" ").strip();
        return pattern.length() > MAX_PATTERN_CHARS
                ? pattern.substring(0, MAX_PATTERN_CHARS) + "…"
                : pattern;
    }


    // --------------------------------------------------------------------------------


    /// One kind of varying value and its placeholder.
    ///
    /// @param pattern     Matches the value.
    /// @param replacement Its placeholder, quoted for [java.util.regex.Matcher#replaceAll(String)].
    private record Rule(Pattern pattern, String replacement) {

    }
}
