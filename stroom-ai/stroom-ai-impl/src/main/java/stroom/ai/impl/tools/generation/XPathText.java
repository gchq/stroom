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
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// How the translation generator writes each kind of XPath expression, one method each, as the Stroom MCP server's
/// generator writes them (`utils/xsltgen.py`). The server's XSLT reader (`utils/xsltread`) reads an XSLT back with
/// patterns made by calling these same functions with slots, so the reader follows whatever the generator writes
/// (asked for by the user: one definition, not two that drift apart).
public final class XPathText {

    /// A time format with digits below the millisecond (microseconds, nanoseconds), rounded half up to the
    /// event-logging schema's three digits.
    public static final Pattern SUB_MS = Pattern.compile("S{4,}");
    /// Epoch values other than milliseconds, to milliseconds: seconds (a fraction kept), microseconds and
    /// nanoseconds.
    public static final Map<String, String> EPOCH_SCALE = Map.of("epoch_s", "* 1000", "epoch_us", "div 1000",
            "epoch_ns", "div 1000000");
    /// The epoch time formats.
    public static final List<String> EPOCH_FORMATS = List.of("epoch_ms", "epoch_s", "epoch_us", "epoch_ns");
    /// Transforms worth a function of their own when repeated; lower, upper and trim are one XPath call already.
    public static final List<String> FUNCTION_TRANSFORMS = List.of("strip_domain", "domain", "digits");

    private XPathText() {
        // Static only.
    }

