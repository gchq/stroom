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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What a translation mapping says, as the Stroom MCP server's generator, checks and documentation work it out
/// (from `utils/xsltgen.py`): the action element a rule writes, which rules are kept as Unknown, what the xpaths
/// read and call, and a mapping's mistakes the schema can't catch (a function call given as a field, a time pattern
/// Java would refuse).
public final class MappingFacts {

    /// The XML Schema instance namespace.
    public static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    /// The XML Schema namespace.
    public static final String XS = "http://www.w3.org/2001/XMLSchema";
    /// The XPath functions namespace.
    public static final String FN = "http://www.w3.org/2005/xpath-functions";
    /// The XPath map functions namespace.
    public static final String MAP_NS = "http://www.w3.org/2005/xpath-functions/map";
    /// The namespace of the generated XSLT's own functions (`mcp:`).
    public static final String MCP_NS = "stroom-mcp:functions";
    /// Prefixes the generated XSLT binds itself, or XPath's own: a functions entry may not rebind one to another
    /// namespace.
    public static final Map<String, String> KNOWN_PREFIXES = knownPrefixes();
    /// A prefixed function call in an xpath: its prefix is the first group.
    public static final Pattern FUNCTION_PREFIX = Pattern.compile(
            "(?<![\\w.:$@-])([A-Za-z_][\\w.-]*):[A-Za-z_][\\w.-]*\\s*\\(", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern CALL = Pattern.compile("^\\s*(?:[A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)\\s*\\(",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> TEXT_FUNCTIONS = Set.of("extract", "regex", "match", "matches", "replace",
            "substring", "substring-before", "substring-after", "analyze-string", "tokenize");
    private static final List<String> NOT_ACTION = List.of("TypeId", "Description", "Classification", "Purpose");
    private static final Pattern LITERALS = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Pattern BOUND_VARIABLE = Pattern.compile("\\b(?:for|let|some|every)\\s+\\$([\\w.-]+)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern VARIABLE = Pattern.compile("\\$([A-Za-z_][\\w.-]*)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LAST_WORD = Pattern.compile("([\\w.-]+)$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ROOTED = Pattern.compile("/+(?:[^\\s,()\\[\\]]|\\[[^\\[\\]]*\\])*",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> OPERATORS = Set.of("and", "or", "return", "then", "else", "in", "satisfies",
            "eq", "ne", "lt", "le", "gt", "ge", "is", "union", "intersect", "except", "to", "div", "mod");
    private static final Pattern CALL_START = Pattern.compile("[\\w:.-]+\\(", Pattern.UNICODE_CHARACTER_CLASS);
    // Pattern letters of Java's SimpleDateFormat and DateTimeFormatter; any other letter must be quoted.
    private static final String PATTERN_LETTERS = "GuyDMLdQqYwWEecFaHkKhmsSAnNVzOXxZp";

    private MappingFacts() {
        // Static only.
    }

    private static Map<String, String> knownPrefixes() {
        final Map<String, String> prefixes = new LinkedHashMap<>();
        prefixes.put("xsl", XmlTrees.XSL);
        prefixes.put("xsi", XSI);
        prefixes.put("xs", XS);
        prefixes.put("stroom", "stroom");
        prefixes.put("fn", FN);
        prefixes.put("map", MAP_NS);
        prefixes.put("array", "http://www.w3.org/2005/xpath-functions/array");
        prefixes.put("math", "http://www.w3.org/2005/xpath-functions/math");
        prefixes.put("mcp", MCP_NS);
        return Collections.unmodifiableMap(prefixes);
    }

    /// Why a field name that is a function call isn't one. Seen (Gemma 4 31B): every field given as
    /// `extract(EventData/Data, 'Action: \[([^\]]+)\]')`, written into the XSLT as XPath; each step failed to
    /// compile ("Cannot find a 2-argument function named extract()") until the agent gave up.
    ///
    /// @param name A field name.
    /// @return Why it is no field, or null if it is not a call.
    public static String functionCall(final String name) {
        final Matcher found = CALL.matcher(Objects.requireNonNullElse(name, ""));
        if (!found.lookingAt()) {
            return null;
        }
        if (TEXT_FUNCTIONS.contains(found.group(1))) {
            return "is a function call, not an input field. To take values out of a text field with a regular "
                   + "expression, add an entry to the mapping's extract list, {\"field\": \"<the text field>\", "
                   + "\"regex\": \"<one group per value>\", \"names\": [\"<a name per group>\"]}, and use those names "
                   + "as fields.";
        }
        return "is an expression, not an input field: give it as xpath (an XPath expression on the record) instead, "
               + "or name the input field itself.";
    }

    /// @param paths A rule's paths.
    /// @return The action element they write, where the rule's Data goes: `EventDetail/Authenticate`, or
    /// `EventDetail/Network/Permit` (Network's action is an element of its own); null if none.
    public static String actionHome(final List<String> paths) {
        for (final String path : paths) {
            final List<String> parts = PyText.split(PyText.strip(path, "/"), "/");
            if (parts.size() >= 3 && "EventDetail".equals(parts.get(0)) && !NOT_ACTION.contains(parts.get(1))) {
                return String.join("/", "Network".equals(parts.get(1)) && parts.size() >= 4
                        ? parts.subList(0, 3)
                        : parts.subList(0, 2));
            }
        }
        return null;
    }

    /// @param mapping A mapping.
    /// @param rule    One of its rules.
    /// @return True if the rule's events get EventDetail/Unknown (its own fields, or the common ones it doesn't
    /// drop).
    public static boolean writesUnknown(final TranslationMapping mapping, final EventRule rule) {
        return rule.fields().stream().anyMatch(MappingFacts::isUnknown)
               || (!rule.drop() && mapping.common().stream().anyMatch(MappingFacts::isUnknown));
    }

    private static boolean isUnknown(final FieldMapping entry) {
        return entry.strippedPath().startsWith("EventDetail/Unknown");
    }

    /// @param mapping A mapping.
    /// @return The rules kept as Unknown: allow_unknown on a rule that writes Unknown (on any other rule it means
    /// nothing).
    public static List<EventRule> keptUnknown(final TranslationMapping mapping) {
        return mapping.events().stream()
                .filter(rule -> rule.allowUnknown() != null && !rule.allowUnknown().isEmpty())
                .filter(rule -> writesUnknown(mapping, rule))
                .toList();
    }

    /// Whether the mapping reads an input field (or a name from extract) anywhere: an output's field, any_of or
    /// lookup key, a condition, an extraction's source, or by name in an xpath (as JSON held in a string is read).
    ///
    /// @param mapping A mapping.
    /// @param name    A field name.
    /// @return True if it reads it.
    public static boolean readsInput(final TranslationMapping mapping, final String name) {
        final List<FieldMapping> entries = mapping.allEntries();
        final List<Condition> conditions = conditions(mapping);
        final Set<String> names = new HashSet<>();
        for (final FieldMapping entry : entries) {
            names.add(entry.field());
            if (entry.anyOf() != null) {
                names.addAll(entry.anyOf());
            }
            if (entry.lookup() != null) {
                names.add(entry.lookup().field());
            }
        }
        conditions.forEach(condition -> names.add(condition.field()));
        mapping.extract().forEach(extraction -> names.add(extraction.field()));
        if (names.contains(name)) {
            return true;
        }
        final List<String> xpaths = new ArrayList<>();
        entries.forEach(entry -> xpaths.add(entry.xpath()));
        entries.stream().filter(entry -> entry.lookup() != null).forEach(entry -> xpaths.add(entry.lookup().xpath()));
        conditions.forEach(condition -> xpaths.add(condition.xpath()));
        mapping.extract().forEach(extraction -> xpaths.add(extraction.xpath()));
        xpaths.add(mapping.forEach());
        // $name too: not a working read (the generator declares no such variable, see variable_reads), but what was
        // meant; seen, "extract names ['kv'], which nothing reads" the only word on an xpath of $kv that failed every
        // record.
        final Pattern read = Pattern.compile("['\"]" + Pattern.quote(name) + "['\"]|\\$" + Pattern.quote(name)
                                             + "(?![\\w.-])", Pattern.UNICODE_CHARACTER_CLASS);
        return xpaths.stream().anyMatch(xpath -> xpath != null && !xpath.isEmpty() && read.matcher(xpath).find());
    }

    private static List<Condition> conditions(final TranslationMapping mapping) {
        final List<Condition> out = new ArrayList<>();
        mapping.events().forEach(rule -> out.addAll(rule.when()));
        mapping.dropWhen().forEach(rule -> out.addAll(rule.when()));
        return out;
    }

    /// @param expression An XPath expression.
    /// @return It with its string literals' contents made spaces, at the same length.
    static String blanked(final String expression) {
        final Matcher matcher = LITERALS.matcher(expression);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String literal = matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(literal.charAt(0)
                                                                   + " ".repeat(literal.length() - 2)
                                                                   + literal.charAt(literal.length() - 1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /// @param expression An XPath expression.
    /// @param names      Names.
    /// @return The names it reads as variables (`$kv`) without binding them itself (for, let, some, every), in the
    /// order it reads them.
    public static List<String> variableReads(final String expression, final Set<String> names) {
        final String code = blanked(expression);
        final Set<String> bound = new HashSet<>();
        final Matcher binding = BOUND_VARIABLE.matcher(code);
        while (binding.find()) {
            bound.add(binding.group(1));
        }
        final Set<String> read = new LinkedHashSet<>();
        final Matcher variable = VARIABLE.matcher(code);
        while (variable.find()) {
            read.add(variable.group(1));
        }
        return read.stream().filter(name -> names.contains(name) && !bound.contains(name)).toList();
    }

    /// The first path in an XPath expression that starts at the document root (`/x`, `//x`) rather than at the
    /// context node. A `/` after a step (`a/b`, `)/b`, `.//b`) continues a path; at the start, or after an operator,
    /// a bracket or a comma, it starts one. String literals are not read.
    ///
    /// @param expression An XPath expression.
    /// @return The path, or null if it has none.
    public static String rootedPath(final String expression) {
        final String code = blanked(expression);
        for (int at = code.indexOf('/'); at >= 0; at = code.indexOf('/', at + 1)) {
            if (at > 0 && code.charAt(at - 1) == '/') {
                continue;       // the second slash of a //
            }
            final String before = PyText.rstrip(code.substring(0, at));
            if (startsPath(before)) {
                final Matcher end = ROOTED.matcher(code.substring(at));
                if (end.lookingAt()) {
                    return expression.substring(at, at + end.end());
                }
            }
        }
        return null;
    }

    private static boolean startsPath(final String before) {
        if (before.isEmpty() || "([,|=<>+!{".indexOf(before.charAt(before.length() - 1)) >= 0) {
            return true;
        }
        final Matcher word = LAST_WORD.matcher(before);
        if (!word.find() || !OPERATORS.contains(word.group(1))) {
            return false;
        }
        final int previous = before.length() - word.group(1).length() - 1;
        return previous >= 0 && (before.charAt(previous) == ' ' || before.charAt(previous) == ')');
    }

    /// @param expression An XPath expression.
    /// @return True if it is one function call, such as `concat(a, '-', b)`, which a predicate can follow without
    /// brackets; `a or b` or `x | y` would need them.
    public static boolean isCall(final String expression) {
        final String text = PyText.strip(expression);
        final Matcher start = CALL_START.matcher(text);
        if (!start.lookingAt()) {
            return false;
        }
        int depth = 0;
        char quote = 0;
        for (int n = start.end() - 1; n < text.length(); n++) {
            final char ch = text.charAt(n);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return n == text.length() - 1;
                }
            }
        }
        return false;
    }

    /// @param mapping A mapping.
    /// @return The function prefixes its xpaths can call: the XSLT's own and those its functions entries bind.
    public static Map<String, String> mappingNamespaces(final TranslationMapping mapping) {
        final Map<String, String> out = new LinkedHashMap<>(KNOWN_PREFIXES);
        mapping.functions().forEach(functions -> out.put(functions.prefix(), functions.namespace()));
        return out;
    }

    /// @param mapping A mapping.
    /// @return Every xpath it gives: fields' and lookups', conditions', extractions', for_each and shared templates'
    /// parameters.
    public static List<String> mappingXpaths(final TranslationMapping mapping) {
        final List<FieldMapping> entries = mapping.allEntries();
        final List<String> found = new ArrayList<>();
        entries.forEach(entry -> found.add(entry.xpath()));
        entries.stream().filter(entry -> entry.lookup() != null).forEach(entry -> found.add(entry.lookup().xpath()));
        conditions(mapping).forEach(condition -> found.add(condition.xpath()));
        mapping.extract().forEach(extraction -> found.add(extraction.xpath()));
        found.add(mapping.forEach());
        mapping.shared().forEach(template -> found.addAll(template.withParams().values()));
        return found.stream().filter(xpath -> xpath != null && !xpath.isEmpty()).toList();
    }

    /// @param path A path below Event.
    /// @return It with its action element as `*` (Network's own action too): `EventDetail/Network/Deny/Source` is
    /// `EventDetail/Network/*/Source`, `EventDetail/Authenticate/Data` is `EventDetail/*/Data`.
    public static String actionFree(final String path) {
        final List<String> steps = new ArrayList<>(PyText.split(PyText.strip(path, "/"), "/"));
        final int at = steps.indexOf("EventDetail");
        if (at < 0 || steps.size() <= at + 1 || NOT_ACTION.contains(steps.get(at + 1))) {
            return path;
        }
        final boolean network = "Network".equals(steps.get(at + 1));
        steps.set(at + 1, network
                ? "Network"
                : "*");
        if (network && steps.size() > at + 2) {
            steps.set(at + 2, "*");
        }
        return String.join("/", steps);
    }

    /// @param condition A condition.
    /// @return It as a reader says it: `action = accept`, `type in traffic, event`.
    public static String conditionWords(final Condition condition) {
        final String source = (condition.field() != null
                ? condition.field()
                : condition.xpath()) + ("record".equals(condition.scope())
                ? " (of the record)"
                : "");
        if (condition.equalTo() != null) {
            return source + " = " + condition.equalTo();
        }
        if (condition.oneOf() != null) {
            return source + " in " + String.join(", ", condition.oneOf());
        }
        if (condition.matches() != null) {
            return source + " matches " + condition.matches();
        }
        if (condition.inDictionary() != null) {
            return source + " in the dictionary " + condition.inDictionary();
        }
        return source + " " + (Boolean.TRUE.equals(condition.present())
                ? "present"
                : "empty");
    }

    /// @param pattern A Java time pattern.
    /// @return The unquoted letters Java would reject in it (the T in `yyyy-MM-ddTHH:mm:ss`), with the pattern
    /// fixed; null if it has none.
    public static String patternProblem(final String pattern) {
        boolean quoted = false;
        final Set<String> bad = new TreeSet<>();
        final StringBuilder fixed = new StringBuilder();
        for (final int cp : pattern.codePoints().toArray()) {
            if (cp == '\'') {
                quoted = !quoted;
            } else if (!quoted && Character.isLetter(cp) && PATTERN_LETTERS.indexOf(cp) < 0) {
                bad.add(Character.toString(cp));
                fixed.append('\'').appendCodePoint(cp).append('\'');
                continue;
            }
            fixed.appendCodePoint(cp);
        }
        if (bad.isEmpty()) {
            return null;
        }
        return "time_format " + PyText.repr(pattern) + " has unquoted letters " + PyText.list(bad)
               + "; quote them, e.g. " + PyText.repr(fixed.toString());
    }

    /// @param mapping A mapping.
    /// @return The namespace the XSLT reads its input in. XML fragments that declare none take the wrapper's
    /// default, records:2 (seen: fragments read with no namespace selected nothing, and every record came out
    /// empty).
    public static String inputNamespace(final TranslationMapping mapping) {
        return switch (mapping.input()) {
            case "xml_fragments" -> mapping.xmlNamespace().isEmpty()
                    ? "records:2"
                    : mapping.xmlNamespace();
            case "data_splitter" -> "records:2";
            case "json" -> "http://www.w3.org/2013/XSL/json";
            default -> mapping.xmlNamespace();
        };
    }
}
