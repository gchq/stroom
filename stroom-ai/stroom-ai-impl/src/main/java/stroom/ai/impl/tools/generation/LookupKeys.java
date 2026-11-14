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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The one definition of a reference data key made of several fields (`key_fields`), written by both generators: the
/// reference generator's loader ([ReferenceGenerator], a map's `key_fields`) and the translation generator's lookup
/// ([XsltGenerator], a lookup's `key_fields`), so the key a loader stores and the key a lookup asks for can't drift
/// apart.
///
/// A composite key is the fields' values in order, each stripped of leading and trailing white space (and inner runs
/// of it made one space, as `normalize-space()` does), joined by [#SEPARATOR]. One field is a plain key, as `field`
/// (or a map's `key`) writes it: the field's first value as it is.
public final class LookupKeys {

    /// What goes between a composite key's values: U+241F SYMBOL FOR UNIT SEPARATOR. Printable (so it is allowed in
    /// XML 1.0, where the control character U+001F is not, and the reference data XML holds keys), and not a
    /// character data holds.
    public static final String SEPARATOR = "␟";
    /// The most fields a composite key is made of.
    public static final int MAX_FIELDS = 3;

    // The separator as the generated XPath writes it: by code point, so the XSLT stays ASCII.
    private static final String SEPARATOR_TEXT = "codepoints-to-string(" + SEPARATOR.codePointAt(0) + ")";
    // A composite key as keyText writes it, its parts counted by partCount.
    private static final Pattern COMPOSITE = Pattern.compile("^string-join\\(\\((.*)\\), "
                                                             + Pattern.quote(SEPARATOR_TEXT) + "\\)$",
            Pattern.DOTALL);
    private static final Pattern PART = Pattern.compile("normalize-space\\(string\\(\\(");

    private LookupKeys() {
        // Static only.
    }

    /// @param sources Each key field's selector, in order (at least one).
    /// @return The key's expression: one field's first value as a string, or several fields' values stripped and
    /// joined by [#SEPARATOR].
    public static String keyText(final List<String> sources) {
        if (sources.size() == 1) {
            return XPathText.keyText(sources.getFirst());
        }
        return "string-join((" + sources.stream()
                .map(source -> "normalize-space(" + XPathText.keyText(source) + ")")
                .collect(Collectors.joining(", ")) + "), " + SEPARATOR_TEXT + ")";
    }

    /// @return The separator as the generated XPath writes it.
    public static String separatorText() {
        return SEPARATOR_TEXT;
    }

    /// @param values Each key field's value, in order.
    /// @return The key the generated XPath makes of them, for a check made outside the XSLT (the agent's proposal of
    /// a context lookup): one value as it is, several stripped and joined.
    public static String key(final List<String> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        return values.stream()
                .map(value -> value == null
                        ? ""
                        : value.strip().replaceAll("\\s+", " "))
                .collect(Collectors.joining(SEPARATOR));
    }

    /// @param keyExpression A reference loader's key expression, as its XSLT writes it.
    /// @return How many fields the key is made of, as [#keyText] wrote it: 1 for any other expression.
    public static int partCount(final String keyExpression) {
        if (keyExpression == null) {
            return 1;
        }
        final Matcher composite = COMPOSITE.matcher(keyExpression.strip());
        if (!composite.matches()) {
            return 1;
        }
        final Matcher parts = PART.matcher(composite.group(1));
        int count = 0;
        while (parts.find()) {
            count++;
        }
        return Math.max(1, count);
    }

    /// @param keyFields A model's `key_fields`, or null.
    /// @param owner     What gives them, for the message, e.g. `lookup 'EVENT_INFO'`.
    /// @return Why they are wrong, or null: one to [#MAX_FIELDS] fields, none blank.
    static String problem(final List<String> keyFields, final String owner) {
        if (keyFields == null) {
            return null;
        }
        if (keyFields.isEmpty() || keyFields.size() > MAX_FIELDS
            || keyFields.stream().anyMatch(field -> field == null || field.isBlank())) {
            return owner + ": key_fields lists the fields whose values make the key, in order: 1 to " + MAX_FIELDS
                   + " of them, none blank";
        }
        return null;
    }
}