    /// @param text Text.
    /// @return It as an XPath string literal, a quote in it doubled: `'o''neil'`.
    public static String literal(final String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /// @param kind      The mapping's input: `data_splitter`, `json`, `xml` or `xml_fragments`.
    /// @param fieldName An input field.
    /// @return The field as the parser presents it: a Data Splitter's data element (its value), a JSON key (a dotted
    /// path of keys), or an XML path as given.
    public static String fieldText(final String kind, final String fieldName) {
        if ("data_splitter".equals(kind)) {
            return PyStr.split(fieldName, "/").stream()
                           .map(part -> "data[@name=" + literal(part) + "]")
                           .collect(Collectors.joining("/")) + "/@value";
        }
        if ("json".equals(kind)) {
            return PyStr.split(fieldName, ".").stream()
                    .map(part -> "*[@key=" + literal(part) + "]")
                    .collect(Collectors.joining("/"));
        }
        return fieldName;
    }

    /// @param source An input.
    /// @return A key for a lookup or a dictionary: the input's first value, as a string.
    public static String keyText(final String source) {
        return "string((" + source + ")[1])";
    }

    /// @param mapName A reference data map.
    /// @param key     The key's expression.
    /// @param path    The path below the value found, or null.
    /// @return A reference data lookup, and the path below the value it finds (its elements are in no namespace).
    public static String lookupText(final String mapName, final String key, final String path) {
        final String found = "stroom:lookup(" + literal(mapName) + ", " + key + ")";
        if (path == null || path.isEmpty()) {
            return found;
        }
        final String steps = PyStr.split(PyStr.stripSlashes(path), "/").stream()
                .filter(step -> !step.isEmpty())
                .map(step -> step.contains(":") || step.startsWith("@") || ".".equals(step) || "*".equals(step)
                        ? step
                        : "*:" + step)
                .collect(Collectors.joining("/"));
        return found + "//" + steps;
    }

    /// @param variable The stylesheet variable holding a dictionary.
    /// @param key      The key's expression.
    /// @return A value looked up in a dictionary of key=value lines.
    public static String dictionaryLookupText(final String variable, final String key) {
        return "$" + variable + "?(" + key + ")";
    }

    /// @param name A Dictionary doc's name.
    /// @param kind `map` for its key=value lines, or `set` for the list of its lines.
    /// @return The stylesheet variable's expression holding it.
    public static String dictionaryText(final String name, final String kind) {
        final String lines = "tokenize(stroom:dictionary(" + literal(name) + "), '\\r?\\n')";
        if ("map".equals(kind)) {
            return "map:merge(for $line in " + lines + "[contains(., '=')] return map{normalize-space("
                   + "substring-before($line, '=')): normalize-space(substring-after($line, '='))})";
        }
        return lines + " ! normalize-space(.)";
    }

    /// @param source A single-valued input where only whether it has a value matters (a test, a map key).
    /// @return The input, trimmed. Told from the trim transform by its `[1]`: that reads the input's first value,
    /// this the input itself.
    public static String hasValueText(final String source) {
        return "normalize-space(" + source + ")";
    }

    /// @param sources Inputs.
    /// @return The first of them with a value.
    public static String anyOfText(final List<String> sources) {
        return "(" + String.join(", ", sources) + ")[normalize-space(.)][1]";
    }

    /// @param mapReference The xsl:map variable, e.g. `$action_to_success`.
    /// @param key          The key's expression.
    /// @param defaultValue The default, or null.
    /// @return A value looked up in an xsl:map (an empty input gives no value, not an error), else the default.
    public static String mapLookupText(final String mapReference, final String key, final String defaultValue) {
        final String lookup = mapReference + "?(" + key + ")";
        return defaultValue != null
                ? "(" + lookup + ", " + literal(defaultValue) + ")[1]"
                : lookup + "[1]";
    }

    /// @param key       The key's expression.
    /// @param match     A key of the map.
    /// @param out       Its value.
    /// @param otherwise What follows.
    /// @return One key of a value map written inline: if the input is the key, its value, else what follows.
    public static String mapStepText(final String key, final String match, final String out, final String otherwise) {
        return "if (" + key + " = " + literal(match) + ") then " + literal(out) + " else " + otherwise;
    }

    /// @param has          The test that the input has a value.
    /// @param expression   The value.
    /// @param defaultValue The default.
    /// @return A value, or the default when the input has none.
    public static String defaultText(final String has, final String expression, final String defaultValue) {
        return "if (" + has + ") then " + expression + " else " + literal(defaultValue);
    }

    /// @param text  The text an extraction reads.
    /// @param regex Its regex.
    /// @param flags Its flags, or null.
    /// @return The extraction's analyze-string() of the text.
    public static String partsText(final String text, final String regex, final String flags) {
        return "analyze-string(string(" + text + "[1]), " + literal(regex) + (flags != null && !flags.isEmpty()
                ? ", " + literal(flags)
                : "") + ")";
    }

    /// @param parts The extraction's analyze-string() result.
    /// @param nr    The group's number.
    /// @return One regex group of it.
    public static String groupText(final String parts, final Object nr) {
        return parts + "/fn:match//fn:group[@nr=" + nr + "]";
    }

    /// @param before The regex before the key.
    /// @param after  The regex after it.
    /// @param flags  The regex flags, or null.
    /// @return The body of a key=value function: the first group of the regex, the key put between before and after.
    public static String keyedText(final String before, final String after, final String flags) {
        return "for $g in (analyze-string(string($text[1]), concat(" + literal(before) + ", $key, " + literal(after)
               + ")" + (flags != null && !flags.isEmpty()
                ? ", " + literal(flags)
                : "") + ")//fn:group[@nr=1])[1] return string($g)";
    }

    /// @param source The input.
    /// @param value  The value.
    /// @return The test that the input is the value.
    public static String equalsText(final String source, final String value) {
        return source + " = " + literal(value);
    }

    /// @param source The input.
    /// @param values The values.
    /// @return The test that the input is one of the values.
    public static String oneOfText(final String source, final List<String> values) {
        return source + " = (" + values.stream().map(XPathText::literal).collect(Collectors.joining(", ")) + ")";
    }

    /// @param source The input.
    /// @param regex  A regex.
    /// @return The test that the input matches it.
    public static String matchesText(final String source, final String regex) {
        return "exists(" + source + "[matches(., " + literal(regex) + ")])";
    }

    /// @param source   The input.
    /// @param variable The stylesheet variable holding a dictionary's lines.
    /// @return The test that the input is one of them.
    public static String inDictionaryText(final String source, final String variable) {
        return source + " = $" + variable;
    }

    /// @param format   A time format: a Java pattern, or one of [#EPOCH_FORMATS].
    /// @param timezone The time zone, or null.
    /// @param one      The value's expression.
    /// @return The value as Stroom's ISO 8601 time, to the millisecond: finer digits rounded half up.
    public static String timeExpression(final String format, final String timezone, final String one) {
        if ("epoch_ms".equals(format)) {
            return "stroom:format-date(string(" + one + "))";
        }
        if (EPOCH_SCALE.containsKey(format)) {
            return "stroom:format-date(string(xs:integer(round(xs:decimal(" + one + ") " + EPOCH_SCALE.get(format)
                   + "))))";
        }
        if (SUB_MS.matcher(format).find()) {
            return timeMsText(format, timezone == null
                    ? ""
                    : timezone, one);
        }
        return "stroom:format-date(" + one + ", " + literal(format) + (timezone != null && !timezone.isEmpty()
                ? ", " + literal(timezone)
                : "") + ")";
    }

    /// @param format   A time format with digits below the millisecond.
    /// @param timezone The time zone, or "".
    /// @param one      The value's expression.
    /// @return The call of the XSLT's own function rounding to the millisecond.
    public static String timeMsText(final String format, final String timezone, final String one) {
        return "mcp:time-ms(string(" + one + "), " + literal(format) + ", " + literal(timezone) + ")";
    }

    /// @param transform A transform, or null.
    /// @param one       The value's expression.
    /// @return The transform applied to the value.
    public static String transformExpression(final String transform, final String one) {
        if (transform == null) {
            return one;
        }
        return switch (transform) {
            case "lower" -> "lower-case(" + one + ")";
            case "upper" -> "upper-case(" + one + ")";
            case "trim" -> "normalize-space(" + one + ")";
            case "strip_domain" -> "replace(replace(" + one + ", '^[^\\\\]*\\\\', ''), '@.*$', '')";
            case "domain" -> "replace(" + one + ", '^(?:([^\\\\]*)\\\\.*|[^@]*@(.*))$', '$1$2')";
            case "digits" -> "replace(" + one + ", '[^0-9]', '')";
            // An ANSI escape (ESC [ ... letter), then any other control character. Matched by class: Stroom's XPath
            // can't make an ESC to match with (codepoints-to-string(27): "invalid XML character", seen in eval case
            // 51).
            case "strip_control" -> "replace(" + one + ", '\\p{Cc}\\[[0-9;]*[A-Za-z]|[\\p{Cc}-[\\t\\n\\r]]', '')";
            case "unescape_quotes" -> "replace(" + one + ", '\"\"', '\"')";
            default -> one;
        };
    }

    /// @param path A path into XML held in a field (`/Envelope/Body/User`, `//User`, `/Login/@id`).
    /// @return It as XPath steps matched by local name, whatever the XML's namespaces.
    public static String xmlSteps(final String path) {
        final StringBuilder out = new StringBuilder();
        String rest = path;
        while (!rest.isEmpty()) {
            final String separator = rest.startsWith("//")
                    ? "//"
                    : "/";
            if (rest.startsWith(separator)) {
                rest = rest.substring(separator.length());
            }
            final int slash = rest.indexOf('/');
            final String step = slash < 0
                    ? rest
                    : rest.substring(0, slash);
            final String after = slash < 0
                    ? ""
                    : rest.substring(slash + 1);
            rest = after.isEmpty()
                    ? ""
                    : "/" + after;
            out.append(separator).append(step.startsWith("@")
                    ? "@*:" + step.substring(1)
                    : "*:" + step);
        }
        return out.toString();
    }
}
