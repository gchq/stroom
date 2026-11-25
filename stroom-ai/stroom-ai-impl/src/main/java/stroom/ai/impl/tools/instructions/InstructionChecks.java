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
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The checks on an AGENTS doc, as `check_instructions` makes them and as `propose_instructions` checks a proposed
/// doc: problems (a line of a section the tools apply themselves that they can't apply as written), suggestions (it
/// would work better changed) and notes (how the doc is read, nothing to change), each problem and suggestion with a
/// proposed fix. All from facts: the sections and keys the tools read ([InstructionRules]), the fields of the
/// indexes where the doc applies, the index plans kept with the pipelines feeding them, the other AGENTS docs and
/// the field conventions; never a guess at what a line means. Free text, and a line of a section the tools don't
/// read, guides the agent only and is never asked to change, unless it is a `key: field` line naming a field an
/// index there has, which the tools would apply under `## Fields`. What the checks need of Stroom is in [Facts], so
/// the rules are tested on their own.
public final class InstructionChecks {

    /// The sections the tools read, in the order they are compared with a heading.
    static final List<String> SECTIONS = List.of(InstructionRules.FIELDS_SECTION,
            InstructionRules.REQUIRED_IMPORTS_SECTION, InstructionRules.COMPONENT_TEMPLATES_SECTION,
            InstructionRules.DOCUMENTATION_SECTION);

