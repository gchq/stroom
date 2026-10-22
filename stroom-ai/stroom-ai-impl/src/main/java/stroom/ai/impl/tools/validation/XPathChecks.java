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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Evaluates a mapping's xpath inputs and extraction regexes against the sample, before anything is created or
/// stepped. Ported from the Stroom MCP server's `utils/xpathcheck.py`, without its mapping model: the caller (the
/// translation build) gives the xpaths and extractions, and the sample records as documents.
///
/// An xpath that selects nothing in any sample record (json-to-xml(event)/timestamp where the JSON parser gives
/// string[@key='event'], say) generates valid XSLT that steps clean and writes empty elements or matches no rule.
/// Each record is rebuilt the way the pipeline's parser presents it to the XSLT (the JSONParser's map/string
/// elements, the Data Splitter's records:2 data elements, or the XML record itself) and every xpath is evaluated on
/// it with Saxon ([LockedSaxon]), as the translation would. Expressions that need Stroom (stroom: functions) or
/// variables are not evaluated.
public final class XPathChecks {

    /// Characters a regex written from memory gets wrong: what the text has, and what the regex was likely given.
    public static final Map<Character, String> CONFUSABLE = Map.ofEntries(
            Map.entry('–', "-"), Map.entry('—', "-"), Map.entry('−', "-"), Map.entry('‐', "-"),
            Map.entry(' ', " "), Map.entry(' ', " "), Map.entry('‘', "'"), Map.entry('’', "'"),
            Map.entry('“', "\""), Map.entry('”', "\""), Map.entry('\t', " "));
    /// Said of a regex escaped twice.
    public static final String ESCAPED_TWICE = " `\\\\` in a regex matches a backslash character. A literal [ or ( "
                                               + "is `\\[` or `\\(`: one backslash in the regex itself, which your "
                                               + "call's JSON writes as two; nothing else escapes it again.";
    /// The most record documents an extraction's xpath source is read from.
    public static final int MAX_SOURCE_DOCUMENTS = 200;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XPathChecks.class);
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]");
    private static final Pattern LITERAL = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Pattern VARIABLE = Pattern.compile("\\$[A-Za-z_]");
    // ^, or a leading (?:^|\s)-like group.
    private static final Pattern LEAD = Pattern.compile("^(?:\\^|\\(\\?:[^()]*\\))?");
    // The literal text after it.
    private static final Pattern KEY = Pattern.compile("(?:\\\\[^dDsSwWbBpPnrt]|[^\\\\()\\[\\]{}.*+?|^$])+");
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
    private static final Pattern NOT_XML = Pattern.compile(
            "[^\\x{9}\\x{a}\\x{d}\\x{20}-\\x{d7ff}\\x{e000}-\\x{fffd}\\x{10000}-\\x{10ffff}]");

    private XPathChecks() {
        // Static only.
    }

    /// @param value A JSON value, as Jackson reads it (maps, lists, strings, numbers, booleans and null).
    /// @param key   Its key in the object holding it, or null.
    /// @return It as the JSONParser writes it: map, array, string, number, boolean and null elements, keyed.
    public static String jsonXml(final Object value, final String key) {
        final String k = key != null
                ? " key=" + quoteAttr(key)
                : "";
        if (value instanceof final Map<?, ?> map) {
            final StringBuilder sb = new StringBuilder("<map").append(k).append('>');
            map.forEach((name, item) -> sb.append(jsonXml(item, String.valueOf(name))));
            return sb.append("</map>").toString();
        }
        if (value instanceof final Collection<?> list) {
            final StringBuilder sb = new StringBuilder("<array").append(k).append('>');
            list.forEach(item -> sb.append(jsonXml(item, null)));
            return sb.append("</array>").toString();
        }
        if (value instanceof final Boolean bool) {
            return "<boolean" + k + ">" + bool + "</boolean>";
        }
        if (value == null) {
            return "<null" + k + "/>";
        }
        if (value instanceof Number) {
            return "<number" + k + ">" + value + "</number>";
        }
        return "<string" + k + ">" + escape(String.valueOf(value)) + "</string>";
    }

    /// @param text      A JSON sample: an array of objects, or an object a line.
    /// @param namespace The namespace the JSONParser's output is in for the translation, or null for none.
    /// @return Each object as the XML document the translation reads it from (its root element is the record).
    public static List<String> jsonRecordDocuments(final String text, final String namespace) {
        final List<String> documents = new ArrayList<>();
        for (final Object value : jsonValues(text)) {
            final String xml = jsonXml(value, null);
            // Each value is an object, so its document's root is a map.
            documents.add(xml11(namespace == null || namespace.isEmpty()
                    ? xml
                    : "<map xmlns=\"" + namespace + "\"" + xml.substring("<map".length())));
        }
        return documents;
    }

    /// @param record    A Data Splitter record: each data element's name and value, in order.
    /// @param namespace The records namespace the translation reads, or null for none.
    /// @return It as the XML document the translation reads it from.
    public static String splitterRecordDocument(final Map<String, ?> record, final String namespace) {
        final StringBuilder sb = new StringBuilder("<record");
        if (namespace != null && !namespace.isEmpty()) {
            sb.append(" xmlns=").append(quoteAttr(namespace));
        }
        sb.append('>');
        record.forEach((name, value) -> sb.append("<data name=").append(quoteAttr(name)).append(" value=")
                .append(quoteAttr(String.valueOf(value))).append("/>"));
        return xml11(sb.append("</record>").toString());
    }

    /// A record document as Stroom writes records, XML 1.1: control characters (an ANSI colour code's ESC, seen in
    /// eval case 51) as character references, which XML 1.0 can't hold at all and the check's parser refused.
    ///
    /// @param document A record document.
    /// @return It, as XML 1.1 if it holds control characters.
    public static String xml11(final String document) {
        final Matcher matcher = CONTROL.matcher(document);
        if (!matcher.find()) {
            return document;
        }
        matcher.reset();
        final StringBuilder sb = new StringBuilder("<?xml version=\"1.1\"?>");
        while (matcher.find()) {
            matcher.appendReplacement(sb, "&#x" + Integer.toHexString(matcher.group().charAt(0))
                    .toUpperCase() + ";");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /// Whether an xpath reads a variable ($name), outside its string literals: a regex replacement's $1 is not
    /// one (seen: every reformatting replace(..., '$1:$2') went unchecked, one selecting nothing among them).
    ///
    /// @param xpath An xpath.
    /// @return True if it reads a variable, which only the XSLT binds.
    public static boolean readsVariable(final String xpath) {
        return VARIABLE.matcher(LITERAL.matcher(xpath).replaceAll("''")).find();
    }

    /// @param xpath            An xpath a mapping reads.
    /// @param importedPrefixes The prefixes of the shared XSLTs' functions the mapping calls (its functions
    ///                         entries).
    /// @return True if it can be evaluated on the sample: not one calling a Stroom function or a shared XSLT's
    /// function (only Stroom, which imports it, can run them), nor one reading a variable.
    public static boolean evaluable(final String xpath, final Collection<String> importedPrefixes) {
        return !xpath.contains("stroom:")
               && !readsVariable(xpath)
               && importedPrefixes.stream().noneMatch(prefix -> xpath.contains(prefix + ":"));
    }

    /// Warnings for xpaths that select nothing in any sample record (or fail to evaluate), and for those that
    /// are refused ([XsltSafety#refused(String, Map)]), which are not evaluated.
    ///
    /// @param inputs    Each xpath to check (as [#evaluable(String, Collection)] allows), with what it is used for
    ///                  (e.g. `EventTime/TimeCreated` or `[logon] when`).
    /// @param bound     The prefixes the mapping's functions entries bind.
    /// @param documents The sample records as the translation reads them.
    /// @param namespace The namespace of the records' elements, or null for none.
    /// @return The warnings.
    public static List<String> checkXPaths(final Map<String, List<String>> inputs,
                                           final Map<String, String> bound,
                                           final List<String> documents,
                                           final String namespace) {
        final List<String> warnings = new ArrayList<>();
        final List<String> evaluated = new ArrayList<>();
        inputs.forEach((xpath, used) -> {
            final String refused = XsltSafety.refused(xpath, bound);
            if (refused != null) {
                warnings.add("xpath " + shown(xpath, 200) + " (used for " + usedFor(used) + ") was not evaluated: "
                             + refused + ".");
            } else {
                evaluated.add(xpath);
            }
        });
        if (evaluated.isEmpty() || documents.isEmpty()) {
            return warnings;
        }
        final List<XdmNode> parsed = parseAll(documents);
        final XPathCompiler compiler = LockedSaxon.xpath(namespace);
        for (final String xpath : evaluated) {
            boolean found = false;
            String error = null;
            for (final XdmNode doc : parsed) {
                try {
                    final XPathSelector selector = compiler.compile(
                            "exists((/*/(" + xpath + "))[normalize-space(string(.))])").load();
                    selector.setContextItem(doc);
                    if (selector.effectiveBooleanValue()) {
                        found = true;
                        break;
                    }
                } catch (final SaxonApiException | RuntimeException e) {
                    // A dynamic error on this record (bad JSON in a string, say): try the next.
                    if (error == null) {
                        error = firstLine(e.getMessage(), 160);
                    }
                }
            }
            if (!found) {
                final String hint = xpath.contains("json-to-xml")
                        ? " JSON held in a string field is read as json-to-xml(*[@key='field'])/*/*[@key='name']."
                        : "";
                warnings.add("xpath " + shown(xpath, 200) + " (used for " + usedFor(inputs.get(xpath))
                             + ") selects nothing in any of the " + parsed.size() + " sample records"
                             + (error != null
                        ? " (" + error + ")"
                        : "") + "." + hint);
            }
        }
        return warnings;
    }

    /// Each extraction's regex run (with XPath's own regex rules) on the text it reads in the sample: one that
    /// matches none is a problem, as its fields would be empty in every event; one that matches some, a warning.
    /// Seen: a regex written with '-' where the text has an en dash matched nothing, nothing said so, and the
    /// agent changed the escaping a hundred times. Every text the sample has is tried: the first 200 alone missed
    /// a VPN rule's keys, whose records began at the 557th of 3,933, and a sound mapping was refused (seen in
    /// production). Past the deadline, the entries left are named, not checked.
    ///
    /// @param extractions The mapping's extractions, in order.
    /// @param fieldTexts  Whether a field's texts are read from the records (a Data Splitter's or JSON input),
    ///                    rather than by xpath from the record documents.
    /// @param records     The sample records as name-value maps (for field texts).
    /// @param documents   The sample records as the translation reads them (for xpath sources), made when needed.
    /// @param namespace   The namespace of the records' elements, or null for none.
    /// @param bound       The prefixes the mapping's functions entries bind.
    /// @param deadline    When to stop checking, or null for no limit.
    /// @param clock       The clock the deadline is read against.
    /// @return The problems and warnings.
    public static Findings checkExtractions(final List<Extraction> extractions,
                                            final boolean fieldTexts,
                                            final List<Map<String, ?>> records,
                                            final Supplier<List<String>> documents,
                                            final String namespace,
                                            final Map<String, String> bound,
                                            final Instant deadline,
                                            final Clock clock) {
        final List<String> problems = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> unchecked = new ArrayList<>();
        final Set<String> produced = new LinkedHashSet<>();
        List<XdmNode> parsed = null;
        for (int n = 0; n < extractions.size(); n++) {
            final Extraction ex = extractions.get(n);
            final String source = ex.field() != null && !ex.field().isEmpty()
                    ? ex.field()
                    : ex.xpath();
            final String where = "extract[" + n + "] (" + source + ")";
            final List<String> names = ex.names().stream().filter(name -> name != null && !name.isEmpty()).toList();
            if (ex.field() != null && produced.contains(ex.field())) {
                // It reads a field an earlier extraction makes: checked through that one.
                produced.addAll(names);
                continue;
            }
            produced.addAll(names);
            final List<String> texts = new ArrayList<>();
            if (ex.field() != null && !ex.field().isEmpty() && fieldTexts) {
                for (final Map<String, ?> record : records) {
                    final String value = valueOf(record, ex.field());
                    if (value != null && !value.isEmpty()) {
                        texts.add(value);
                    }
                }
            } else {
                final String refused = XsltSafety.refused(source, bound);
                if (refused != null) {
                    problems.add(where + ": " + refused + ".");
                    continue;
                }
                if (parsed == null) {
                    final List<String> all = documents.get();
                    parsed = parseAll(all.subList(0, Math.min(MAX_SOURCE_DOCUMENTS, all.size())));
                }
                texts.addAll(sourceTexts(source, parsed, namespace));
            }
            if (texts.isEmpty()) {
                // Nothing to read: the xpath and field checks say so.
                continue;
            }
            if (deadline != null && clock.instant().isAfter(deadline)) {
                unchecked.add(where);
                continue;
            }
            checkExtraction(where, ex, texts, problems, warnings);
        }
        if (!unchecked.isEmpty()) {
            warnings.add(String.join(", ", unchecked) + " not checked against the sample: the check's time ran "
                         + "out. step_sample shows what they give.");
        }
        return new Findings(problems, warnings);
    }

    /// Where a regex stops matching the texts: the longest start of it that matches the start of a text, and what
    /// the text has there instead of what the regex goes on with. Read with Java's regular expressions, close to
    /// XPath's, as the Stroom MCP server read it with Python's.
    ///
    /// @param regex A regex.
    /// @param flags Its XPath flags (i, m, s, x).
    /// @param texts The texts it doesn't match.
    /// @return Where it stops, and why if a character there is one a regex written from memory gets wrong.
    public static String whereItStops(final String regex, final String flags, final List<String> texts) {
        final int javaFlags = javaFlags(flags);
        final List<String> some = texts.subList(0, Math.min(8, texts.size()));
        if (!regex.startsWith("^")) {
            final String found = searched(regex, javaFlags, some);
            if (found != null) {
                return found;
            }
        }
        int bestEnd = -1;
        int bestCut = -1;
        String bestText = null;
        for (final String text : some) {
            for (int cut = regex.length(); cut > 0; cut--) {
                final Matcher matcher = matcher(regex.substring(0, cut), javaFlags, text);
                if (matcher != null && matcher.lookingAt()) {
                    if (bestText == null || matcher.end() > bestEnd || (matcher.end() == bestEnd && cut > bestCut)) {
                        bestEnd = matcher.end();
                        bestCut = cut;
                        bestText = text;
                    }
                    break;
                }
            }
        }
        if (bestText == null) {
            return "Not even its start matches: the text starts " + shown(texts.getFirst(), 60) + ".";
        }
        final String rest = regex.substring(bestCut);
        final String there = bestText.substring(bestEnd);
        if (stripChars(rest, '$').isEmpty()) {
            return "All of it matches, but the text goes on after: " + shown(there, 60) + "; end the regex with "
                   + "what follows, or drop the $.";
        }
        String message = "It matches as far as " + shown(bestText.substring(Math.max(0, bestEnd - 40), bestEnd), 60)
                         + " and stops there: the regex goes on with " + shown(rest, 30) + ", the text with "
                         + shown(there, 30) + ".";
        if (!there.isEmpty() && CONFUSABLE.containsKey(there.charAt(0))) {
            final char ch = there.charAt(0);
            final String name = Character.getName(ch);
            message += " The text has " + (name != null
                    ? name
                    : String.format("U+%04X", (int) ch)) + " (U+" + String.format("%04X", (int) ch) + "), not "
                       + shown(CONFUSABLE.get(ch), 60) + ": put that character in the regex as it is (or a class "
                       + "with both).";
        }
        if (rest.substring(0, Math.min(6, rest.length())).contains("\\\\")) {
            message += ESCAPED_TWICE;
        }
        return message;
    }

    /// Text as it is, in backticks, for a message: never escaped, which doubles each backslash (and the reply's
    /// JSON doubles it again), and an agent then 'fixes' the escaping of a regex that was right.
    ///
    /// @param text  Text.
    /// @param limit The most characters shown.
    /// @return It in backticks, cut with an ellipsis past the limit.
    public static String shown(final String text, final int limit) {
        final String cut = text.length() <= limit
                ? text
                : text.substring(0, limit) + "…";
        return "`" + cut.replace('`', '\'') + "`";
    }

    // Checks one extraction's regex on its texts.
    private static void checkExtraction(final String where,
                                        final Extraction ex,
                                        final List<String> rawTexts,
                                        final List<String> problems,
                                        final List<String> warnings) {
        // As XPath reads them.
        final List<String> texts = rawTexts.stream()
                .map(text -> NOT_XML.matcher(text).replaceAll("�"))
                .toList();
        final String flags = ex.flags() == null
                ? ""
                : ex.flags();
        if (ex.regex().length() > SafePattern.MAX_REGEX_LENGTH) {
            problems.add(where + ": the regex is " + ex.regex().length() + " characters long, and may be at most "
                         + SafePattern.MAX_REGEX_LENGTH + ".");
            return;
        }
        final RegularExpression regex;
        try {
            regex = LockedSaxon.regex(ex.regex(), flags);
        } catch (final XPathException e) {
            problems.add(where + ": the regex is not a valid XPath regular expression: "
                         + firstLine(e.getMessage(), 200) + "." + (ex.regex().contains("\\\\")
                    ? ESCAPED_TWICE
                    : ""));
            return;
        }
        try {
            SafePattern.limited(ex.regex(), () -> {
                checkMatches(where, ex, flags, regex, texts, problems, warnings);
                return null;
            });
        } catch (final PatternTimeoutException e) {
            problems.add(where + ": " + e.getMessage());
        }
    }

    // Runs an extraction's regex on its texts, within the time limit of [SafePattern#limited(String, Supplier)].
    // Saxon's regex engine copies the text before it matches and checks no time limit, so each text is matched
    // first by the same regex in Java (close to XPath's), reading the text through a DeadlineCharSequence: a regex
    // that backtracks badly is stopped there before Saxon runs it. Saxon's own backtracking limit (lowered in
    // LockedSaxon) stops one the Java one can't read.
    private static void checkMatches(final String where,
                                     final Extraction ex,
                                     final String flags,
                                     final RegularExpression regex,
                                     final List<String> texts,
                                     final List<String> problems,
                                     final List<String> warnings) {
        final Pattern java = javaPattern(ex.regex(), javaFlags(flags));
        int matched = 0;
        String missed = null;
        for (final String text : texts) {
            ToolDeadline.check();
            if (java != null) {
                SafePattern.matcher(java, text).find();
            }
            final boolean found;
            try {
                found = regex.containsMatch(text);
            } catch (final UncheckedXPathException e) {
                // Saxon's backtracking limit.
                LOGGER.debug(() -> "Regex " + ex.regex() + " stopped: " + e.getMessage());
                throw new PatternTimeoutException(ex.regex());
            }
            if (found) {
                matched++;
            } else if (missed == null) {
                missed = text;
            }
        }
        final String names = PythonText.list(ex.names().stream().filter(n -> n != null && !n.isEmpty()).toList());
        if (matched == 0) {
            problems.add(where + ": the regex matches none of the " + texts.size() + " sample texts, so " + names
                         + " would be empty in every event. " + whereItStops(ex.regex(), flags, texts));
        } else if (matched < texts.size()) {
            warnings.add(where + ": the regex matches " + matched + " of the " + texts.size() + " sample texts; "
                         + "the rest leave " + names + " empty. E.g. " + shown(missed, 160) + ": "
                         + whereItStops(ex.regex(), flags, List.of(missed)));
        }
    }

    // The regex as Java reads it, or null if Java can't (XPath has syntax Java doesn't, e.g. [a-z-[aeiou]]).
    private static Pattern javaPattern(final String regex, final int javaFlags) {
        try {
            return SafePattern.compile(regex, javaFlags);
        } catch (final PatternSyntaxException e) {
            return null;
        }
    }

    // The text an xpath source gives in each record document, where it gives any.
    private static List<String> sourceTexts(final String source, final List<XdmNode> parsed, final String namespace) {
        final List<String> texts = new ArrayList<>();
        final XPathCompiler compiler = LockedSaxon.xpath(namespace);
        for (final XdmNode doc : parsed) {
            try {
                final XPathSelector selector = compiler.compile("string((/*/(" + source + "))[1])").load();
                selector.setContextItem(doc);
                final XdmItem value = selector.evaluateSingle();
                final String text = value == null
                        ? ""
                        : value.getStringValue();
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            } catch (final SaxonApiException | RuntimeException e) {
                LOGGER.debug(() -> "Unable to evaluate " + source + ": " + e.getMessage());
            }
        }
        return texts;
    }

    // For a regex that looks for a key anywhere in a line ((?:^|\s)logdesc="([^"]*)"): whether the texts have its
    // key at all. Seen: such a regex was said to match "as far as ``", true at the text's start and no help; the
    // 200 texts read simply had no logdesc=.
    private static String searched(final String regex, final int javaFlags, final List<String> texts) {
        final Matcher lead = LEAD.matcher(regex);
        final int leadEnd = lead.lookingAt()
                ? lead.end()
                : 0;
        final Matcher keyMatcher = KEY.matcher(regex);
        keyMatcher.region(leadEnd, regex.length());
        final String key = keyMatcher.lookingAt()
                ? ESCAPE.matcher(keyMatcher.group()).replaceAll("$1")
                : "";
        if (key.length() < 3) {
            return null;
        }
        final int keyFlags = javaFlags & Pattern.CASE_INSENSITIVE;
        final Pattern keyPattern = Pattern.compile(Pattern.quote(key), keyFlags);
        String text = null;
        int at = -1;
        for (final String candidate : texts) {
            final Matcher matcher = keyPattern.matcher(candidate);
            if (matcher.find()) {
                text = candidate;
                at = matcher.end();
                break;
            }
        }
        if (text == null) {
            return "None of these texts has " + shown(key, 60) + ": a key the sample lacks (its records may be "
                   + "elsewhere in the feed), or one spelt otherwise in the text.";
        }
        // The key is there: how far the rest of the regex goes on from it.
        final String rest = regex.substring(keyMatcher.end());
        final String after = text.substring(at);
        for (int cut = rest.length(); cut > 0; cut--) {
            final Matcher matcher = matcher(rest.substring(0, cut), javaFlags, after);
            if (matcher != null && matcher.lookingAt()) {
                return "It finds " + shown(key + after.substring(0, matcher.end()), 60) + " and stops there: the "
                       + "regex goes on with " + shown(rest.substring(cut), 30) + ", the text with "
                       + shown(after.substring(matcher.end()), 30) + ".";
            }
        }
        return "It finds " + shown(key, 60) + ", then the regex goes on with " + shown(rest, 30) + ", the text with "
               + shown(after, 30) + ".";
    }

    // A matcher that checks the time limit as it reads (see SafePattern), or null if the regex isn't Java's.
    private static Matcher matcher(final String regex, final int javaFlags, final String text) {
        try {
            return SafePattern.matcher(SafePattern.compile(regex, javaFlags), text);
        } catch (final PatternSyntaxException e) {
            return null;
        }
    }

    private static int javaFlags(final String flags) {
        int javaFlags = 0;
        for (final char flag : (flags == null
                ? ""
                : flags).toCharArray()) {
            javaFlags |= switch (flag) {
                case 'i' -> Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                case 'm' -> Pattern.MULTILINE;
                case 's' -> Pattern.DOTALL;
                case 'x' -> Pattern.COMMENTS;
                default -> 0;
            };
        }
        return javaFlags;
    }

    private static List<XdmNode> parseAll(final List<String> documents) {
        final List<XdmNode> parsed = new ArrayList<>();
        for (final String document : documents) {
            try {
                parsed.add(LockedSaxon.parse(document));
            } catch (final SaxonApiException e) {
                LOGGER.debug(() -> "Unable to parse a record: " + e.getMessage());
            }
        }
        return parsed;
    }

    private static List<Object> jsonValues(final String text) {
        final String stripped = stripChars(text, '﻿', '\r', '\n', ' ');
        final List<Object> values = new ArrayList<>();
        try {
            if (stripped.startsWith("[")) {
                final Object array = JsonUtil.readValue(stripped, Object.class);
                if (array instanceof final List<?> list) {
                    list.stream().filter(Map.class::isInstance).forEach(values::add);
                }
                return values;
            }
            for (final String line : stripped.split("\n")) {
                if (!line.isBlank()) {
                    final Object value = JsonUtil.readValue(line, Object.class);
                    if (value instanceof Map) {
                        values.add(value);
                    }
                }
            }
            return values;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Not JSON: " + e.getMessage());
            return List.of();
        }
    }

    private static String valueOf(final Map<String, ?> record, final String field) {
        final Object value = record.get(field);
        if (value == null) {
            return null;
        }
        return value instanceof final String text
                ? text
                : JsonUtil.writeValueAsString(value, false);
    }

    private static String usedFor(final List<String> used) {
        return used.getFirst() + (used.size() > 1
                ? " and more"
                : "");
    }

    private static String firstLine(final String message, final int limit) {
        final String first = message == null
                ? ""
                : message.strip().lines().findFirst().orElse("");
        return first.substring(0, Math.min(limit, first.length()));
    }

    private static String stripChars(final String text, final char... chars) {
        int start = 0;
        int end = text.length();
        while (start < end && contains(chars, text.charAt(start))) {
            start++;
        }
        while (end > start && contains(chars, text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean contains(final char[] chars, final char ch) {
        for (final char c : chars) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }

    private static String escape(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // As Python's xml.sax.saxutils.quoteattr: the value escaped and quoted.
    private static String quoteAttr(final String value) {
        String escaped = escape(value).replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;");
        if (escaped.contains("\"")) {
            if (escaped.contains("'")) {
                escaped = escaped.replace("\"", "&quot;");
            } else {
                return "'" + escaped + "'";
            }
        }
        return "\"" + escaped + "\"";
    }


    // --------------------------------------------------------------------------------


    /// An extraction: a regex run on a field's text or an xpath's, its groups named.
    ///
    /// @param field The field it reads (a Data Splitter's or JSON record's, or one an earlier extraction makes),
    ///              or null.
    /// @param xpath The xpath it reads, where no field is given.
    /// @param regex The XPath regular expression.
    /// @param flags Its flags, or null.
    /// @param names The names of its groups, in order.
    public record Extraction(String field, String xpath, String regex, String flags, List<String> names) {

    }

    /// What the extraction check found.
    ///
    /// @param problems Regexes that match no sample text, or aren't valid.
    /// @param warnings Regexes that match only some, and entries not checked in time.
    public record Findings(List<String> problems, List<String> warnings) {

    }
}
