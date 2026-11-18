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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Names in an [XsltStyle]'s naming (variables, templates, modes, Data Names), as the generator makes them.
public final class XsltNames {

    private static final Pattern WORDS = Pattern.compile("[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|\\d+",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String, Pattern> IN_STYLE = Map.of(
            "snake_case", Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*"),
            "camelCase", Pattern.compile("[a-z][a-z0-9]*([A-Z][a-z0-9]*)*"),
            "PascalCase", Pattern.compile("[A-Z][A-Za-z0-9]*"),
            "kebab-case", Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*"));
    private static final int MAX_NUMBER = 1000;

    private XsltNames() {
        // Static only.
    }

    /// @param text   A field name, a path such as `Authenticate-User`, or `src_ip`.
    /// @param naming The naming: `snake_case`, `camelCase`, `PascalCase` or `kebab-case`.
    /// @return The text in the naming, as an XML name: `EventSource` is `event_source`, `eventSource`,
    /// `EventSource` or `event-source`; one starting with a digit gets a `_` before it.
    public static String styleName(final String text, final String naming) {
        final List<String> words = new ArrayList<>();
        final Matcher matcher = WORDS.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        if (words.isEmpty()) {
            words.add("field");
        }
        final String name;
        if ("camelCase".equals(naming)) {
            final StringBuilder sb = new StringBuilder(words.getFirst());
            words.subList(1, words.size()).forEach(word -> sb.append(capitalise(word)));
            name = sb.toString();
        } else if ("PascalCase".equals(naming)) {
            final StringBuilder sb = new StringBuilder();
            words.forEach(word -> sb.append(capitalise(word)));
            name = sb.toString();
        } else {
            name = String.join("kebab-case".equals(naming)
                    ? "-"
                    : "_", words);
        }
        return Character.isDigit(name.codePointAt(0))
                ? "_" + name
                : name;
    }

    /// @param base   A name.
    /// @param taken  The names in use.
    /// @param naming The naming.
    /// @return The name, or it with a number (`event_source_2`, `eventSource2`) when it is taken.
    public static String uniqueName(final String base, final Collection<String> taken, final String naming) {
        if (!taken.contains(base)) {
            return base;
        }
        final String separator = switch (naming) {
            case "snake_case" -> "_";
            case "kebab-case" -> "-";
            default -> "";
        };
        for (int n = 2; n < MAX_NUMBER; n++) {
            final String candidate = base + separator + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free name for " + base);
    }

    /// @param name   A Data element's Name.
    /// @param naming The mapping's `data_names` style, or `as_given`.
    /// @return The name in the style; a name already in it is kept (`IPAddress` stays, not `IpAddress`).
    public static String dataNameIn(final String name, final String naming) {
        if ("as_given".equals(naming) || IN_STYLE.get(naming).matcher(name).matches()) {
            return name;
        }
        return styleName(name, naming);
    }

    // A word of letters and digits capitalised: its first character upper case, the rest lower.
    private static String capitalise(final String word) {
        return word.isEmpty()
                ? word
                : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