    // A word in a heading that names each section, e.g. '## Field names' for Fields.
    private static final Map<String, List<String>> STEMS = Map.of(
            InstructionRules.FIELDS_SECTION, List.of("field"),
            InstructionRules.REQUIRED_IMPORTS_SECTION, List.of("import"),
            InstructionRules.COMPONENT_TEMPLATES_SECTION, List.of("component", "template"),
            InstructionRules.DOCUMENTATION_SECTION, List.of("documentation"));
    private static final Pattern HEADING_MARKS = Pattern.compile("^#{1,6}");
    private static final Pattern NOT_A_LETTER = Pattern.compile("[^a-z ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    // A key written as 'User field' or 'host fields' under another heading.
    private static final Pattern FIELD_SUFFIX = Pattern.compile("\\s+fields?$");

    private InstructionChecks() {
        // Static only.
    }

    /// Checks a doc.
    ///
    /// @param folder       The folder the doc is in (or would be in), e.g. `/Feeds/Acme`; null if not known, when
    ///                     only its form is checked.
    /// @param text         Its text.
    /// @param facts        What the checks need of Stroom; [Facts#NONE] for none.
    /// @param whereUnruled True to also suggest a `## Fields` user line for the indexes where the doc applies
    ///                     whose user field would only be found by a field convention (for the folder's own doc).
    /// @return The problems, then the suggestions, then the notes.
    public static List<Finding> check(final String folder,
                                      final String text,
                                      final Facts facts,
                                      final boolean whereUnruled) {
        final List<String> lines = lines(text);
        final String at = folder == null
                ? null
                : AgentInstructions.normalise(folder);
        final Scope scope = Scope.of(at, facts);
        final List<Finding> findings = new ArrayList<>();
        final List<FieldLine> fieldLines = new ArrayList<>();
        for (final Section section : sections(lines)) {
            if (section.typed() != null && !section.first()) {
                duplicateSection(lines, section, findings);
            } else if (section.typed() != null) {
                typedLines(lines, section, fieldLines, findings);
            } else if (!nearHeading(lines, section, findings)) {
                misplacedFields(lines, section, scope, findings);
            }
        }
        if (!scope.fields().isEmpty()) {
            for (final FieldLine line : fieldLines) {
                unknownFields(line, scope, findings);
            }
        }
        repeatedKeys(fieldLines, findings);
        if (at != null) {
            generalDocs(fieldLines, at, scope, findings);
            plans(at, text, fieldLines, scope, findings);
            if (whereUnruled) {
                unruled(at, text, fieldLines, scope, findings);
            }
        }
        findings.sort(Comparator.comparing(Finding::kind));
        return List.copyOf(findings);
    }

    /// @param findings Findings.
    /// @return Only the problems.
    public static List<Finding> problems(final List<Finding> findings) {
        return NullSafe.list(findings).stream().filter(finding -> finding.kind() == Kind.PROBLEM).toList();
    }

    /// @param findings Findings.
    /// @return The findings as text, a line each, the problems first; "No problems or suggestions." if none.
    public static String describe(final List<Finding> findings) {
        if (NullSafe.isEmptyCollection(findings)) {
            return "No problems or suggestions.";
        }
        final StringBuilder sb = new StringBuilder();
        for (final Finding finding : findings) {
            sb.append("- ").append(finding.kind().label())
                    .append(" (").append(finding.rule()).append("): ").append(finding.message());
            if (NullSafe.isNonBlankString(finding.fix())) {
                sb.append(" Fix: ").append(finding.fix());
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /// The doc with every problem's fix applied (never a suggestion's, which the user is to confirm).
    ///
    /// @param text     The doc's text, as checked.
    /// @param findings Its findings, from [#check] on that text.
    /// @return The corrected text; the text itself if no problem has a fix.
    public static String corrected(final String text, final List<Finding> findings) {
        final List<Finding> fixing = problems(findings).stream()
                .filter(finding -> !finding.edits().isEmpty() || !finding.moves().isEmpty())
                .toList();
        if (fixing.isEmpty()) {
            return text;
        }
        final List<String> lines = lines(text);
        final Map<Integer, LineEdit> edits = new HashMap<>();
        final Map<String, List<String>> moves = new LinkedHashMap<>();
        for (final Finding finding : fixing) {
            finding.edits().forEach(edit -> edits.putIfAbsent(edit.line(), edit));
            finding.moves().forEach(move -> moves.computeIfAbsent(move.section(), k -> new ArrayList<>())
                    .add(move.line()));
        }
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final LineEdit edit = edits.get(i);
            if (edit == null) {
                out.add(lines.get(i));
            } else if (edit.text() != null) {
                out.add(edit.text());
            }
        }
        moves.forEach((section, moved) -> addToSection(out, section, moved));
        final String joined = String.join("\n", out);
        return text.endsWith("\n") && !joined.endsWith("\n")
                ? joined + "\n"
                : joined;
    }

    /// A doc with `## Fields` lines given anew: each replaces the doc's line for its key (the one in force, the last),
    /// or is added at the end of its first `## Fields` section, made at the end of the doc if it has none.
    ///
    /// @param text  The doc's text.
    /// @param lines The lines, by the key the tools know (e.g. `user`), each as written, e.g. `- user: actor.login`.
    /// @return The doc with them.
    public static String withFieldLines(final String text, final Map<String, String> lines) {
        final List<String> out = new ArrayList<>(lines(text));
        final Map<String, Integer> at = new LinkedHashMap<>();
        for (final Section section : sections(out)) {
            if (!InstructionRules.FIELDS_SECTION.equals(section.typed()) || !section.first()) {
                continue;
            }
            for (int i = section.start(); i < section.end(); i++) {
                final Matcher bullet = InstructionRules.BULLET.matcher(out.get(i));
                final String key = bullet.matches()
                        ? InstructionRules.FIELDS.get(InstructionRules.normaliseKey(bullet.group(1)))
                        : null;
                if (key != null) {
                    at.put(key, i);
                }
            }
        }
        final List<String> added = new ArrayList<>();
        lines.forEach((key, line) -> {
            final Integer index = at.get(key);
            if (index == null) {
                added.add(line);
            } else {
                out.set(index, line);
            }
        });
        if (!added.isEmpty()) {
            addToSection(out, InstructionRules.FIELDS_SECTION, added);
        }
        final String joined = String.join("\n", out);
        return NullSafe.string(text).endsWith("\n") || joined.endsWith("\n")
                ? joined.stripTrailing() + "\n"
                : joined;
    }

    // ------------------------------------------------------------------------------------------------
    // The sections the tools read

    // A section the tools read whose heading is there again: only the first is read.
    private static void duplicateSection(final List<String> lines,
                                         final Section section,
                                         final List<Finding> findings) {
        final List<LineEdit> edits = new ArrayList<>();
        final List<Move> moves = new ArrayList<>();
        edits.add(LineEdit.remove(section.heading()));
        for (int i = section.start(); i < section.end(); i++) {
            if (!lines.get(i).isBlank()) {
                edits.add(LineEdit.remove(i));
                moves.add(new Move(section.typed(), lines.get(i)));
            }
        }
        findings.add(Finding.problem("repeated_section", "'" + lines.get(section.heading()).strip() + "' is there "
                                                         + "twice; the tools read only the first, so the lines "
                                                         + "below this one are left out.",
                "move its lines under the first '## " + section.typed() + "' and remove this heading.", edits,
                moves));
    }

    // The lines of a section the tools read: a key, stage, rule or layout they don't know.
    private static void typedLines(final List<String> lines,
                                   final Section section,
                                   final List<FieldLine> fieldLines,
                                   final List<Finding> findings) {
        final String name = section.typed();
        final Set<String> keys = InstructionRules.keysOf(name);
        for (int i = section.start(); i < section.end(); i++) {
            final String line = lines.get(i);
            if (InstructionRules.DOCUMENTATION_SECTION.equals(name)) {
                documentationLine(i, line, findings);
                continue;
            }
            final Matcher bullet = InstructionRules.BULLET.matcher(line);
            if (!bullet.matches()) {
                continue;
            }
            final String key = InstructionRules.normaliseKey(bullet.group(1));
            if (keys.contains(key)) {
                if (InstructionRules.FIELDS_SECTION.equals(name)) {
                    final List<String> names = InstructionRules.fieldNames(bullet.group(2));
                    if (!names.isEmpty()) {
                        fieldLines.add(new FieldLine(i, InstructionRules.FIELDS.get(key), names, line));
                    }
                }
                continue;
            }
            final List<String> close = closest(key, keys);
            if (close.size() == 1) {
                final String fixed = replaceOnce(line, bullet.group(1).strip(), close.getFirst());
                findings.add(Finding.problem("unknown_key", "'## " + name + "' line '" + line.strip() + "' names "
                                                            + "no " + what(name) + " the tools know, so they leave "
                                                            + "it out.",
                        "replace it with '" + fixed.strip() + "'.", List.of(LineEdit.replace(i, fixed)),
                        List.of()));
            } else {
                findings.add(Finding.note("unknown_key", "'## " + name + "' line '" + line.strip() + "' names no "
                                                         + what(name) + " the tools know (" + known(name) + ")"
                                                         + (close.isEmpty()
                                                                 ? ""
                                                                 : "; as close: " + String.join(", ", close))
                                                         + ", so it only guides the agent: fine for a note."));
            }
        }
    }

    private static void documentationLine(final int index, final String line, final List<Finding> findings) {
        final Matcher layout = InstructionRules.LAYOUT_BULLET.matcher(line);
        if (layout.matches()) {
            final String given = InstructionRules.stripChars(layout.group(2).strip(), "`*.").toLowerCase(Locale.ROOT);
            if (InstructionRules.LAYOUTS.containsKey(given)) {
                return;
            }
            final List<String> close = closest(given, InstructionRules.LAYOUTS.keySet());
            if (close.size() == 1) {
                final String fixed = replaceOnce(line, layout.group(2).strip(), close.getFirst());
                findings.add(Finding.problem("unknown_layout", "'## Documentation' line '" + line.strip() + "' "
                                                               + "names no layout the tools know.",
                        "replace it with '" + fixed.strip() + "'.", List.of(LineEdit.replace(index, fixed)),
                        List.of()));
            } else {
                findings.add(Finding.problem("unknown_layout", "'## Documentation' line '" + line.strip() + "' "
                                                               + "names no layout the tools know ("
                                                               + known(InstructionRules.DOCUMENTATION_SECTION)
                                                               + "), so they leave it out.",
                        "write one of those.", List.of(), List.of()));
            }
            return;
        }
        // A layout under another key, e.g. '- layout: compact': the tools read only 'field mapping'.
        final Matcher bullet = InstructionRules.BULLET.matcher(line);
        if (bullet.matches()) {
            final String value = InstructionRules.stripChars(bullet.group(2).strip(), "`*.")
                    .toLowerCase(Locale.ROOT);
            if (InstructionRules.LAYOUTS.containsKey(value)) {
                final String fixed = "- field mapping: " + bullet.group(2).strip();
                findings.add(Finding.problem("unknown_key", "'## Documentation' line '" + line.strip() + "' gives "
                                                            + "a layout, but the tools read it only from a 'field "
                                                            + "mapping' line.",
                        "replace it with '" + fixed + "'.", List.of(LineEdit.replace(index, fixed)), List.of()));
            }
        }
    }

    // A heading that nearly names a section the tools read: its lines are left out.
    private static boolean nearHeading(final List<String> lines, final Section section, final List<Finding> findings) {
        if (section.heading() < 0) {
            return false;
        }
        final String heading = lines.get(section.heading());
        final String words = headingWords(heading);
        for (final String name : SECTIONS) {
            final String wanted = name.toLowerCase(Locale.ROOT);
            final int distance = distance(words, wanted);
            final boolean keyed = hasKeyOf(lines, section, name);
            final boolean stem = STEMS.get(name).stream().anyMatch(words::contains);
            if (words.equals(wanted) || distance <= 1 || (keyed && (distance <= 2 || stem))) {
                final String renamed = headingLevel(heading) + " " + name;
                if (sectionHeadingAt(lines, name) < 0) {
                    findings.add(Finding.problem("heading", "Heading '" + heading.strip() + "' isn't '## " + name
                                                            + "', so the tools don't read the lines under it.",
                            "rename it '" + renamed + "'.", List.of(LineEdit.replace(section.heading(), renamed)),
                            List.of()));
                } else {
                    final List<LineEdit> edits = new ArrayList<>();
                    final List<Move> moves = new ArrayList<>();
                    for (int i = section.start(); i < section.end(); i++) {
                        if (isKeyLineOf(lines.get(i), name)) {
                            edits.add(LineEdit.remove(i));
                            moves.add(new Move(name, lines.get(i)));
                        }
                    }
                    findings.add(Finding.problem("heading", "Heading '" + heading.strip() + "' isn't '## " + name
                                                            + "', so the tools don't read the lines under it.",
                            "move its " + name + " lines under the doc's '## " + name + "'.", edits, moves));
                }
                return true;
            }
        }
        return false;
    }

    // Lines that would be ## Fields lines naming a field an index here has, under another heading.
    private static void misplacedFields(final List<String> lines,
                                        final Section section,
                                        final Scope scope,
                                        final List<Finding> findings) {
        if (scope.fields().isEmpty()) {
            return;
        }
        final List<LineEdit> edits = new ArrayList<>();
        final List<Move> moves = new ArrayList<>();
        final List<String> found = new ArrayList<>();
        for (int i = section.start(); i < section.end(); i++) {
            final Matcher bullet = InstructionRules.BULLET.matcher(lines.get(i));
            if (!bullet.matches()) {
                continue;
            }
            final String key = FIELD_SUFFIX.matcher(InstructionRules.normaliseKey(bullet.group(1))).replaceAll("");
            final String canonical = InstructionRules.FIELDS.get(key);
            final List<String> names = InstructionRules.fieldNames(bullet.group(2));
            if (canonical != null && !names.isEmpty() && names.stream().allMatch(scope.fields()::contains)) {
                edits.add(LineEdit.remove(i));
                moves.add(new Move(InstructionRules.FIELDS_SECTION, "- " + canonical + ": " + String.join(", ",
                        names)));
                found.add(lines.get(i).strip());
            }
        }
        if (!found.isEmpty()) {
            final String where = section.heading() < 0
                    ? "above the doc's first heading"
                    : "under '" + lines.get(section.heading()).strip() + "'";
            findings.add(Finding.problem("misplaced_fields", "'" + String.join("', '", found) + "' " + where
                                                             + " name" + (found.size() == 1
                    ? "s"
                    : "") + " fields the indexes here have, but the tools read field lines only under "
                                                             + "'## Fields'.",
                    "move " + (found.size() == 1
                            ? "it"
                            : "them") + " under '## Fields' (" + (sectionHeadingAt(lines,
                            InstructionRules.FIELDS_SECTION) < 0
                            ? "a new section at the end"
                            : "the doc's own") + ") as " + moves.stream().map(move -> "'" + move.line() + "'")
                            .reduce((a, b) -> a + ", " + b).orElse("") + ".", edits, moves));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // ## Fields lines

    // A field named that no index where the doc applies has: the tools pass over it.
    private static void unknownFields(final FieldLine line, final Scope scope, final List<Finding> findings) {
        final List<String> missing = line.names().stream().filter(name -> !scope.fields().contains(name)).toList();
        if (missing.isEmpty()) {
            return;
        }
        final List<String> fixed = new ArrayList<>();
        final List<String> replaced = new ArrayList<>();
        final List<String> noneClose = new ArrayList<>();
        for (final String name : line.names()) {
            if (scope.fields().contains(name)) {
                fixed.add(name);
                continue;
            }
            final List<String> close = closest(name, scope.fields());
            if (close.size() == 1) {
                fixed.add(close.getFirst());
                replaced.add(name + " -> " + close.getFirst());
            } else {
                fixed.add(name);
                noneClose.add(name + (close.isEmpty()
                        ? ""
                        : " (as close: " + String.join(", ", close) + ")"));
            }
        }
        final boolean all = missing.size() == line.names().size();
        final String message = "'## Fields' line '" + line.text().strip() + "' names " + String.join(", ", missing)
                               + ", which no index where the doc applies has" + (all
                ? ", so the tools pass over the line."
                : "; the tools pass over " + (missing.size() == 1
                        ? "it."
                        : "them."));
        if (replaced.isEmpty()) {
            findings.add(new Finding(all
                    ? Kind.PROBLEM
                    : Kind.SUGGESTION, "unknown_field", message, "none is close: " + String.join(", ", noneClose)
                                                             + "; give the field as the index names it, or remove "
                                                             + "it.", List.of(), List.of()));
            return;
        }
        final String newLine = withNames(line.text(), fixed);
        findings.add(new Finding(all
                ? Kind.PROBLEM
                : Kind.SUGGESTION, "unknown_field", message, "replace it with '" + newLine.strip() + "' ("
                                                             + String.join(", ", replaced) + (noneClose.isEmpty()
                ? ""
                : "; none is close to " + String.join(", ", noneClose)) + ").",
                List.of(LineEdit.replace(line.index(), newLine)), List.of()));
    }

    // The same key twice in the doc's ## Fields: the later line applies.
    private static void repeatedKeys(final List<FieldLine> fieldLines, final List<Finding> findings) {
        final Map<String, FieldLine> first = new LinkedHashMap<>();
        for (final FieldLine line : fieldLines) {
            final FieldLine earlier = first.putIfAbsent(line.key(), line);
            if (earlier == null) {
                continue;
            }
            if (earlier.names().equals(line.names())) {
                findings.add(Finding.suggestion("repeated_key", "'## Fields' gives " + line.key() + " twice, the "
                                                                + "same both times.",
                        "remove the second '" + line.text().strip() + "'.", List.of(LineEdit.remove(line.index())),
                        List.of()));
            } else {
                findings.add(Finding.problem("repeated_key", "'## Fields' gives " + line.key() + " twice: the "
                                                             + "later line, '" + line.text().strip() + "', applies "
                                                             + "and '" + earlier.text().strip() + "' is never used.",
                        "keep the one meant and remove the other (ask the user which).", List.of(), List.of()));
            }
            first.put(line.key(), line);
        }
    }

    // A key a more general doc gives too: the same (redundant here), or other fields (this doc's apply here).
    private static void generalDocs(final List<FieldLine> fieldLines,
                                    final String folder,
                                    final Scope scope,
                                    final List<Finding> findings) {
        final Map<String, FieldRule> general = InstructionRules.fields(scope.general());
        final Map<String, FieldLine> mine = new LinkedHashMap<>();
        fieldLines.forEach(line -> mine.put(line.key(), line));
        for (final FieldLine line : mine.values()) {
            final FieldRule rule = general.get(line.key());
            if (rule == null) {
                continue;
            }
            if (rule.fields().equals(line.names())) {
                findings.add(Finding.suggestion("same_as_general", "'" + line.text().strip() + "' says what the "
                                                                   + "AGENTS doc in " + rule.from() + " says "
                                                                   + "already.",
                        "remove it here.", List.of(LineEdit.remove(line.index())), List.of()));
            } else {
                findings.add(Finding.note("overrides_general", "In " + folder + " and below, '" + line.text().strip()
                                                               + "' applies, not the " + line.key() + " line of the "
                                                               + "AGENTS doc in " + rule.from() + " ("
                                                               + String.join(", ", rule.fields())
                                                               + "), which applies elsewhere."));
            }
        }
    }

    // A ## Fields line this doc puts in force for an index whose kept index plan maps the thing's event-logging path
    // to another field the index has.
    private static void plans(final String folder,
                              final String text,
                              final List<FieldLine> fieldLines,
                              final Scope scope,
                              final List<Finding> findings) {
        final Map<String, FieldLine> mine = new LinkedHashMap<>();
        fieldLines.forEach(line -> mine.put(line.key(), line));
        // By key, then the plan's field and the line's field: the indexes they disagree for.
        final Map<List<String>, List<String>> disagreeing = new LinkedHashMap<>();
        final Map<List<String>, String> pipelines = new HashMap<>();
        for (final IndexFacts index : scope.indexes()) {
            for (final Map.Entry<String, List<PlanField>> planned : index.plans().entrySet()) {
                final FieldLine line = mine.get(planned.getKey());
                if (line == null) {
                    continue;
                }
                final FieldRule rule = scope.inForce(folder, text, index).get(planned.getKey());
                if (rule == null || !folder.equalsIgnoreCase(rule.from())) {
                    continue;
                }
                final String used = rule.fields().stream().filter(index.fields()::contains).findFirst().orElse(null);
                final List<String> planFields = index.plannedFields(planned.getKey());
                if (planFields.size() > 1) {
                    // The pipelines feeding it map the path to different fields: no one field is the plans', so
                    // replacing the line with one would be wrong for the other pipelines' events.
                    findings.add(Finding.note("plans_differ", "Index " + index.name() + " is fed by pipelines "
                                                              + "whose index plans map "
                                                              + conventionPath(planned.getKey())
                                                              + " to different fields: " + String.join(", ",
                            planned.getValue().stream()
                                    .filter(field -> index.fields().contains(field.field()))
                                    .map(field -> field.field() + " ('" + field.pipeline() + "')")
                                    .distinct()
                                    .toList()) + "; '" + line.text().strip() + "' gives " + used + " for "
                                                              + "all of them, so events of the other pipelines' "
                                                              + "streams are read by " + used + " only where they "
                                                              + "hold it too."));
                    continue;
                }
                for (final PlanField planField : planned.getValue()) {
                    final String plan = planField.field();
                    if (used != null && !used.equals(plan) && index.fields().contains(plan)) {
                        final List<String> group = List.of(planned.getKey(), plan, used);
                        final List<String> named = disagreeing.computeIfAbsent(group, k -> new ArrayList<>());
                        if (!named.contains(index.name())) {
                            named.add(index.name());
                        }
                        pipelines.putIfAbsent(group, planField.pipeline());
                    }
                }
            }
        }
        disagreeing.forEach((group, indexes) -> {
            final FieldLine line = mine.get(group.get(0));
            final List<String> names = new ArrayList<>();
            names.add(group.get(1));
            line.names().stream().filter(name -> !name.equals(group.get(1))).forEach(names::add);
            final String newLine = withNames(line.text(), names);
            findings.add(Finding.suggestion("plan_disagrees", "'" + line.text().strip() + "' gives " + group.get(2)
                                                              + " for index " + String.join(", ", indexes)
                                                              + ", but the index plan of '"
                                                              + pipelines.get(group) + "' maps "
                                                              + conventionPath(group.get(0)) + " to " + group.get(1)
                                                              + ".",
                    "if " + group.get(1) + " is meant (ask the user; never change it without them), replace it "
                    + "with '" + newLine.strip() + "'.", List.of(LineEdit.replace(line.index(), newLine)),
                    List.of()));
        });
    }

    // Indexes where the doc applies whose user field only a field convention would find: the line to make it the
    // site's.
    private static void unruled(final String folder,
                                final String text,
                                final List<FieldLine> fieldLines,
                                final Scope scope,
                                final List<Finding> findings) {
        final String key = InstructionRules.USER_FIELD;
        final Map<String, List<String>> byField = new LinkedHashMap<>();
        for (final IndexFacts index : scope.indexes()) {
            final String convention = index.conventions().get(key);
            if (convention == null || !index.fields().contains(convention) || index.plans().containsKey(key)) {
                continue;
            }
            final FieldRule rule = scope.inForce(folder, text, index).get(key);
            if (rule != null && rule.fields().stream().anyMatch(index.fields()::contains)) {
                continue;
            }
            byField.computeIfAbsent(convention, k -> new ArrayList<>()).add(index.name());
        }
        final FieldLine userLine = fieldLines.stream().filter(line -> key.equals(line.key())).reduce((a, b) -> b)
                .orElse(null);
        byField.forEach((field, indexes) -> {
            final String message = "Index " + String.join(", ", indexes) + " would find the " + key + " only by "
                                   + "a field convention (" + field + "), a guess.";
            if (userLine == null) {
                final String added = "- " + key + ": " + field;
                findings.add(Finding.suggestion("convention_only", message, "add '" + added + "' under '## Fields'"
                                                                            + " if " + field + " is the " + key
                                                                            + " field (ask the user).", List.of(),
                        List.of(new Move(InstructionRules.FIELDS_SECTION, added))));
            } else {
                final List<String> names = new ArrayList<>(userLine.names());
                names.add(field);
                final String newLine = withNames(userLine.text(), names);
                findings.add(Finding.suggestion("convention_only", message, "replace '" + userLine.text().strip()
                                                                            + "' with '" + newLine.strip()
                                                                            + "' if " + field + " is the " + key
                                                                            + " field (ask the user).",
                        List.of(LineEdit.replace(userLine.index(), newLine)), List.of()));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    static List<String> lines(final String text) {
        return List.of(NullSafe.string(text).split("\\R", -1));
    }

    // The doc's sections, each from a heading (or the doc's start) to the next heading.
    private static List<Section> sections(final List<String> lines) {
        final List<Section> out = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        int heading = -1;
        int start = 0;
        for (int i = 0; i <= lines.size(); i++) {
            if (i == lines.size() || InstructionRules.isHeading(lines.get(i))) {
                final String typed = heading < 0
                        ? null
                        : InstructionRules.typedSection(lines.get(heading));
                out.add(new Section(heading, start, i, typed, typed != null && seen.add(typed)));
                heading = i;
                start = i + 1;
            }
        }
        return out;
    }

    // The line of the first heading of a section the tools read, or -1.
    private static int sectionHeadingAt(final List<String> lines, final String section) {
        for (int i = 0; i < lines.size(); i++) {
            if (section.equals(InstructionRules.typedSection(lines.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    // The lines added at the end of the first of a section, or in a new section at the end of the doc.
    private static void addToSection(final List<String> out, final String section, final List<String> added) {
        final int heading = sectionHeadingAt(out, section);
        if (heading < 0) {
            while (!out.isEmpty() && out.getLast().isBlank()) {
                out.removeLast();
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            out.add("## " + section);
            out.add("");
            out.addAll(added);
            out.add("");
            return;
        }
        int end = heading + 1;
        while (end < out.size() && !InstructionRules.isHeading(out.get(end))) {
            end++;
        }
        int at = end;
        while (at > heading + 1 && out.get(at - 1).isBlank()) {
            at--;
        }
        if (at == heading + 1) {
            // An empty section: a blank line after the heading, as one is written.
            out.add(at, "");
            at++;
        }
        out.addAll(at, added);
    }

    private static boolean hasKeyOf(final List<String> lines, final Section section, final String name) {
        for (int i = section.start(); i < section.end(); i++) {
            if (isKeyLineOf(lines.get(i), name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeyLineOf(final String line, final String section) {
        if (InstructionRules.DOCUMENTATION_SECTION.equals(section)) {
            return InstructionRules.LAYOUT_BULLET.matcher(line).matches();
        }
        final Matcher bullet = InstructionRules.BULLET.matcher(line);
        return bullet.matches() && InstructionRules.keysOf(section)
                .contains(InstructionRules.normaliseKey(bullet.group(1)));
    }

    // A heading's words, lower case, without its marks, e.g. '## **Field names:**' -> 'field names'.
    private static String headingWords(final String heading) {
        final String words = HEADING_MARKS.matcher(heading.strip()).replaceFirst("").toLowerCase(Locale.ROOT);
        return SPACES.matcher(NOT_A_LETTER.matcher(words).replaceAll(" ")).replaceAll(" ").strip();
    }

    private static String headingLevel(final String heading) {
        final Matcher marks = HEADING_MARKS.matcher(heading.strip());
        return marks.find()
                ? marks.group()
                : "##";
    }

    // The line with its fields given anew, its key and bullet as written.
    private static String withNames(final String line, final List<String> names) {
        final int colon = line.indexOf(':');
        return line.substring(0, colon + 1) + " " + String.join(", ", names);
    }

    private static String replaceOnce(final String line, final String from, final String to) {
        final int at = line.indexOf(from);
        return at < 0
                ? line
                : line.substring(0, at) + to + line.substring(at + from.length());
    }

    private static String what(final String section) {
        return switch (section) {
            case InstructionRules.REQUIRED_IMPORTS_SECTION -> "stage";
            case InstructionRules.COMPONENT_TEMPLATES_SECTION -> "rule";
            case InstructionRules.DOCUMENTATION_SECTION -> "layout";
            default -> "key";
        };
    }

    private static String known(final String section) {
        final Collection<String> keys = InstructionRules.FIELDS_SECTION.equals(section)
                ? InstructionRules.FIELDS.values()
                : InstructionRules.keysOf(section);
        return String.join(", ", new TreeSet<>(keys));
    }

    private static String conventionPath(final String key) {
        return NullSafe.nonBlankStringElse(InstructionRules.CONVENTION_PATHS.get(key), "its event-logging path");
    }

    /// The candidates closest to what is given, ignoring case: those at the least edit distance, if it is small
    /// for the length (1 up to 3 letters, 2 up to 8, else 3).
    ///
    /// @param given      What is given.
    /// @param candidates What there is.
    /// @return The closest, one if it is clear; several as close; none if none is close.
    static List<String> closest(final String given, final Collection<String> candidates) {
        final String lower = NullSafe.string(given).toLowerCase(Locale.ROOT);
        final int most = lower.length() <= 3
                ? 1
                : lower.length() <= 8
                        ? 2
                        : 3;
        int best = Integer.MAX_VALUE;
        final Set<String> out = new TreeSet<>();
        for (final String candidate : NullSafe.collection(candidates)) {
            final int distance = distance(lower, candidate.toLowerCase(Locale.ROOT));
            if (distance > most) {
                continue;
            }
            if (distance < best) {
                best = distance;
                out.clear();
            }
            if (distance == best) {
                out.add(candidate);
            }
        }
        return List.copyOf(out);
    }

    /// @param a A string.
    /// @param b Another.
    /// @return The edit (Levenshtein) distance between them.
    static int distance(final String a, final String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            final int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1)
                        ? 0
                        : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[b.length()];
    }


    // --------------------------------------------------------------------------------


    /// What a finding is.
    public enum Kind {
        /// The tools can't apply a line as written.
        PROBLEM("Problem"),
        /// It would work better changed.
        SUGGESTION("Suggestion"),
        /// How the doc is read; nothing to change.
        NOTE("Note");

        private final String label;

        Kind(final String label) {
            this.label = label;
        }

        /// @return Its name in the result.
        public String label() {
            return label;
        }
    }


    // --------------------------------------------------------------------------------


    /// A finding.
    ///
    /// @param kind    Problem, suggestion or note.
    /// @param rule    The check's name, e.g. `unknown_key`.
    /// @param message What was found.
    /// @param fix     The fix proposed, in words with the replacement text; null for a note.
    /// @param edits   The fix's changes to the doc's lines.
    /// @param moves   The fix's lines to add to a section the tools read.
    public record Finding(Kind kind, String rule, String message, String fix, List<LineEdit> edits,
                          List<Move> moves) {

        /// Copies the lists.
        public Finding {
            edits = List.copyOf(NullSafe.list(edits));
            moves = List.copyOf(NullSafe.list(moves));
        }

        static Finding problem(final String rule,
                               final String message,
                               final String fix,
                               final List<LineEdit> edits,
                               final List<Move> moves) {
            return new Finding(Kind.PROBLEM, rule, message, fix, edits, moves);
        }

        static Finding suggestion(final String rule,
                                  final String message,
                                  final String fix,
                                  final List<LineEdit> edits,
                                  final List<Move> moves) {
            return new Finding(Kind.SUGGESTION, rule, message, fix, edits, moves);
        }

        static Finding note(final String rule, final String message) {
            return new Finding(Kind.NOTE, rule, message, null, List.of(), List.of());
        }
    }


    // --------------------------------------------------------------------------------


    /// A change to a line of a doc.
    ///
    /// @param line The line's index, from 0.
    /// @param text Its new text; null to remove it.
    public record LineEdit(int line, String text) {

        static LineEdit replace(final int line, final String text) {
            return new LineEdit(line, Objects.requireNonNull(text));
        }

        static LineEdit remove(final int line) {
            return new LineEdit(line, null);
        }
    }


    // --------------------------------------------------------------------------------


    /// A line to add to a section the tools read.
    ///
    /// @param section The section, e.g. [InstructionRules#FIELDS_SECTION].
    /// @param line    The line.
    public record Move(String section, String line) {

    }


    // --------------------------------------------------------------------------------


    /// What the checks need of Stroom.
    ///
    /// @param docs    The AGENTS docs the user can view, most general first (without the one checked, where it is
    ///                a draft of the doc saved in its folder).
    /// @param indexes The indexes the user can use, at least those where the doc checked applies.
    public record Facts(List<InstructionDoc> docs, List<IndexFacts> indexes) {

        /// No facts: only the doc's form is checked.
        public static final Facts NONE = new Facts(List.of(), List.of());

        /// Copies the lists.
        public Facts {
            docs = List.copyOf(NullSafe.list(docs));
            indexes = List.copyOf(NullSafe.list(indexes));
        }
    }


    // --------------------------------------------------------------------------------


    /// What the checks know of an index.
    ///
    /// @param name        Its name.
    /// @param folder      Its folder, e.g. `/Indexes`.
    /// @param fields      Its fields.
    /// @param planned     For each `## Fields` key ([InstructionRules#CONVENTION_PATHS]), the field (one the index
    ///                    has) the index plan kept with the first pipeline feeding it maps the key's event-logging
    ///                    path to.
    /// @param conventions For each `## Fields` key, the field (one the index has) a field convention gives.
    /// @param plans       For each `## Fields` key, the field each pipeline feeding the index maps the path to, in
    ///                    the pipelines' order (one entry a pipeline): several pipelines may feed one index, each
    ///                    with its own plan.
    public record IndexFacts(String name, String folder, Set<String> fields, Map<String, PlanField> planned,
                             Map<String, String> conventions, Map<String, List<PlanField>> plans) {

        /// Copies the collections; each one's plans give its first plan's field, and its first plan's field its
        /// plans where no plans are given.
        public IndexFacts {
            fields = Set.copyOf(NullSafe.set(fields));
            conventions = Map.copyOf(NullSafe.map(conventions));
            final Map<String, List<PlanField>> all = new LinkedHashMap<>();
            NullSafe.map(plans).forEach((key, list) -> {
                if (!NullSafe.isEmptyCollection(list)) {
                    all.put(key, List.copyOf(list));
                }
            });
            final Map<String, PlanField> first = new LinkedHashMap<>(NullSafe.map(planned));
            all.forEach((key, list) -> first.putIfAbsent(key, list.getFirst()));
            first.forEach((key, field) -> all.putIfAbsent(key, List.of(field)));
            planned = Map.copyOf(first);
            plans = Map.copyOf(all);
        }

        /// One pipeline's plan for each key, or several agreeing.
        ///
        /// @param name        Its name.
        /// @param folder      Its folder.
        /// @param fields      Its fields.
        /// @param planned     For each key, the field the plan maps its path to.
        /// @param conventions For each key, the field a field convention gives.
        public IndexFacts(final String name,
                          final String folder,
                          final Set<String> fields,
                          final Map<String, PlanField> planned,
                          final Map<String, String> conventions) {
            this(name, folder, fields, planned, conventions, Map.of());
        }

        /// @param key A `## Fields` key.
        /// @return The fields the feeding pipelines' plans map its path to (those the index has), each once, in the
        /// pipelines' order: more than one where their plans differ.
        public List<String> plannedFields(final String key) {
            return plans.getOrDefault(key, List.of()).stream()
                    .map(PlanField::field)
                    .filter(fields::contains)
                    .distinct()
                    .toList();
        }
    }


    // --------------------------------------------------------------------------------


    /// A field an index plan maps an event-logging path to.
    ///
    /// @param field    The field.
    /// @param pipeline The name of the pipeline whose plan it is.
    public record PlanField(String field, String pipeline) {

    }


    // --------------------------------------------------------------------------------


    // A section of a doc: its heading's line (-1 for the text above the first heading), its lines' range, the section
    // the tools read whose heading it is (or null), and whether it is the first such.
    private record Section(int heading, int start, int end, String typed, boolean first) {

    }


    // --------------------------------------------------------------------------------


    // A ## Fields line the tools read: its index, key, fields and text.
    private record FieldLine(int index, String key, List<String> names, String text) {

    }


    // --------------------------------------------------------------------------------


    // Where a doc applies: the docs above and below it, the indexes there, and their fields.
    private record Scope(List<InstructionDoc> general, List<InstructionDoc> below, List<IndexFacts> indexes,
                         Set<String> fields) {

        static Scope of(final String folder, final Facts facts) {
            if (folder == null) {
                return new Scope(List.of(), List.of(), List.of(), Set.of());
            }
            final String at = AgentInstructions.normalise(folder);
            final List<InstructionDoc> general = facts.docs().stream()
                    .filter(doc -> !doc.folder().equalsIgnoreCase(at) && AgentInstructions.isWithin(at, doc.folder()))
                    .toList();
            final List<InstructionDoc> below = facts.docs().stream()
                    .filter(doc -> !doc.folder().equalsIgnoreCase(at) && AgentInstructions.isWithin(doc.folder(), at))
                    .toList();
            final List<IndexFacts> indexes = facts.indexes().stream()
                    .filter(index -> AgentInstructions.isWithin(AgentInstructions.normalise(index.folder()), at))
                    .toList();
            final Set<String> fields = new LinkedHashSet<>();
            indexes.forEach(index -> fields.addAll(index.fields()));
            return new Scope(general, below, indexes, fields);
        }

        // The ## Fields lines in force for an index, this doc's text among the docs.
        Map<String, FieldRule> inForce(final String folder, final String text, final IndexFacts index) {
            final List<InstructionDoc> docs = new ArrayList<>();
            general.stream().filter(doc -> doc.appliesTo(index.folder())).forEach(docs::add);
            docs.add(new InstructionDoc(null, folder, text, List.of()));
            below.stream().filter(doc -> doc.appliesTo(index.folder())).forEach(docs::add);
            return InstructionRules.fields(docs);
        }
    }
}
