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

package stroom.ai.impl.extraction;

import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A field a dashboard table's search extraction gives each event: its name, the XPath that finds its value in the
/// event, and the type of value it holds. The agent gives only these; Stroom writes the XSLT from them (see
/// [ExtractionXslt]).
///
/// The XPath runs in Stroom's own pipeline engine, on every search that extracts, where extension functions (e.g.
/// `stroom:http-call`) and functions that read other documents are available. So it may only use the plain string
/// and number functions in [#ALLOWED_FUNCTIONS], with no prefixed function and no variable, and is checked by
/// compiling it with the locked-down engine of [SafeXPath].
///
/// @param name  The field's name, as columns name it in `${...}`.
/// @param xpath The XPath that finds its value, from the event element (e.g. `EventSource/User/Id`), unprefixed:
///              names are in the events' namespace.
/// @param type  The type of value: [#TEXT], [#NUMBER] or [#DATE].
public record ExtractionField(@JsonProperty("name") String name,
                              @JsonProperty("xpath") String xpath,
                              @JsonProperty("type") String type) {

    /// A value of text.
    public static final String TEXT = "text";
    /// A number.
    public static final String NUMBER = "number";
    /// A date and time.
    public static final String DATE = "date";
    /// The types a field's value may be.
    public static final List<String> TYPES = List.of(TEXT, NUMBER, DATE);
    /// The fields every extraction gives, from the ids Stroom puts on each event as it extracts.
    public static final List<String> RESERVED = List.of("StreamId", "EventId");
    /// The most fields one extraction gives.
    public static final int MAX_FIELDS = 100;
    /// The longest a field's name may be.
    public static final int MAX_NAME_CHARS = 100;
    /// The longest a field's XPath may be.
    public static final int MAX_XPATH_CHARS = 500;
    /// The functions a field's XPath may use: plain string and number functions, and node tests.
    public static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "text", "node", "string", "normalize-space", "concat", "substring", "substring-before",
            "substring-after", "string-length", "string-join", "upper-case", "lower-case", "translate", "contains",
            "starts-with", "ends-with", "not", "true", "false", "boolean", "number", "round", "floor", "ceiling",
            "abs", "sum", "count", "min", "max", "avg", "exists", "empty", "position", "last", "name", "local-name",
            "data", "distinct-values", "format-number");

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9 _.()-]*");
    // A function call: a name (perhaps prefixed) followed by '(' or '#' (a function reference).
    private static final Pattern FUNCTION_CALL = Pattern.compile(
            "(?<![\\w.@$-])([A-Za-z_][\\w.-]*(?::[A-Za-z_][\\w.-]*)?)\\s*[(#]");
    // What a comment is made in an XPath's code (see code(String)): an empty comment, which no code can be.
    static final String COMMENT = "(::)";
    // The simple map operator, '!', not '!='.
    private static final Pattern SIMPLE_MAP = Pattern.compile("!(?!=)");

    @JsonCreator
    public ExtractionField {
        name = NullSafe.get(name, String::strip);
        xpath = NullSafe.get(xpath, String::strip);
        type = NullSafe.isBlankString(type)
                ? TEXT
                : type.strip().toLowerCase(Locale.ROOT);
    }

    /// Reads fields given as a tool's argument: a list of objects with `name`, `xpath` and optionally `type`.
    ///
    /// @param value The argument.
    /// @return The fields, checked.
    /// @throws WorkspaceException saying what is wrong with them, so the agent can correct them.
    public static List<ExtractionField> parse(final Object value) {
        if (!(value instanceof final List<?> list) || list.isEmpty()) {
            throw new WorkspaceException("Give fields as a list of {name, xpath, type} objects, e.g. "
                                         + "[{\"name\": \"Organisation\", \"xpath\": "
                                         + "\"EventSource/User/Organisation\", \"type\": \"text\"}].");
        }
        if (list.size() > MAX_FIELDS) {
            throw new WorkspaceException("Give at most " + MAX_FIELDS + " fields.");
        }
        final List<ExtractionField> fields = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof final Map<?, ?> map)) {
                throw new WorkspaceException("Each field is an object with name, xpath and type.");
            }
            fields.add(new ExtractionField(text(map.get("name")), text(map.get("xpath")), text(map.get("type"))));
        }
        final List<String> problems = problems(fields);
        if (!problems.isEmpty()) {
            throw new WorkspaceException("The fields can't be extracted:\n- " + String.join("\n- ", problems));
        }
        return fields;
    }

    /// @param fields Fields to extract.
    /// @return What is wrong with them; none if nothing is.
    public static List<String> problems(final List<ExtractionField> fields) {
        final List<String> problems = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final ExtractionField field : fields) {
            final String label = "'" + NullSafe.string(field.name()) + "'";
            if (NullSafe.isBlankString(field.name())) {
                problems.add("A field has no name.");
                continue;
            }
            if (field.name().length() > MAX_NAME_CHARS || !NAME_PATTERN.matcher(field.name()).matches()) {
                problems.add(label + " isn't a name a field can have: letters, digits, spaces and _ . ( ) -, "
                             + "starting with a letter, up to " + MAX_NAME_CHARS + " characters.");
            }
            if (RESERVED.stream().anyMatch(reserved -> reserved.equalsIgnoreCase(field.name()))) {
                problems.add(label + " is given by every extraction, from the ids of the event; leave it out.");
            }
            if (!seen.add(field.name().toLowerCase(Locale.ROOT))) {
                problems.add(label + " is given more than once.");
            }
            if (!TYPES.contains(field.type())) {
                problems.add(label + " has type '" + field.type() + "': give one of " + String.join(", ", TYPES)
                             + ".");
            }
            NullSafe.consume(xpathProblem(field.xpath()), problem -> problems.add(label + ": " + problem));
        }
        return problems;
    }

    /// @param xpath A field's XPath.
    /// @return What is wrong with it, or null if nothing is.
    static String xpathProblem(final String xpath) {
        if (NullSafe.isBlankString(xpath)) {
            return "give the XPath of its value, from the event, e.g. EventSource/User/Id.";
        }
        if (xpath.length() > MAX_XPATH_CHARS) {
            return "its XPath may be at most " + MAX_XPATH_CHARS + " characters.";
        }
        final String code = code(xpath);
        if (code == null) {
            return "its XPath has a string or comment that isn't closed.";
        }
        if (code.contains(COMMENT)) {
            // Nothing needs one, and one is a place to hide code.
            return "its XPath may not have comments.";
        }
        if (code.contains("{") || code.contains("#")) {
            return "its XPath may not use braces (e.g. Q{...} names) or function references (#).";
        }
        if (code.contains("$")) {
            return "its XPath may not use variables.";
        }
        if (code.contains("=>") || SIMPLE_MAP.matcher(code).find()) {
            return "its XPath may not use the arrow or map operators.";
        }
        final Matcher matcher = FUNCTION_CALL.matcher(code);
        while (matcher.find()) {
            final String function = matcher.group(1);
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                return "its XPath uses " + function + "(), which an extraction may not: it may use only "
                       + String.join(", ", ALLOWED_FUNCTIONS.stream().sorted().toList()) + ".";
            }
        }
        try {
            // Its functions are checked above, against those allowed, in its code alone.
            SafeXPath.compileWithoutFunctionCheck(xpath, Map.of());
        } catch (final WorkspaceException e) {
            return e.getMessage();
        }
        return null;
    }

    /// The code of an XPath, as XPath reads it: each string literal (in either quote, a doubled quote standing for
    /// one) made empty, and each comment (`(: ... :)`, which may be nested) made [#COMMENT], so what they hold is
    /// never taken for code, nor code hidden in them (e.g. a quote in a comment taken for the start of a string).
    ///
    /// @param xpath An XPath expression.
    /// @return Its code; null if a string or comment isn't closed.
    static String code(final String xpath) {
        final StringBuilder code = new StringBuilder();
        int i = 0;
        while (i < xpath.length()) {
            final char c = xpath.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (true) {
                    end = xpath.indexOf(c, end);
                    if (end < 0) {
                        return null;
                    }
                    if (end + 1 < xpath.length() && xpath.charAt(end + 1) == c) {
                        // A doubled quote: one quote in the string.
                        end += 2;
                    } else {
                        break;
                    }
                }
                code.append(c).append(c);
                i = end + 1;
            } else if (c == '(' && i + 1 < xpath.length() && xpath.charAt(i + 1) == ':') {
                int depth = 1;
                int at = i + 2;
                while (depth > 0) {
                    if (at + 1 >= xpath.length()) {
                        return null;
                    }
                    if (xpath.charAt(at) == '(' && xpath.charAt(at + 1) == ':') {
                        depth++;
                        at += 2;
                    } else if (xpath.charAt(at) == ':' && xpath.charAt(at + 1) == ')') {
                        depth--;
                        at += 2;
                    } else {
                        at++;
                    }
                }
                code.append(COMMENT);
                i = at;
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }

    private static String text(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }
}
