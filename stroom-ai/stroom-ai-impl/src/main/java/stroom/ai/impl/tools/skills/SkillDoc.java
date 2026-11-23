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

import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.yaml.YamlUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A skills doc: a Documentation doc holding saved procedures (skills), Markdown only, read by convention rather
/// than to a rigid format.
///
/// ```markdown
/// ---
/// stroom: skills
/// history:
///   - version: 1
///     date: "2026-11-23"
///     author: "admin"
///     how: agent
///     skill: "User activity timeline"
///     change: "Created"
///     body: "3fa9c2d41b7e"
/// ---
///
/// # User activity timeline
///
/// Builds a timeline of one user's activity over a period.
///
/// ## When to use
/// - "build a timeline of jsmith's activity"
///
/// ## Parameters
/// - user (required): the user's account name.
/// - days (default 7): how many days back.
///
/// ## Steps
/// 1. Find the user's events:
///    ```json
///    {"tool": "search_events", "arguments": {"index": "Endpoint", "where": [{"field": "User", "value": "{user}"}]}}
///    ```
///
/// ## Output
/// A table in the chat, by time.
/// ```
///
/// Only a Documentation doc named [#DOC_NAME] is a skills doc. Its front matter (YAML between `---` lines) says
/// what it is (`stroom: skills`) and holds its change history, written by the tools: each entry's `body` is a
/// short hash of the doc's text below the front matter as the entry
/// left it, so an edit by hand since shows. Each `#` heading is a skill: its name; the first paragraph under it is its
/// description; `{name}` in its steps or output uses a parameter; `[[...]]` is a template's placeholder still to fill.
public final class SkillDoc {

    /// The default name of a skills doc.
    public static final String DOC_NAME = "SKILLS";
    /// The front matter key and value that mark a skills doc.
    public static final String MARKER_KEY = "stroom";
    public static final String MARKER_VALUE = "skills";
    static final String HISTORY_KEY = "history";
    /// How a change was made: by the authoring tool, or by hand.
    public static final String HOW_AGENT = "agent";
    public static final String HOW_USER = "user";

    static final String WHEN_TO_USE = "when to use";
    static final String PARAMETERS = "parameters";
    static final String STEPS = "steps";
    static final String OUTPUT = "output";

