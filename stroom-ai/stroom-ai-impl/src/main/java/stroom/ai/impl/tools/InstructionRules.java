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

import stroom.ai.impl.tools.AgentInstructions.InstructionDoc;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The sections of AGENTS docs that the tools apply themselves, rather than only hand to the agent, as the Stroom
/// MCP server reads them. Each is a heading followed by bullets of the form `- key: value, value`, and a doc's
/// section ends at its next heading.
///
/// - `## Required imports`: shared XSLTs every XSLT of a stage imports (asked for by the user), e.g. Raw Events to
///   Events translations importing Stream-Meta-V1:
///   ```markdown
///   ## Required imports
///   - translation: Stream-Meta-V1
///   - indexing: Common-Index-V1, Lookups-V2
///   ```
/// - `## Component templates`: component templates an environment's index templates always or never compose
///   (asked for by the user):
///   ```markdown
///   ## Component templates
///   - include: logs-ilm-90d, org-base
///   - exclude: legacy-base
///   ```
/// - `## Documentation`: how a pipeline's documentation lays out its Field mapping (asked for by the user): by
///   event type (the default: the fields common to every event type in one table, then a table per event type),
///   or compact:
///   ```markdown
///   ## Documentation
///   - field mapping: compact
///   ```
///
/// The docs are given most general first, as [AgentInstructions.Standing#applyingTo] gives them.
public final class InstructionRules {

    /// The stage of the translation XSLTs (Raw Events to Events).
    public static final String TRANSLATION = "translation";
    /// The stage of the XSLTs that write records (Raw Events to Records).
    public static final String RECORDS = "records";
    /// The stage of the indexing XSLTs.
    public static final String INDEXING = "indexing";
    /// The stage of the reference data XSLTs.
    public static final String REFERENCE = "reference";
    /// The stage of the CEF (forwarding) XSLTs.
    public static final String CEF = "cef";
    /// A Field mapping laid out by event type: the common fields in one table, then a table per event type.
    public static final String BY_EVENT_TYPE = "by_event_type";
    /// A Field mapping laid out as one table.
    public static final String COMPACT = "compact";

    /// What each stage may be called in a doc.
    static final Map<String, String> STAGES = Map.ofEntries(
            Map.entry("translation", TRANSLATION),
            Map.entry("translations", TRANSLATION),
            Map.entry("events", TRANSLATION),
            Map.entry("raw events to events", TRANSLATION),
            Map.entry("raw event to event", TRANSLATION),
            Map.entry("records", RECORDS),
            Map.entry("raw events to records", RECORDS),
            Map.entry("raw event to record", RECORDS),
            Map.entry("indexing", INDEXING),
            Map.entry("reference", REFERENCE),
            Map.entry("reference data", REFERENCE),
            Map.entry("cef", CEF),
            Map.entry("forwarding", CEF));

    private static final String INCLUDE = "include";
    private static final String EXCLUDE = "exclude";
    private static final Map<String, String> COMPONENT_RULES = Map.of(
            "include", INCLUDE,
            "always", INCLUDE,
            "add", INCLUDE,
            "use", INCLUDE,
            "exclude", EXCLUDE,
            "never", EXCLUDE,
            "omit", EXCLUDE,
            "drop", EXCLUDE);
    private static final Map<String, String> LAYOUTS = Map.of(
            "by event type", BY_EVENT_TYPE,
            "by_event_type", BY_EVENT_TYPE,
            "per event type", BY_EVENT_TYPE,
            "compact", COMPACT,
            "one table", COMPACT,
            "single table", COMPACT);

