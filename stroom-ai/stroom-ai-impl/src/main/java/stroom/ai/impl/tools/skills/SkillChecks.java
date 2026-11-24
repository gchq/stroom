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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.BooleanSchema;
import stroom.ai.impl.tools.ToolSchema.EnumSchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.impl.tools.skills.SkillDoc.ExampleCall;
import stroom.ai.impl.tools.skills.SkillDoc.Parameter;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;
import stroom.ai.impl.tools.skills.SkillDoc.Step;
import stroom.ai.impl.tools.timeline.EventChains;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The checks on a skill, as `check_skill` makes them and as a skill is checked when it is read to run: problems
/// (it won't work as written) and suggestions (it would work better changed), each with a proposed fix. What the
/// checks need of Stroom (the tools, the reader's policies, data sources, other skills, how the skill was used) is
/// asked of [Facts], so the rules are tested on their own.
final class SkillChecks {

    /// The fewest phrasings a "When to use" should have.
    static final int MIN_PHRASINGS = 2;
    /// How alike two skills' words must be (shared over all, 0 to 1) to be near-duplicates.
    static final double NEAR_DUPLICATE = 0.5;
    /// How many times a skill offered and never chosen is pointed out.
    static final int OFTEN_OFFERED = 5;
    /// The chart kind the chat draws itself; anything else is a dashboard's.
    static final String CHAT_CHART = "gantt";

