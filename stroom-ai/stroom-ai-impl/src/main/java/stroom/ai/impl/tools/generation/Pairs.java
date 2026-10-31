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

package stroom.ai.impl.tools.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Key/value pairs in a text field: the regex that reads one key's value, for each way a text writes its pairs.
///
/// A mapping's pairs entry ([KeyValuePairs]) names a text field, its style and its keys; it stands for an extract
/// entry a key, with these regexes (as the draft writes them, and as the XSLT folds them into one function called
/// with the key). Seen in testing: a Fortigate body's 60 keys as 60 extract entries were a third of a 50 KB mapping
/// the agent had to send whole.
public final class Pairs {

    // A key follows the text's start or a separator (; | ( ) ] or a spaced dash) and is one to four words before
    // ': ' (then its value, or [value]); or one word before '=' (value, or "value"). Seen in production: Secret
    // Server's audit text in a Windows event's Data, "... Event: [User] Action: [Login] By User:
    // domain.com\priya.patel (Item Id: 219); Source IP: 192.0.2.45", its pairs parted by ;, | or ' - ' from record
    // to record.
    /// Where a colon or bracketed key starts: the text's start or a separator, then spaces.
    public static final String BOUND = "(?:^|[;|()\\]]|\\s-)\\s*";
    /// A key of one to four words.
    public static final String WORDS = "[A-Za-z][A-Za-z0-9_-]*(?: [A-Za-z][A-Za-z0-9_-]*){0,3}";

    /// Each style's regex, with `{key}` where the key goes. The value runs to the next separator: a dash only where
    /// a key or a `[` follows it ("Secret: Firewall - Edge Admin;").
    public static final Map<String, String> PAIR_REGEX = pairRegexes();
    /// The styles, in order: `colon`, `bracketed`, `equals`, `quoted`.
    public static final List<String> STYLES = List.copyOf(PAIR_REGEX.keySet());

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_. -]*");

    private Pairs() {
        // Static only.
    }

    private static Map<String, String> pairRegexes() {
        final Map<String, String> regexes = new LinkedHashMap<>();
        regexes.put("colon", BOUND + "{key}: (.*?)(?:\\s*[;|()]|\\s+-\\s+(?:\\[|" + WORDS + ": )|\\s*$)");
        regexes.put("bracketed", BOUND + "{key}: \\[([^\\]]*)\\]");
        regexes.put("equals", "(?:^|[\\s,;]){key}=([^\\s,;\"]*)");
        regexes.put("quoted", "(?:^|[\\s,;]){key}=\"([^\"]*)\"");
        return Collections.unmodifiableMap(regexes);
    }

    /// @param style One of [#STYLES].
    /// @param key   A key, as the text writes it.
    /// @return The regex reading the key's value, its first group the value.
    public static String pairRegex(final String style, final String key) {
        return PAIR_REGEX.get(style).replace("{key}", key.replace(".", "\\."));
    }

    /// @param key A key, as the text writes it.
    /// @return The field its value is, by default: the key in snake case (`Source IP` -> `source_ip`).
    public static String keyName(final String key) {
        final String joined = NOT_ALPHANUMERIC.matcher(key).replaceAll("_");
        return strip(joined, '_').toLowerCase(Locale.ROOT);
    }

    /// @param regex An extract entry's regex.
    /// @return The style and key it reads, if [#pairRegex(String, String)] writes it.
    public static Optional<StyleAndKey> pairOf(final String regex) {
        for (final Map.Entry<String, String> entry : PAIR_REGEX.entrySet()) {
            final String template = entry.getValue();
            final int at = template.indexOf("{key}");
            final String before = template.substring(0, at);
            final String after = template.substring(at + "{key}".length());
            if (regex.startsWith(before) && regex.endsWith(after)
                && regex.length() > before.length() + after.length()) {
                final String key = regex.substring(before.length(), regex.length() - after.length())
                        .replace("\\.", ".");
                if (pairRegex(entry.getKey(), key).equals(regex) && KEY.matcher(key).matches()) {
                    return Optional.of(new StyleAndKey(entry.getKey(), key));
                }
            }
        }
        return Optional.empty();
    }

    /// @param text Text.
    /// @param ch   A character.
    /// @return The text without it at either end.
    static String strip(final String text, final char ch) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == ch) {
            start++;
        }
        while (end > start && text.charAt(end - 1) == ch) {
            end--;
        }
        return text.substring(start, end);
    }


    // --------------------------------------------------------------------------------


    /// What a pairs regex reads.
    ///
    /// @param style The style, one of [#STYLES].
    /// @param key   The key, as the text writes it.
    public record StyleAndKey(String style, String key) {

    }
}