    private static final Pattern REQUIRED_HEADING = Pattern.compile("^#{1,6}\\s*required imports\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern COMPONENTS_HEADING = Pattern.compile("^#{1,6}\\s*component templates\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DOCUMENTATION_HEADING = Pattern.compile("^#{1,6}\\s*documentation\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern NEXT_HEADING = Pattern.compile("^#{1,6}\\s", Pattern.MULTILINE);
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s*([^:]+):\\s*(.+)$");
    private static final Pattern LAYOUT_BULLET = Pattern.compile("^\\s*[-*]\\s*field mapping( layout)?\\s*:\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private InstructionRules() {
        // Static only.
    }

    /// The imports the applicable AGENTS docs require, by stage. A line naming no known stage is left out, and an
    /// import is given once, from the first (most general) doc that requires it.
    ///
    /// @param applying The docs that apply, most general first.
    /// @return For each stage (e.g. [#TRANSLATION]), the XSLTs (by name, as an `xsl:import` href) its XSLTs must
    /// import, each with the folder of the doc that requires it.
    public static Map<String, List<RequiredImport>> requiredImports(final List<InstructionDoc> applying) {
        final Map<String, List<RequiredImport>> out = new LinkedHashMap<>();
        for (final InstructionDoc doc : NullSafe.list(applying)) {
            for (final String line : section(doc.text(), REQUIRED_HEADING).orElse("").split("\\R")) {
                final Matcher bullet = BULLET.matcher(line);
                final String stage = bullet.matches()
                        ? STAGES.get(stripChars(bullet.group(1).strip(), "`*").toLowerCase(Locale.ROOT))
                        : null;
                if (stage == null) {
                    continue;
                }
                for (final String item : bullet.group(2).split(",")) {
                    final String href = stripChars(item.strip(), "`").strip();
                    final List<RequiredImport> imports = out.computeIfAbsent(stage, k -> new ArrayList<>());
                    if (!href.isEmpty() && imports.stream().noneMatch(found -> found.href().equals(href))) {
                        imports.add(new RequiredImport(href, doc.folder()));
                    }
                }
            }
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    /// The component templates the applicable AGENTS docs say index templates always or never compose. A more
    /// specific doc's rule for a name overrides a more general one's.
    ///
    /// @param applying The docs that apply, most general first.
    /// @return The names to include and to exclude, each with the folder of the doc whose rule it is.
    public static ComponentRules componentRules(final List<InstructionDoc> applying) {
        final Map<String, String[]> rules = new LinkedHashMap<>();
        for (final InstructionDoc doc : NullSafe.list(applying)) {
            for (final String line : section(doc.text(), COMPONENTS_HEADING).orElse("").split("\\R")) {
                final Matcher bullet = BULLET.matcher(line);
                final String kind = bullet.matches()
                        ? COMPONENT_RULES.get(stripChars(bullet.group(1).strip(), "`*").toLowerCase(Locale.ROOT))
                        : null;
                if (kind == null) {
                    continue;
                }
                for (final String item : bullet.group(2).split(",")) {
                    final String name = stripChars(item.strip(), "`").strip();
                    if (!name.isEmpty()) {
                        rules.put(name, new String[]{kind, doc.folder()});
                    }
                }
            }
        }
        final List<ComponentRule> include = new ArrayList<>();
        final List<ComponentRule> exclude = new ArrayList<>();
        rules.forEach((name, rule) -> (INCLUDE.equals(rule[0])
                ? include
                : exclude).add(new ComponentRule(name, rule[1])));
        return new ComponentRules(List.copyOf(include), List.copyOf(exclude));
    }

    /// How the applicable AGENTS docs say a pipeline's documentation lays out its Field mapping: the most specific
    /// doc's word.
    ///
    /// @param applying The docs that apply, most general first.
    /// @return The layout ([#BY_EVENT_TYPE] or [#COMPACT]) and the folder of the doc that says so, or empty if
    /// none says.
    public static Optional<FieldMappingLayout> fieldMappingLayout(final List<InstructionDoc> applying) {
        FieldMappingLayout found = null;
        for (final InstructionDoc doc : NullSafe.list(applying)) {
            for (final String line : section(doc.text(), DOCUMENTATION_HEADING).orElse("").split("\\R")) {
                final Matcher bullet = LAYOUT_BULLET.matcher(line);
                final String layout = bullet.matches()
                        ? LAYOUTS.get(stripChars(bullet.group(2).strip(), "`*.").toLowerCase(Locale.ROOT))
                        : null;
                if (layout != null) {
                    found = new FieldMappingLayout(layout, doc.folder());
                }
            }
        }
        return Optional.ofNullable(found);
    }

    /// What in a doc's sections the tools apply themselves they couldn't apply: a bullet naming no stage, rule or
    /// layout they know, which they leave out without saying. Fine for a note; a slip (e.g. 'indexng') would be a
    /// rule that does nothing, so a proposed doc's are pointed out.
    ///
    /// @param text A doc's text.
    /// @return A problem for each such line.
    public static List<String> problems(final String text) {
        final List<String> out = new ArrayList<>();
        keyProblems(out, text, REQUIRED_HEADING, "Required imports", STAGES.keySet(), BULLET);
        keyProblems(out, text, COMPONENTS_HEADING, "Component templates", COMPONENT_RULES.keySet(), BULLET);
        for (final String line : section(text, DOCUMENTATION_HEADING).orElse("").split("\\R")) {
            final Matcher bullet = LAYOUT_BULLET.matcher(line);
            if (bullet.matches()
                && !LAYOUTS.containsKey(stripChars(bullet.group(2).strip(), "`*.").toLowerCase(Locale.ROOT))) {
                out.add("'## Documentation' line '" + line.strip() + "' names no layout the tools know ("
                        + String.join(", ", new TreeSet<>(LAYOUTS.keySet())) + ")");
            }
        }
        return out;
    }

    private static void keyProblems(final List<String> out,
                                    final String text,
                                    final Pattern heading,
                                    final String name,
                                    final Set<String> keys,
                                    final Pattern bulletPattern) {
        for (final String line : section(text, heading).orElse("").split("\\R")) {
            final Matcher bullet = bulletPattern.matcher(line);
            if (bullet.matches()
                && !keys.contains(stripChars(bullet.group(1).strip(), "`*").toLowerCase(Locale.ROOT))) {
                out.add("'## " + name + "' line '" + line.strip() + "' names none the tools know ("
                        + String.join(", ", new TreeSet<>(keys)) + "), so it would be left out");
            }
        }
    }

    /// @param text    A doc's text.
    /// @param heading The section's heading.
    /// @return The text below the heading up to the next heading, or empty if the doc has no such section.
    static Optional<String> section(final String text, final Pattern heading) {
        final Matcher found = heading.matcher(NullSafe.string(text));
        if (!found.find()) {
            return Optional.empty();
        }
        final String rest = text.substring(found.end());
        final Matcher next = NEXT_HEADING.matcher(rest);
        return Optional.of(next.find()
                ? rest.substring(0, next.start())
                : rest);
    }

    // The text without any of the characters at either end, as Python's str.strip(chars) does.
    private static String stripChars(final String text, final String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }


    // --------------------------------------------------------------------------------


    /// A shared XSLT the XSLTs of a stage must import.
    ///
    /// @param href The XSLT's name, as an `xsl:import` href, e.g. `Stream-Meta-V1`.
    /// @param from The folder of the AGENTS doc that requires it, e.g. `/Feeds`.
    public record RequiredImport(String href, String from) {

    }


    // --------------------------------------------------------------------------------


    /// A component template an AGENTS doc says to include or exclude.
    ///
    /// @param name The component template's name.
    /// @param from The folder of the AGENTS doc whose rule it is.
    public record ComponentRule(String name, String from) {

    }


    // --------------------------------------------------------------------------------


    /// The component templates to include and exclude.
    ///
    /// @param include Those index templates always compose.
    /// @param exclude Those they never compose.
    public record ComponentRules(List<ComponentRule> include, List<ComponentRule> exclude) {

    }


    // --------------------------------------------------------------------------------


    /// How a documentation's Field mapping is laid out.
    ///
    /// @param layout [#BY_EVENT_TYPE] or [#COMPACT].
    /// @param from   The folder of the AGENTS doc that says so.
    public record FieldMappingLayout(String layout, String from) {

    }
}