    private static final Set<String> DATA_SOURCE_KEYS = Set.of("index", "data_source", "dataSource");
    private static final Pattern FROM = Pattern.compile("(?i)\\bfrom\\s+(\"([^\"]+)\"|'([^']+)'|([^\\s,()]+))");
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}(T[\\d:.]+Z?)?\\b");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]+");
    private static final Pattern TOOL_NAME = Pattern.compile("\\b([a-z]+(?:_[a-z]+)+)\\b");
    private static final Pattern CHART_WORDS = Pattern.compile(
            "(?i)\\b(pie|bar|line|area|scatter|heat ?map|network|graph|sankey|treemap|histogram|map|xychart"
            + "(?:-beta)?)\\s+(chart|diagram|plot)?");
    private static final Pattern MERMAID = Pattern.compile("(?s)```mermaid\\s*\\n\\s*([A-Za-z-]+)");
    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "from", "that", "this",
            "each", "its", "into", "then", "them", "they", "their", "what", "which", "when", "where", "who", "all",
            "any", "are", "was", "has", "have", "use", "using", "used", "give", "gives", "given", "one", "two",
            "per", "not", "but", "out", "how", "than", "more", "most", "find", "show", "call", "tool", "step");

    private SkillChecks() {
        // Static only.
    }

    /// @param skill The skill.
    /// @param doc   The doc it is in, for its history, or null for a draft.
    /// @param facts What the checks need of Stroom.
    /// @return The problems, then the suggestions.
    static List<Finding> check(final Skill skill, final SkillDoc doc, final Facts facts) {
        final List<Finding> findings = new ArrayList<>();
        structure(skill, findings);
        parameters(skill, findings);
        calls(skill, facts, findings);
        if (doc != null && doc.isEditedSinceLatestEntry()) {
            final int version = doc.latestEntry().map(SkillDoc.HistoryEntry::version).orElse(0);
            findings.add(Finding.problem("hand_edit", "The doc has been changed by hand since its latest history "
                                                      + "entry (version " + version + ").",
                    "Record it: propose_skill action=record (adds an entry with how: user)."));
        }
        vagueSteps(skill, facts, findings);
        hardCodedValues(skill, findings);
        charts(skill, findings);
        whenToUse(skill, findings);
        nearDuplicates(skill, facts, findings);
        usage(skill, facts, findings);
        eventChains(skill, facts, findings);
        findings.sort(Comparator.comparing(Finding::kind));
        return List.copyOf(findings);
    }

    // A timeline skill's Event chains: each line an entry, each source an index the reader can use, and the entries
    // still true of recent data (their types seen, a pair's key held and alternating).
    private static void eventChains(final Skill skill, final Facts facts, final List<Finding> findings) {
        final String section = skill.sections().get(EventChains.SECTION);
        if (section == null) {
            return;
        }
        final EventChains.Parsed parsed = EventChains.parse(section);
        for (final String line : parsed.unread()) {
            findings.add(Finding.problem("event_chains", "Event chains line '" + line + "' isn't an entry, so the "
                                                         + "timeline leaves it out.",
                    "Write it as '- <index>: pair <start type> -> <end type> by <key path>' or '- <index>: <type> "
                    + "is active|idle|open|close|action|ignore'."));
        }
        final List<EventChains.Entry> usable = new ArrayList<>();
        for (final Map.Entry<String, List<EventChains.Entry>> source : parsed.bySource().entrySet()) {
            if (EventChains.ANY.equalsIgnoreCase(source.getKey()) || facts.dataSource(source.getKey()).usable()) {
                usable.addAll(source.getValue());
            } else {
                findings.add(Finding.problem("event_chains", "Event chains name '" + source.getKey() + "', which "
                                                             + "isn't a data source you can use.",
                        "Name the index as list_data_sources gives it."));
            }
        }
        for (final String problem : facts.eventChains(usable)) {
            findings.add(Finding.problem("event_chains", problem, "Correct the entry, or remove it so the timeline "
                                                                  + "infers the pair again."));
        }
    }

    /// @param findings Findings.
    /// @return Only the problems.
    static List<Finding> problems(final List<Finding> findings) {
        return findings.stream().filter(finding -> finding.kind() == Kind.PROBLEM).toList();
    }

    /// @param findings Findings.
    /// @return The findings as text, a line each, the problems first; "No problems or suggestions." if none.
    static String describe(final List<Finding> findings) {
        if (findings.isEmpty()) {
            return "No problems or suggestions.";
        }
        final StringBuilder sb = new StringBuilder();
        for (final Finding finding : findings) {
            sb.append("- ").append(finding.kind() == Kind.PROBLEM
                            ? "Problem"
                            : "Suggestion")
                    .append(" (").append(finding.rule()).append("): ").append(finding.message());
            if (NullSafe.isNonBlankString(finding.fix())) {
                sb.append(" Fix: ").append(finding.fix());
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------------------------------------
    // Structure and parameters

    private static void structure(final Skill skill, final List<Finding> findings) {
        if (skill.description().isBlank()) {
            findings.add(Finding.problem("no_description", "It has no description.",
                    "Add a paragraph under '# " + skill.name() + "' saying what it does, in a sentence or two: "
                    + "it is what the agent is shown to choose it."));
        }
        if (skill.steps().isEmpty()) {
            findings.add(Finding.problem("no_steps", "It has no steps.",
                    "Add '## Steps' with numbered steps, an example call in fenced JSON where a tool is used."));
        }
        for (final String placeholder : skill.placeholders()) {
            findings.add(Finding.problem("unfilled_placeholder", "The template's placeholder [[" + placeholder
                                                                 + "]] is still to fill.",
                    "Put in what it stands for at this site (e.g. an index, its role conditions)."));
        }
    }

    private static void parameters(final Skill skill, final List<Finding> findings) {
        for (final String used : skill.used()) {
            if (skill.parameter(used).isEmpty()) {
                findings.add(Finding.problem("undeclared_parameter", "{" + used + "} is used but not listed under "
                                                                     + "'## Parameters'.",
                        "Add '- " + used + " (required): <what it is>' (or '(default <value>)') to '## "
                        + "Parameters'."));
            }
        }
        for (final Parameter parameter : skill.parameters()) {
            if (!skill.used().contains(parameter.name())) {
                findings.add(Finding.problem("unused_parameter", "Parameter '" + parameter.name() + "' is listed "
                                                                 + "but no step uses it.",
                        "Use {" + parameter.name() + "} where it goes in a step or example call, or remove it."));
            }
            if (!parameter.stated()) {
                findings.add(Finding.suggestion("parameter_unstated", "Parameter '" + parameter.name() + "' doesn't "
                                                                      + "say whether it is required or its "
                                                                      + "default, so it is taken as required.",
                        "Write it as '- " + parameter.name() + " (required): ...' or '- " + parameter.name()
                        + " (default <value>): ...'."));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Example calls

    private static void calls(final Skill skill, final Facts facts, final List<Finding> findings) {
        for (final ExampleCall call : skill.calls()) {
            final String where = "Step " + call.step() + "'s example call";
            if (call.problem() != null) {
                findings.add(Finding.problem("bad_call", where + " can't be read: " + call.problem() + ".",
                        "Write it as {\"tool\": \"<name>\", \"arguments\": {...}} in a ```json block."));
                continue;
            }
            final Optional<ToolDefinition> definition = facts.tool(call.tool());
            if (definition.isEmpty()) {
                findings.add(Finding.problem("unknown_tool", where + " uses '" + call.tool() + "', which isn't a "
                                                             + "tool.",
                        closestTool(call.tool(), facts)
                                .map(name -> "Did you mean '" + name + "'?")
                                .orElse("Use one of the tools you are offered.")));
                continue;
            }
            if (!facts.allowed(call.tool())) {
                findings.add(Finding.problem("tool_not_allowed", where + " uses '" + call.tool() + "', which your "
                                                                 + "tool policies don't allow.",
                        "Ask an administrator for the policy, or do the step another way."));
            }
            final List<String> offSchema = new ArrayList<>();
            schema(call.arguments(), definition.get().parameters(), "", offSchema);
            if (!offSchema.isEmpty()) {
                findings.add(Finding.problem("call_off_schema", where + " doesn't fit " + call.tool() + "'s "
                                                                + "parameters: " + String.join("; ", offSchema)
                                                                + ".",
                        "Correct the arguments to " + call.tool() + "'s parameters: "
                        + String.join(", ", definition.get().parameters().properties().keySet()) + "."));
            }
            dataSources(call, facts, findings);
        }
    }

    /// Checks arguments against a tool's schema, as far as a skill's example call can be: a value that is a
    /// parameter (`"{days}"`) or a template's placeholder fits any type.
    ///
    /// @param value  The value.
    /// @param schema Its schema.
    /// @param path   Where it is, for the messages (empty at the top).
    /// @param out    What doesn't fit, added to.
    static void schema(final Object value, final ToolSchema schema, final String path, final List<String> out) {
        if (value == null || isStandIn(value)) {
            return;
        }
        final String at = path.isEmpty()
                ? "the arguments"
                : "'" + path + "'";
        switch (schema) {
            case final ObjectSchema object -> {
                if (!(value instanceof final Map<?, ?> map)) {
                    out.add(at + " should be an object");
                    return;
                }
                if (object.properties().isEmpty()) {
                    return;
                }
                for (final Map.Entry<?, ?> entry : map.entrySet()) {
                    final String key = String.valueOf(entry.getKey());
                    final ToolSchema property = object.properties().get(key);
                    if (property == null) {
                        out.add("'" + join(path, key) + "' isn't a parameter");
                    } else {
                        schema(entry.getValue(), property, join(path, key), out);
                    }
                }
                for (final String required : NullSafe.list(object.required())) {
                    if (!map.containsKey(required)) {
                        out.add("'" + join(path, required) + "' is required");
                    }
                }
            }
            case final ArraySchema array -> {
                if (!(value instanceof final List<?> list)) {
                    out.add(at + " should be a list");
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    if (array.items() != null) {
                        schema(list.get(i), array.items(), path + "[" + i + "]", out);
                    }
                }
            }
            case final StringSchema ignored -> {
                if (!(value instanceof String)) {
                    out.add(at + " should be text");
                }
            }
            case final IntegerSchema ignored -> {
                if (!(value instanceof Integer || value instanceof Long)) {
                    out.add(at + " should be a whole number");
                }
            }
            case final BooleanSchema ignored -> {
                if (!(value instanceof Boolean)) {
                    out.add(at + " should be true or false");
                }
            }
            case final EnumSchema enumSchema -> {
                if (!enumSchema.values().contains(String.valueOf(value))) {
                    out.add(at + " should be one of " + String.join(", ", enumSchema.values()));
                }
            }
        }
    }

    private static void dataSources(final ExampleCall call, final Facts facts, final List<Finding> findings) {
        final String where = "Step " + call.step() + "'s example call";
        walk(call.arguments(), node -> {
            for (final String key : DATA_SOURCE_KEYS) {
                if (node.get(key) instanceof final String name && !isStandIn(name) && !hasStandIn(name)) {
                    final DataSource dataSource = facts.dataSource(name.strip());
                    if (!dataSource.usable()) {
                        findings.add(Finding.problem("missing_data_source", where + " names data source '" + name
                                                                            + "', which doesn't exist or you "
                                                                            + "can't Use.",
                                "Use a data source list_data_sources gives, or make it a parameter."));
                    } else {
                        NullSafe.consume(dataSource.fields(), known -> fieldsOf(node).stream()
                                .filter(field -> !hasStandIn(field) && !known.contains(field))
                                .distinct()
                                .forEach(field -> findings.add(Finding.problem("missing_field", where
                                        + " uses field '" + field + "', which data source '" + name
                                        + "' doesn't have.", "Use one of its fields (describe_data_source)."))));
                    }
                }
            }
            if (node.get("query") instanceof final String query) {
                final Matcher from = FROM.matcher(query);
                if (from.find()) {
                    final String name = first(from.group(2), from.group(3), from.group(4));
                    if (!hasStandIn(name) && !facts.dataSource(name).usable()) {
                        findings.add(Finding.problem("missing_data_source", where + "'s query is from '" + name
                                                                            + "', which doesn't exist or you "
                                                                            + "can't Use.",
                                "Use a data source list_data_sources gives, or make it a parameter."));
                    }
                }
            }
        });
    }

    // The fields an object of a call's arguments names: its fields, by, field and where conditions' fields.
    private static List<String> fieldsOf(final Map<?, ?> node) {
        final List<String> fields = new ArrayList<>();
        for (final String key : List.of("fields", "by")) {
            if (node.get(key) instanceof final List<?> list) {
                list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(fields::add);
            }
        }
        if (node.get("field") instanceof final String field) {
            fields.add(field);
        }
        if (node.get("where") instanceof final List<?> conditions) {
            for (final Object condition : conditions) {
                if (condition instanceof final Map<?, ?> map && map.get("field") instanceof final String field) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    // ------------------------------------------------------------------------------------------------
    // Suggestions

    private static void vagueSteps(final Skill skill, final Facts facts, final List<Finding> findings) {
        for (final Step step : skill.steps()) {
            if (step.number() == 0 || !step.calls().isEmpty()) {
                continue;
            }
            final Matcher named = TOOL_NAME.matcher(step.text());
            boolean namesTool = false;
            while (named.find() && !namesTool) {
                namesTool = facts.tool(named.group(1)).isPresent();
            }
            if (!namesTool) {
                final Optional<String> tool = facts.closestByPurpose(words(step.text()));
                findings.add(Finding.suggestion("vague_step", "Step " + step.number() + " names no tool and has "
                                                              + "no example call, so it may be done differently "
                                                              + "each time.",
                        tool.map(name -> "Say how, e.g. with " + example(name, facts) + ".")
                                .orElse("Say which tool does it, with an example call.")));
            }
        }
    }

    private static void hardCodedValues(final Skill skill, final List<Finding> findings) {
        final String asked = String.join(" ", skill.whenToUse()).toLowerCase(Locale.ROOT);
        final Set<String> seen = new HashSet<>();
        for (final ExampleCall call : skill.calls()) {
            walkValues(call.arguments(), value -> {
                if (hasStandIn(value) || !seen.add(value)) {
                    return;
                }
                final Matcher date = ISO_DATE.matcher(value);
                if (date.find()) {
                    findings.add(Finding.suggestion("hard_coded_value", "Step " + call.step() + " has the time '"
                                                                        + date.group() + "' written in.",
                            "Make the period a parameter (e.g. '- days (default 7): ...' and "
                            + "\"timeFrom\": \"day()-{days}d\")."));
                    return;
                }
                for (final String quoted : quotedLiterals(value)) {
                    if (quoted.length() > 2 && asked.contains(quoted.toLowerCase(Locale.ROOT))) {
                        findings.add(hardCoded(call, quoted));
                    }
                }
                if (value.length() > 2 && !value.contains(" ") && asked.contains(value.toLowerCase(Locale.ROOT))) {
                    findings.add(hardCoded(call, value));
                }
            });
        }
    }

    private static Finding hardCoded(final ExampleCall call, final String value) {
        return Finding.suggestion("hard_coded_value", "Step " + call.step() + " has '" + value + "' written in, "
                                                      + "which the 'When to use' phrasings ask about: it varies "
                                                      + "with the question.",
                "Make it a parameter: '- <name> (required): ...' and {<name>} in place of '" + value + "'.");
    }

    private static void charts(final Skill skill, final List<Finding> findings) {
        final String text = skill.output() + "\n" + NullSafe.string(skill.sections().get(SkillDoc.STEPS));
        final Set<String> kinds = new LinkedHashSet<>();
        final Matcher mermaid = MERMAID.matcher(text);
        while (mermaid.find()) {
            if (!CHAT_CHART.equalsIgnoreCase(mermaid.group(1))) {
                kinds.add(mermaid.group(1));
            }
        }
        final String output = skill.output().toLowerCase(Locale.ROOT);
        if (output.contains("chat") && !output.contains("dashboard")) {
            final Matcher chart = CHART_WORDS.matcher(skill.output());
            while (chart.find()) {
                if (chart.group(2) != null || chart.group(1).toLowerCase(Locale.ROOT).contains("map")) {
                    kinds.add(chart.group().strip());
                }
            }
        }
        for (final String kind : kinds) {
            findings.add(Finding.suggestion("chart_outside_subset", "Its output is a " + kind + " in the chat, but "
                                                                    + "the chat draws only timelines (" + CHAT_CHART
                                                                    + ").",
                    "Make it a dashboard ('## Output' saying so), or a timeline where that shows the same."));
        }
    }

    private static void whenToUse(final Skill skill, final List<Finding> findings) {
        final long words = skill.whenToUse().stream().mapToLong(line -> words(line).size()).sum();
        if (skill.whenToUse().size() < MIN_PHRASINGS || words < 6) {
            findings.add(Finding.suggestion("thin_when_to_use", "Its 'When to use' has " + skill.whenToUse().size()
                                                                + " phrasing(s): it is what the agent matches a "
                                                                + "question against.",
                    "List at least " + MIN_PHRASINGS + " ways people ask for it, in their words, under '## When to "
                    + "use'."));
        }
    }

    private static void nearDuplicates(final Skill skill, final Facts facts, final List<Finding> findings) {
        final Set<String> own = words(skill.name() + " " + skill.description());
        for (final OtherSkill other : facts.otherSkills()) {
            if (other.name().equalsIgnoreCase(skill.name()) && other.isSame()) {
                continue;
            }
            final double alike = alike(own, words(other.name() + " " + other.description()));
            if (alike >= NEAR_DUPLICATE) {
                findings.add(Finding.suggestion("near_duplicate", "It is much like '" + other.name() + "' (in "
                                                                  + other.folder() + ").",
                        "Merge them, or say in each description how they differ."));
            }
        }
    }

    private static void usage(final Skill skill, final Facts facts, final List<Finding> findings) {
        final SkillUsage.Counts counts = facts.usage(skill.name());
        if (counts == null) {
            return;
        }
        if (counts.offered() >= OFTEN_OFFERED && counts.chosen() == 0) {
            findings.add(Finding.suggestion("offered_never_chosen", "It was offered for " + counts.offered()
                                                                    + " of your questions and never chosen.",
                    "Its description or 'When to use' may mislead: say more exactly what it does and when."));
        }
        if (!counts.askedNotOffered().isEmpty()) {
            findings.add(Finding.suggestion("asked_not_offered", "It was asked for by name " + counts
                    .askedNotOffered().size() + " time(s) when its description and 'When to use' didn't match the "
                                                                 + "question, e.g. '" + counts.askedNotOffered()
                                                                         .getFirst() + "'.",
                    "Add how they asked to '## When to use'."));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    /// @param text Some text.
    /// @return Its words of three letters or more, in lower case, without common ones.
    static Set<String> words(final String text) {
        final Set<String> words = new LinkedHashSet<>();
        final Matcher matcher = WORD.matcher(NullSafe.string(text));
        while (matcher.find()) {
            final String word = matcher.group().toLowerCase(Locale.ROOT);
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /// @return How alike two sets of words are: those shared over all of them, 0 to 1.
    static double alike(final Set<String> a, final Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        final Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        final Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) shared.size() / all.size();
    }

    private static boolean isStandIn(final Object value) {
        return value instanceof final String text
               && (SkillDoc.PARAMETER_USE.matcher(text.strip()).matches()
                   || SkillDoc.PLACEHOLDER.matcher(text.strip()).matches());
    }

    private static boolean hasStandIn(final String text) {
        return SkillDoc.PARAMETER_USE.matcher(text).find() || SkillDoc.PLACEHOLDER.matcher(text).find();
    }

    private static List<String> quotedLiterals(final String text) {
        final List<String> literals = new ArrayList<>();
        final Matcher matcher = Pattern.compile("'([^']+)'|\"([^\"]+)\"").matcher(text);
        while (matcher.find()) {
            literals.add(first(matcher.group(1), matcher.group(2)));
        }
        return literals;
    }

    private static String first(final String... values) {
        for (final String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static String join(final String path, final String key) {
        return path.isEmpty()
                ? key
                : path + "." + key;
    }

    private static Optional<String> closestTool(final String name, final Facts facts) {
        final Set<String> parts = Set.of(name.toLowerCase(Locale.ROOT).split("_"));
        return facts.toolNames().stream()
                .filter(candidate -> Set.of(candidate.split("_")).stream().anyMatch(parts::contains))
                .max(Comparator.comparingDouble(candidate -> alike(parts, Set.of(candidate.split("_")))));
    }

    private static String example(final String tool, final Facts facts) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        facts.tool(tool).ifPresent(definition -> NullSafe.list(definition.parameters().required())
                .forEach(required -> arguments.put(required, "...")));
        final Map<String, Object> call = new LinkedHashMap<>();
        call.put("tool", tool);
        call.put("arguments", arguments);
        return JsonUtil.writeValueAsString(call, false);
    }

    @SuppressWarnings("unchecked")
    private static void walk(final Object value, final Consumer<Map<String, Object>> visitor) {
        if (value instanceof final Map<?, ?> map) {
            visitor.accept((Map<String, Object>) map);
            map.values().forEach(child -> walk(child, visitor));
        } else if (value instanceof final List<?> list) {
            list.forEach(child -> walk(child, visitor));
        }
    }

    private static void walkValues(final Object value, final Consumer<String> visitor) {
        if (value instanceof final Map<?, ?> map) {
            map.values().forEach(child -> walkValues(child, visitor));
        } else if (value instanceof final List<?> list) {
            list.forEach(child -> walkValues(child, visitor));
        } else if (value instanceof final String text) {
            visitor.accept(text);
        }
    }


    // --------------------------------------------------------------------------------


    /// What the checks need of Stroom, as the reader sees it.
    interface Facts {

        /// @param name A tool's name.
        /// @return Its definition, if it is a tool (a group's `activate_` tool counts, with any arguments).
        Optional<ToolDefinition> tool(String name);

        /// @return The names of the tools.
        Set<String> toolNames();

        /// @param name A tool's name.
        /// @return True if the reader's tool policies allow it.
        boolean allowed(String name);

        /// @param dataSource A data source's name or uuid.
        /// @return Whether the reader can Use it (it exists), and its fields.
        DataSource dataSource(String dataSource);

        /// @param words A step's words.
        /// @return The tool whose description shares most of them, if any does.
        Optional<String> closestByPurpose(Set<String> words);

        /// @return The other skills the reader can read, for near-duplicates.
        List<OtherSkill> otherSkills();

        /// @param skill A skill's name.
        /// @return How it was used in the reader's chats, or null if that isn't known.
        SkillUsage.Counts usage(String skill);

        /// @param entries A timeline skill's Event chains entries, of data sources the reader can use.
        /// @return What recent data says is wrong with them (types not seen, a key not held, a pair no longer
        /// alternating); none by default.
        default List<String> eventChains(final List<EventChains.Entry> entries) {
            return List.of();
        }
    }

    /// A data source an example call names.
    ///
    /// @param usable True if it exists and the reader can Use it.
    /// @param fields Its fields, or null if they aren't known.
    record DataSource(boolean usable, Set<String> fields) {

    }

    /// Another skill, as the checks compare it.
    ///
    /// @param name        Its name.
    /// @param description Its description.
    /// @param folder      Its folder.
    /// @param isSame      True if it is the skill being checked itself.
    record OtherSkill(String name, String description, String folder, boolean isSame) {

    }

    /// Problem or suggestion.
    enum Kind {
        PROBLEM,
        SUGGESTION
    }

    /// A problem or suggestion, with its proposed fix.
    ///
    /// @param kind    Which.
    /// @param rule    The rule, e.g. `unknown_tool`.
    /// @param message What was found.
    /// @param fix     The proposed fix.
    record Finding(Kind kind, String rule, String message, String fix) {

        static Finding problem(final String rule, final String message, final String fix) {
            return new Finding(Kind.PROBLEM, rule, message, fix);
        }

        static Finding suggestion(final String rule, final String message, final String fix) {
            return new Finding(Kind.SUGGESTION, rule, message, fix);
        }
    }
}