    private static final String FENCE_LINE = "---";
    private static final int HASH_CHARS = 12;
    private static final Pattern SKILL_HEADING = Pattern.compile("^#\\s+(\\S.*?)\\s*#*\\s*$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^##\\s+(\\S.*?)\\s*#*\\s*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```+|~~~+)\\s*([A-Za-z0-9_-]*)");
    private static final Pattern STEP_ITEM = Pattern.compile("^ {0,3}(\\d+)[.)]\\s+(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern PARAMETER_ITEM = Pattern.compile(
            "^`?([A-Za-z][A-Za-z0-9_]*)`?\\s*(?:\\(([^)]*)\\)\\s*(?:[:\u2013\u2014-]\\s*)?|[:\u2013\u2014-]\\s*)(.*)$");
    private static final Pattern DEFAULT = Pattern.compile("^default\\s*[:=]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    /// A parameter used in a skill's steps or output.
    public static final Pattern PARAMETER_USE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");
    /// A template's placeholder, for what a site supplies, still to fill.
    public static final Pattern PLACEHOLDER = Pattern.compile("\\[\\[([^\\]\\n]+)]]");
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_-]+)\\s*:");

    private final String text;
    private final List<String> frontMatterLines;
    private final Map<String, Object> frontMatter;
    private final String frontMatterProblem;
    private final String body;
    private final List<Skill> skills;
    private final List<HistoryEntry> history;

    private SkillDoc(final String text,
                     final List<String> frontMatterLines,
                     final Map<String, Object> frontMatter,
                     final String frontMatterProblem,
                     final String body) {
        this.text = text;
        this.frontMatterLines = frontMatterLines;
        this.frontMatter = frontMatter;
        this.frontMatterProblem = frontMatterProblem;
        this.body = body;
        this.skills = readSkills(body);
        this.history = readHistory(frontMatter);
    }

    /// @param text A Documentation doc's text, or null for none.
    /// @return The doc, read as a skills doc (whether or not it is one: see [#isMarked()]).
    public static SkillDoc parse(final String text) {
        final String normalised = NullSafe.string(text).replace("\r\n", "\n");
        final List<String> lines = List.of(normalised.split("\n", -1));
        if (!lines.isEmpty() && FENCE_LINE.equals(lines.getFirst().strip())) {
            for (int i = 1; i < lines.size(); i++) {
                final String line = lines.get(i).strip();
                if (FENCE_LINE.equals(line) || "...".equals(line)) {
                    final List<String> yamlLines = lines.subList(1, i);
                    final String rest = String.join("\n", lines.subList(i + 1, lines.size()));
                    Map<String, Object> yaml = Map.of();
                    String problem = null;
                    try {
                        final Object read = yamlLines.isEmpty()
                                ? null
                                : YamlUtil.getMapper().readValue(String.join("\n", yamlLines), Object.class);
                        if (read instanceof final Map<?, ?> map) {
                            yaml = stringKeys(map);
                        } else if (read != null) {
                            problem = "its front matter isn't a set of keys";
                        }
                    } catch (final RuntimeException e) {
                        problem = "its front matter isn't YAML that can be read: " + firstLine(e.getMessage());
                    }
                    return new SkillDoc(normalised, List.copyOf(yamlLines), yaml, problem, rest);
                }
            }
        }
        return new SkillDoc(normalised, null, Map.of(), null, normalised);
    }

    /// @return The doc's whole text, as read.
    public String text() {
        return text;
    }

    /// @return True if its front matter marks it as a skills doc (`stroom: skills`).
    public boolean isMarked() {
        return MARKER_VALUE.equalsIgnoreCase(String.valueOf(frontMatter.get(MARKER_KEY)).strip());
    }

    /// @return True if it has front matter, however it reads.
    public boolean hasFrontMatter() {
        return frontMatterLines != null;
    }

    /// @return Why its front matter couldn't be read, or null if it could (or it has none).
    public String frontMatterProblem() {
        return frontMatterProblem;
    }

    /// @return The text below the front matter (all of it, if it has none).
    public String body() {
        return body;
    }

    /// @return Its skills, in order.
    public List<Skill> skills() {
        return skills;
    }

    /// @param name A skill's name, ignoring case.
    /// @return The skill, if the doc has one of that name.
    public Optional<Skill> skill(final String name) {
        return skills.stream()
                .filter(skill -> skill.name().equalsIgnoreCase(NullSafe.string(name).strip()))
                .findFirst();
    }

    /// @return Its change history, oldest first.
    public List<HistoryEntry> history() {
        return history;
    }

    /// @return The latest entry in its change history, if it has one.
    public Optional<HistoryEntry> latestEntry() {
        return history.isEmpty()
                ? Optional.empty()
                : Optional.of(history.getLast());
    }

    /// @return The next version for the change history: one more than the highest.
    public int nextVersion() {
        return history.stream().mapToInt(HistoryEntry::version).max().orElse(0) + 1;
    }

    /// @return The short hash of the text below the front matter, as a history entry records it.
    public String bodyHash() {
        return bodyHash(body);
    }

    /// @return True if the text below the front matter has been changed since the latest history entry, which is
    /// one the tools wrote (with the hash of the text as they left it).
    public boolean isEditedSinceLatestEntry() {
        return latestEntry()
                .map(HistoryEntry::body)
                .filter(NullSafe::isNonBlankString)
                .map(hash -> !hash.equals(bodyHash()))
                .orElse(false);
    }

    /// @param skillMarkdown One skill, from its `#` heading.
    /// @param entry         The history entry for the change (its `body` is set here).
    /// @return The doc's text with the skill added, or put in place of the one of the same name, and the entry
    /// added to the history.
    /// @throws IllegalArgumentException if the text isn't one skill.
    public String withSkill(final String skillMarkdown, final HistoryEntry entry) {
        final List<Skill> given = readSkills("\n" + NullSafe.string(skillMarkdown).replace("\r\n", "\n"));
        if (given.size() != 1) {
            throw new IllegalArgumentException(given.isEmpty()
                    ? "Give the skill from its '# Name' heading."
                    : "Give one skill at a time (a '# Name' heading each): this has " + given.size() + ".");
        }
        final String skillText = given.getFirst().text().strip();
        final Optional<Skill> existing = skill(given.getFirst().name());
        final String newBody;
        if (existing.isPresent()) {
            final List<String> lines = List.of(body.split("\n", -1));
            final List<String> result = new ArrayList<>(lines.subList(0, existing.get().startLine()));
            result.add(skillText);
            final List<String> after = lines.subList(existing.get().endLine(), lines.size());
            if (!after.isEmpty() && after.stream().anyMatch(line -> !line.isBlank())) {
                result.add("");
            }
            result.addAll(after);
            newBody = String.join("\n", result);
        } else if (body.isBlank()) {
            newBody = "\n" + skillText + "\n";
        } else {
            newBody = body.stripTrailing() + "\n\n" + skillText + "\n";
        }
        return withBody(newBody, entry);
    }

    /// @param name  A skill's name.
    /// @param entry The history entry for the change.
    /// @return The doc's text without the skill, and with the entry added to the history.
    /// @throws IllegalArgumentException if the doc has no skill of that name.
    public String withoutSkill(final String name, final HistoryEntry entry) {
        final Skill existing = skill(name)
                .orElseThrow(() -> new IllegalArgumentException("There is no skill '" + name + "' in this doc."));
        final List<String> lines = List.of(body.split("\n", -1));
        final List<String> result = new ArrayList<>(lines.subList(0, existing.startLine()));
        result.addAll(lines.subList(existing.endLine(), lines.size()));
        String newBody = String.join("\n", result).replaceAll("\n{3,}", "\n\n");
        if (!newBody.endsWith("\n")) {
            newBody = newBody + "\n";
        }
        return withBody(newBody, entry);
    }

    /// @param newBody The text below the front matter.
    /// @param entry   The history entry for the change (its `body` is set here), or null to add none.
    /// @return The doc's text with that body, marked, its history kept and the entry added.
    public String withBody(final String newBody, final HistoryEntry entry) {
        final List<HistoryEntry> entries = new ArrayList<>(history);
        if (entry != null) {
            entries.add(entry.withBody(bodyHash(newBody)));
        }
        final StringBuilder sb = new StringBuilder(FENCE_LINE).append('\n');
        sb.append(MARKER_KEY).append(": ").append(MARKER_VALUE).append('\n');
        for (final String line : otherFrontMatterLines()) {
            sb.append(line).append('\n');
        }
        if (!entries.isEmpty()) {
            sb.append(HISTORY_KEY).append(":\n");
            entries.forEach(each -> each.appendYaml(sb));
        }
        sb.append(FENCE_LINE).append('\n');
        final String bodyText = newBody.startsWith("\n")
                ? newBody
                : "\n" + newBody;
        return sb.append(bodyText).toString();
    }

    /// @param text The text below a doc's front matter.
    /// @return Its short hash, ignoring line endings and white space at its ends.
    public static String bodyHash(final String text) {
        return ProposeCodeChangeTool.sha256(NullSafe.string(text).replace("\r\n", "\n").strip())
                .substring(0, HASH_CHARS);
    }

    /// @param name A skill's name.
    /// @return The name as a slash command's word: lower case, words joined by `-`, e.g. `user-activity-timeline`.
    public static String slug(final String name) {
        return NullSafe.string(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    // The front matter's lines other than the marker and the history, kept as they were written.
    private List<String> otherFrontMatterLines() {
        final List<String> kept = new ArrayList<>();
        boolean skipping = false;
        for (final String line : NullSafe.list(frontMatterLines)) {
            final Matcher key = TOP_LEVEL_KEY.matcher(line);
            if (key.find() && !Character.isWhitespace(line.charAt(0))) {
                skipping = MARKER_KEY.equals(key.group(1)) || HISTORY_KEY.equals(key.group(1));
            } else if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("-")) {
                skipping = false;
            }
            if (!skipping && !line.isBlank()) {
                kept.add(line);
            }
        }
        return kept;
    }

    private static List<Skill> readSkills(final String body) {
        final List<String> lines = List.of(body.split("\n", -1));
        final List<Skill> skills = new ArrayList<>();
        int start = -1;
        String name = null;
        boolean inFence = false;
        for (int i = 0; i <= lines.size(); i++) {
            final String line = i < lines.size()
                    ? lines.get(i)
                    : null;
            if (line != null && CODE_FENCE.matcher(line).find()) {
                inFence = !inFence;
                continue;
            }
            final Matcher heading = line == null || inFence
                    ? null
                    : SKILL_HEADING.matcher(line);
            if (line == null || (heading != null && heading.matches())) {
                if (name != null) {
                    int end = i;
                    while (end > start + 1 && lines.get(end - 1).isBlank()) {
                        end--;
                    }
                    skills.add(Skill.read(name, lines.subList(start, end), start, end));
                }
                if (line != null) {
                    name = heading.group(1).strip();
                    start = i;
                }
            }
        }
        return List.copyOf(skills);
    }

    private static List<HistoryEntry> readHistory(final Map<String, Object> frontMatter) {
        final List<HistoryEntry> entries = new ArrayList<>();
        if (frontMatter.get(HISTORY_KEY) instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    entries.add(HistoryEntry.of(stringKeys(map)));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static Map<String, Object> stringKeys(final Map<?, ?> map) {
        final Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String firstLine(final String text) {
        final String value = NullSafe.string(text);
        final int end = value.indexOf('\n');
        return end < 0
                ? value
                : value.substring(0, end);
    }


    // --------------------------------------------------------------------------------


    /// One skill: a `#` heading and what is under it, up to the next.
    ///
    /// @param name        Its name: the heading.
    /// @param text        Its whole text, from the heading.
    /// @param startLine   The line of the doc's body its heading is on, from 0.
    /// @param endLine     The line after its last line that isn't blank.
    /// @param description The first paragraph under its heading, or empty.
    /// @param whenToUse   The lines of its `## When to use`: how people ask for it.
    /// @param parameters  Its `## Parameters`, in order.
    /// @param steps       Its `## Steps`, in order.
    /// @param output      Its `## Output`, or empty.
    /// @param sections    Each `##` section's text, by its heading in lower case.
    /// @param used        The parameters its steps and output use (`{name}`), in order of first use.
    /// @param placeholders A template's placeholders still to fill (`[[...]]`), in order.
    public record Skill(String name,
                        String text,
                        int startLine,
                        int endLine,
                        String description,
                        List<String> whenToUse,
                        List<Parameter> parameters,
                        List<Step> steps,
                        String output,
                        Map<String, String> sections,
                        Set<String> used,
                        List<String> placeholders) {

        static Skill read(final String name, final List<String> lines, final int start, final int end) {
            final Map<String, String> sections = new LinkedHashMap<>();
            final List<String> intro = new ArrayList<>();
            String section = null;
            final StringBuilder current = new StringBuilder();
            boolean inFence = false;
            for (final String line : lines.subList(1, lines.size())) {
                if (CODE_FENCE.matcher(line).find()) {
                    inFence = !inFence;
                }
                final Matcher heading = inFence
                        ? null
                        : SECTION_HEADING.matcher(line);
                if (heading != null && heading.matches()) {
                    if (section != null) {
                        sections.putIfAbsent(section, current.toString().strip());
                    }
                    section = heading.group(1).strip().toLowerCase(Locale.ROOT);
                    current.setLength(0);
                } else if (section == null) {
                    intro.add(line);
                } else {
                    current.append(line).append('\n');
                }
            }
            if (section != null) {
                sections.putIfAbsent(section, current.toString().strip());
            }
            final String text = String.join("\n", lines);
            final String stepsAndOutput = NullSafe.string(sections.get(STEPS)) + "\n"
                                          + NullSafe.string(sections.get(OUTPUT));
            final Set<String> used = new LinkedHashSet<>();
            final Matcher use = PARAMETER_USE.matcher(stepsAndOutput);
            while (use.find()) {
                used.add(use.group(1));
            }
            final List<String> placeholders = new ArrayList<>();
            final Matcher placeholder = PLACEHOLDER.matcher(text);
            while (placeholder.find()) {
                if (!placeholders.contains(placeholder.group(1).strip())) {
                    placeholders.add(placeholder.group(1).strip());
                }
            }
            return new Skill(name, text, start, end, description(intro), listLines(sections.get(WHEN_TO_USE)),
                    parameters(sections.get(PARAMETERS)), steps(sections.get(STEPS)),
                    NullSafe.string(sections.get(OUTPUT)), Map.copyOf(sections), Set.copyOf(used),
                    List.copyOf(placeholders));
        }

        /// @param name A parameter's name.
        /// @return The parameter, if it is listed.
        public Optional<Parameter> parameter(final String name) {
            return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst();
        }

        /// @return Every example call in its steps, in order.
        public List<ExampleCall> calls() {
            return steps.stream().flatMap(step -> step.calls().stream()).toList();
        }

        private static String description(final List<String> intro) {
            final List<String> paragraph = new ArrayList<>();
            for (final String line : intro) {
                if (line.isBlank()) {
                    if (!paragraph.isEmpty()) {
                        break;
                    }
                } else {
                    paragraph.add(line.strip());
                }
            }
            return String.join(" ", paragraph);
        }

        private static List<String> listLines(final String section) {
            final List<String> result = new ArrayList<>();
            for (final String line : NullSafe.string(section).split("\n")) {
                final Matcher item = LIST_ITEM.matcher(line);
                final String value = (item.matches()
                        ? item.group(1)
                        : line).strip();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
            return List.copyOf(result);
        }

        private static List<Parameter> parameters(final String section) {
            final List<Parameter> result = new ArrayList<>();
            for (final String line : NullSafe.string(section).split("\n")) {
                final Matcher item = LIST_ITEM.matcher(line);
                if (!item.matches()) {
                    continue;
                }
                final Matcher parameter = PARAMETER_ITEM.matcher(item.group(1).strip());
                if (!parameter.matches()) {
                    continue;
                }
                final String qualifier = NullSafe.string(parameter.group(2)).strip();
                final Matcher defaultValue = DEFAULT.matcher(qualifier);
                final boolean hasDefault = defaultValue.matches();
                final String value = hasDefault
                        ? defaultValue.group(1).strip().replaceAll("^[`'\"]|[`'\"]$", "")
                        : null;
                final boolean required = qualifier.toLowerCase(Locale.ROOT).contains("required");
                result.add(new Parameter(parameter.group(1), required || (!hasDefault && qualifier.isEmpty()),
                        value, parameter.group(3).strip(), !qualifier.isEmpty()));
            }
            return List.copyOf(result);
        }

        private static List<Step> steps(final String section) {
            final List<Step> result = new ArrayList<>();
            int number = 0;
            final StringBuilder text = new StringBuilder();
            final List<ExampleCall> calls = new ArrayList<>();
            StringBuilder json = null;
            boolean inFence = false;
            for (final String line : NullSafe.string(section).split("\n", -1)) {
                final Matcher fence = CODE_FENCE.matcher(line);
                if (fence.find()) {
                    if (!inFence && "json".equalsIgnoreCase(fence.group(2))) {
                        json = new StringBuilder();
                    } else if (inFence && json != null) {
                        calls.add(ExampleCall.read(number, json.toString()));
                        json = null;
                    }
                    inFence = !inFence;
                    text.append(line).append('\n');
                    continue;
                }
                if (inFence) {
                    NullSafe.consume(json, builder -> builder.append(line).append('\n'));
                    text.append(line).append('\n');
                    continue;
                }
                final Matcher item = STEP_ITEM.matcher(line);
                if (item.matches()) {
                    if (number > 0 || !text.toString().isBlank()) {
                        result.add(new Step(number, text.toString().strip(), List.copyOf(calls)));
                    }
                    number = Integer.parseInt(item.group(1));
                    text.setLength(0);
                    calls.clear();
                    text.append(item.group(2)).append('\n');
                } else {
                    text.append(line).append('\n');
                }
            }
            if (number > 0 || !text.toString().isBlank()) {
                result.add(new Step(number, text.toString().strip(), List.copyOf(calls)));
            }
            return List.copyOf(result);
        }
    }

    /// A skill's parameter, as its `## Parameters` lists it.
    ///
    /// @param name         Its name, as the steps use it (`{name}`).
    /// @param required     True if it must be given (said so, or neither required nor a default was said).
    /// @param defaultValue Its default, or null for none.
    /// @param description  What it is.
    /// @param stated       True if it says whether it is required or its default.
    public record Parameter(String name, boolean required, String defaultValue, String description, boolean stated) {

    }

    /// One of a skill's numbered steps.
    ///
    /// @param number Its number.
    /// @param text   Its text, with any code blocks.
    /// @param calls  The example calls in its fenced JSON blocks.
    public record Step(int number, String text, List<ExampleCall> calls) {

    }

    /// An example call in a step: a fenced JSON block of `{"tool": ..., "arguments": {...}}`.
    ///
    /// @param step      The step's number.
    /// @param json      The block's text.
    /// @param tool      The tool it calls, or null if the block doesn't say.
    /// @param arguments Its arguments, or null if they can't be read.
    /// @param problem   Why it isn't a call that can be read, or null.
    public record ExampleCall(int step, String json, String tool, Map<String, Object> arguments, String problem) {

        static ExampleCall read(final int step, final String json) {
            final Map<?, ?> map;
            try {
                map = JsonUtil.readValue(json, Map.class);
            } catch (final RuntimeException e) {
                return new ExampleCall(step, json, null, null, "it isn't JSON that can be read (quote a parameter "
                                                               + "used as a value, e.g. \"{days}\")");
            }
            if (!(map.get("tool") instanceof final String tool) || tool.isBlank()) {
                return new ExampleCall(step, json, null, null, "it doesn't name its tool: write it as {\"tool\": "
                                                               + "\"<name>\", \"arguments\": {...}}");
            }
            final Object arguments = map.get("arguments");
            if (arguments != null && !(arguments instanceof Map<?, ?>)) {
                return new ExampleCall(step, json, tool.strip(), null, "its arguments aren't an object");
            }
            return new ExampleCall(step, json, tool.strip(), arguments == null
                    ? Map.of()
                    : stringKeys((Map<?, ?>) arguments), null);
        }
    }

    /// An entry in a skills doc's change history.
    ///
    /// @param version Its version, from 1.
    /// @param date    When, as `YYYY-MM-DD`.
    /// @param author  Who.
    /// @param how     [#HOW_AGENT] (by the authoring tool) or [#HOW_USER] (by hand).
    /// @param skill   The skill it changed.
    /// @param change  What changed.
    /// @param body    The short hash of the doc's text below the front matter as the change left it, or null.
    public record HistoryEntry(int version, String date, String author, String how, String skill, String change,
                               String body) {

        static HistoryEntry of(final Map<String, Object> map) {
            int version = 0;
            try {
                version = Integer.parseInt(String.valueOf(map.get("version")).strip());
            } catch (final NumberFormatException e) {
                // Not a number: 0.
            }
            return new HistoryEntry(version, string(map.get("date")), string(map.get("author")),
                    string(map.get("how")), string(map.get("skill")), string(map.get("change")),
                    string(map.get("body")));
        }

        HistoryEntry withBody(final String hash) {
            return new HistoryEntry(version, date, author, how, skill, change, hash);
        }

        void appendYaml(final StringBuilder sb) {
            sb.append("  - version: ").append(version).append('\n');
            sb.append("    date: ").append(quoted(date)).append('\n');
            sb.append("    author: ").append(quoted(author)).append('\n');
            sb.append("    how: ").append(NullSafe.nonBlankStringElse(how, HOW_AGENT)).append('\n');
            sb.append("    skill: ").append(quoted(skill)).append('\n');
            sb.append("    change: ").append(quoted(change)).append('\n');
            if (NullSafe.isNonBlankString(body)) {
                sb.append("    body: ").append(quoted(body)).append('\n');
            }
        }

        private static String quoted(final String value) {
            return "\"" + NullSafe.string(value).replace("\\", "\\\\").replace("\"", "\\\"")
                    .replaceAll("[\\r\\n\\t]+", " ").strip() + "\"";
        }

        private static String string(final Object value) {
            return value == null
                    ? null
                    : String.valueOf(value).strip();
        }
    }
}
