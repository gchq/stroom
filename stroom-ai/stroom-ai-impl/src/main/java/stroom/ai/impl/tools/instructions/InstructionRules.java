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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
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

/// The sections of AGENTS docs that the tools apply themselves, rather than only hand to the agent. Each is a
/// heading followed by bullets of the form `- key: value, value`, and a doc's section ends at its next heading.
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
/// - `## Fields`: the fields that hold what the tools look for in the data sources where the doc applies, a field
///   (or several, the first an index has used) a line, e.g. for activity timelines (`activity_timeline`):
///   ```markdown
///   ## Fields
///   - user: Initiator.Id, user.name
///   - host: HostName
///   ```
///   The keys: `user`, `person` (`person id`), `target person` (`target person id`), `target user`, `host`,
///   `application` (`process`), `object`, `element` (`event detail`), `action`, `event type` (`event code`), and,
///   for `compare_activity`'s categories and location, `category` and `country`.
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
    /// The field holding the user (or entity) an event is of.
    public static final String USER_FIELD = "user";
    /// The field holding the person an event is of: an id processing decorates each event with, the same for all of
    /// a person's accounts (the user field holds the logon id, which differs by source).
    public static final String PERSON_FIELD = "person";
    /// The field holding the person an event was done to (access granted to them, their password reset, a failed
    /// logon against their account): their person id, as [#PERSON_FIELD] holds the actor's.
    public static final String TARGET_PERSON_FIELD = "target person";
    /// The field holding the account an event was done to, its logon id, as [#USER_FIELD] holds the actor's.
    public static final String TARGET_USER_FIELD = "target user";
    /// The field holding the host an event happened on.
    public static final String HOST_FIELD = "host";
    /// The field holding the application.
    public static final String APPLICATION_FIELD = "application";
    /// The field holding what an event touched (a file, a URL, a subject).
    public static final String OBJECT_FIELD = "object";
    /// The field holding the event-logging EventDetail element an event was written as (e.g. `Authenticate`).
    public static final String ELEMENT_FIELD = "element";
    /// The field holding the event's Action (e.g. `Logon`).
    public static final String ACTION_FIELD = "action";
    /// The field holding the source's own code for the type of event (e.g. a vendor's event id).
    public static final String EVENT_TYPE_FIELD = "event type";
    /// The field holding the source's own category of what an event reached (e.g. a proxy's URL category).
    public static final String CATEGORY_FIELD = "category";
    /// The field holding the country an event came from, as a code (e.g. where a sign-in came from).
    public static final String COUNTRY_FIELD = "country";

    /// The event-logging path of each `## Fields` key the field conventions and the index plans kept with the
    /// indexing XSLTs give a field for, as the timeline tools look for them.
    public static final Map<String, String> CONVENTION_PATHS = Map.of(
            USER_FIELD, "EventSource/User/Id",
            PERSON_FIELD, "EventSource/User/UserDetails/Id",
            TARGET_USER_FIELD, "EventDetail/Authorise/User/Id",
            TARGET_PERSON_FIELD, "EventDetail/Authorise/User/UserDetails/Id",
            HOST_FIELD, "EventSource/Device/HostName",
            ACTION_FIELD, "EventDetail/*/Action",
            EVENT_TYPE_FIELD, "EventDetail/TypeId");

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

    /// The heading of the section of `## Fields` lines.
    public static final String FIELDS_SECTION = "Fields";
    /// The heading of the section of required imports.
    public static final String REQUIRED_IMPORTS_SECTION = "Required imports";
    /// The heading of the section of component template rules.
    public static final String COMPONENT_TEMPLATES_SECTION = "Component templates";
    /// The heading of the section of documentation rules.
    public static final String DOCUMENTATION_SECTION = "Documentation";

    private static final String INCLUDE = "include";
    private static final String EXCLUDE = "exclude";
    static final Map<String, String> COMPONENT_RULES = Map.of(
            "include", INCLUDE,
            "always", INCLUDE,
            "add", INCLUDE,
            "use", INCLUDE,
            "exclude", EXCLUDE,
            "never", EXCLUDE,
            "omit", EXCLUDE,
            "drop", EXCLUDE);
    static final Map<String, String> LAYOUTS = Map.of(
            "by event type", BY_EVENT_TYPE,
            "by_event_type", BY_EVENT_TYPE,
            "per event type", BY_EVENT_TYPE,
            "compact", COMPACT,
            "one table", COMPACT,
            "single table", COMPACT);

    /// What each field may be called in a doc's `## Fields` section.
    static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("user", USER_FIELD),
            Map.entry("person", PERSON_FIELD),
            Map.entry("person id", PERSON_FIELD),
            Map.entry("target person", TARGET_PERSON_FIELD),
            Map.entry("target person id", TARGET_PERSON_FIELD),
            Map.entry("target user", TARGET_USER_FIELD),
            Map.entry("host", HOST_FIELD),
            Map.entry("application", APPLICATION_FIELD),
            Map.entry("process", APPLICATION_FIELD),
            Map.entry("object", OBJECT_FIELD),
            Map.entry("element", ELEMENT_FIELD),
            Map.entry("event detail", ELEMENT_FIELD),
            Map.entry("action", ACTION_FIELD),
            Map.entry("event type", EVENT_TYPE_FIELD),
            Map.entry("event code", EVENT_TYPE_FIELD),
            Map.entry("category", CATEGORY_FIELD),
            Map.entry("country", COUNTRY_FIELD));
    private static final Pattern FIELDS_HEADING = Pattern.compile("^#{1,6}\\s*fields\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern REQUIRED_HEADING = Pattern.compile("^#{1,6}\\s*required imports\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern COMPONENTS_HEADING = Pattern.compile("^#{1,6}\\s*component templates\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DOCUMENTATION_HEADING = Pattern.compile("^#{1,6}\\s*documentation\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern NEXT_HEADING = Pattern.compile("^#{1,6}\\s", Pattern.MULTILINE);
    static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s*([^:]+):\\s*(.+)$");
    static final Pattern LAYOUT_BULLET = Pattern.compile("^\\s*[-*]\\s*field mapping( layout)?\\s*:\\s*(.+)$",
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

    /// The fields the applicable AGENTS docs' `## Fields` sections name: the most specific doc's line for each.
    ///
    /// @param applying The docs that apply, most general first.
    /// @return For each field (e.g. [#USER_FIELD]), the fields named (the first an index has is used) and the
    /// folder of the doc that names them.
    public static Map<String, FieldRule> fields(final List<InstructionDoc> applying) {
        final Map<String, FieldRule> out = new LinkedHashMap<>();
        for (final InstructionDoc doc : NullSafe.list(applying)) {
            fieldLines(doc.text()).forEach((key, names) -> out.put(key, new FieldRule(names, doc.folder())));
        }
        return out;
    }

    /// @param text A doc's text.
    /// @return Its `## Fields` lines the tools know, by field, each with the fields it names in order.
    static Map<String, List<String>> fieldLines(final String text) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        for (final String line : section(text, FIELDS_HEADING).orElse("").split("\\R")) {
            final Matcher bullet = BULLET.matcher(line);
            final String key = bullet.matches()
                    ? FIELDS.get(stripChars(bullet.group(1).strip(), "`*").toLowerCase(Locale.ROOT))
                    : null;
            if (key == null) {
                continue;
            }
            final List<String> names = fieldNames(bullet.group(2));
            if (!names.isEmpty()) {
                out.put(key, List.copyOf(names));
            }
        }
        return out;
    }

    /// The `## Fields` lines of a doc that name no field any of the data sources where it applies has: a slip (e.g.
    /// `Initiator.ID`) the tools would pass over, falling back to the next way of finding the field.
    ///
    /// @param text   A doc's text.
    /// @param fields The fields of the data sources where it applies; empty when none are known, and nothing is said.
    /// @return A problem for each such line.
    public static List<String> fieldProblems(final String text, final Set<String> fields) {
        final List<String> out = new ArrayList<>();
        if (NullSafe.isEmptyCollection(fields)) {
            return out;
        }
        fieldLines(text).forEach((key, names) -> {
            final List<String> missing = names.stream().filter(name -> !fields.contains(name)).toList();
            if (missing.size() == names.size()) {
                out.add("'## Fields' line for " + key + " names no field the data sources where the doc applies have ("
                        + String.join(", ", missing) + ")");
            }
        });
        return out;
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
        keyProblems(out, text, FIELDS_HEADING, "Fields", FIELDS.keySet(), BULLET);
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

    /// @param line A line of a doc.
    /// @return The section the tools read whose heading it is ([#FIELDS_SECTION] and so on), or null if it is no
    /// such heading.
    static String typedSection(final String line) {
        final String text = NullSafe.string(line);
        if (FIELDS_HEADING.matcher(text).matches()) {
            return FIELDS_SECTION;
        } else if (REQUIRED_HEADING.matcher(text).matches()) {
            return REQUIRED_IMPORTS_SECTION;
        } else if (COMPONENTS_HEADING.matcher(text).matches()) {
            return COMPONENT_TEMPLATES_SECTION;
        } else if (DOCUMENTATION_HEADING.matcher(text).matches()) {
            return DOCUMENTATION_SECTION;
        }
        return null;
    }

    /// @param line A line of a doc.
    /// @return True if the tools take it as a heading, ending the section above it.
    static boolean isHeading(final String line) {
        return NEXT_HEADING.matcher(NullSafe.string(line)).find() || typedSection(line) != null;
    }

    /// @param section A section the tools read, e.g. [#FIELDS_SECTION].
    /// @return The keys its `- key: value` lines may have (for [#DOCUMENTATION_SECTION], its layouts).
    static Set<String> keysOf(final String section) {
        return switch (section) {
            case FIELDS_SECTION -> FIELDS.keySet();
            case REQUIRED_IMPORTS_SECTION -> STAGES.keySet();
            case COMPONENT_TEMPLATES_SECTION -> COMPONENT_RULES.keySet();
            case DOCUMENTATION_SECTION -> LAYOUTS.keySet();
            default -> Set.of();
        };
    }

    /// @param key A line's key as written, e.g. `` `Person ID` ``.
    /// @return It as the tools compare it: without backticks or asterisks at either end, lower case.
    static String normaliseKey(final String key) {
        return stripChars(NullSafe.string(key).strip(), "`*").toLowerCase(Locale.ROOT);
    }

    /// @param value A `## Fields` line's value, e.g. `` `user.name`, UserId ``.
    /// @return The fields it names, in order, as the tools read them.
    static List<String> fieldNames(final String value) {
        final List<String> names = new ArrayList<>();
        for (final String item : NullSafe.string(value).split(",")) {
            final String name = stripChars(item.strip(), "`*.").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    // The text without any of the characters at either end.
    static String stripChars(final String text, final String chars) {
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


    /// The fields an AGENTS doc's `## Fields` line names for one thing.
    ///
    /// @param fields The fields, in order: the first a data source has is used.
    /// @param from   The folder of the AGENTS doc that names them.
    public record FieldRule(List<String> fields, String from) {

        /// Copies the list.
        public FieldRule {
            fields = List.copyOf(fields);
        }
    }


    // --------------------------------------------------------------------------------


    /// How a documentation's Field mapping is laid out.
    ///
    /// @param layout [#BY_EVENT_TYPE] or [#COMPACT].
    /// @param from   The folder of the AGENTS doc that says so.
    public record FieldMappingLayout(String layout, String from) {

    }
}
