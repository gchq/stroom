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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.util.json.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A build_translation_xslt result too large for a client outside Stroom to take whole: each rule's Data entries
/// (`<action element>/Data[<field>]`) counted in its `events` summary rather than listed. Seen in eval cases 35 and 36
/// over Claude Code: results of about 50 KB saved to files by the client, most of them the rules' Data (in the Pega
/// sample, 19,000 of 32,000 characters for 13 rules). The mapping, kept by the server, still lists them; each rule
/// names its first few, so that the agent can see what it might type further without reading the saved result (seen
/// in chats 184-186: 13 reads of it for which fields each rule kept as Data). The sample check's warnings, which the
/// result's warnings repeat, are said once.
final class BuiltRulesTrim {

    private static final String EVENTS = "events";
    private static final String DATA = "/Data[";
    private static final String SAMPLE_CHECK = "sample_check";
    private static final String WARNINGS = "warnings";
    private static final String EXAMPLE = " E.g. ";
    // The most characters of a warning, and of the changes applied, said in an outline.
    private static final int WARNING_CHARS = 400;
    private static final int CHANGES_CHARS = 600;
    // A warning's list of rules past this many characters names only its first few.
    private static final int RULES_CHARS = 200;
    private static final int RULES_NAMED = 5;
    // The most Data names said of a rule; in an outline, fewer in turn until its rules fit the room they have (none
    // last: the Data counted only).
    private static final int DATA_NAMED = 12;
    private static final List<Integer> OUTLINE_DATA_NAMED = List.of(DATA_NAMED, 6, 3, 0);
    private static final String DATA_NAMES = "data_names";
    private static final String MORE_MARK = "… ";

    private BuiltRulesTrim() {
        // Static only.
    }

    /// Counts each rule's Data in the result's `events`, and leaves out the sample check's warnings the result's
    /// warnings repeat, when the caller takes outlines ([ToolCaller#takesOutlines]) and the result is past the limit;
    /// Stroom AI's own chat, which keeps a large result in its workspace itself, gets it whole.
    ///
    /// @param caller Who called the tool.
    /// @param result The build's result, changed in place.
    /// @param limit  The most characters a result is given whole in ([LargeResults#WHOLE_RESULT_CHARS]).
    /// @return True if it was trimmed.
    static boolean trimIfLarge(final ToolCaller caller, final Map<String, Object> result, final int limit) {
        if (!caller.takesOutlines() || !(result.get(EVENTS) instanceof List<?>)
            || JsonUtil.writeValueAsString(result, false).length() <= limit) {
            return false;
        }
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(result.get(EVENTS))) {
            final Map<String, Object> said = new LinkedHashMap<>(rule);
            final List<String> fields = new ArrayList<>();
            final List<String> dataNames = new ArrayList<>();
            for (final Object field : JsonValues.listOrEmpty(rule.get("fields"))) {
                final String path = ToolText.str(field);
                if (path.contains(DATA)) {
                    dataNames.add(dataName(path));
                } else {
                    fields.add(path);
                }
            }
            said.put("fields", fields);
            said.put("data", dataNames.size());
            said.put(DATA_NAMES, capped(dataNames, DATA_NAMED));
            rules.add(said);
        }
        result.put(EVENTS, rules);
        result.put("events_note", "Each rule's Data are counted here, and only the first " + DATA_NAMED + " "
                                  + "named (data_names; this result is too large to show whole): the mapping lists "
                                  + "them all, the fields each rule carries as Data of its action element.");
        sampleWarningsOnce(result);
        return true;
    }

    /// Gives a build_translation_xslt result in outline when it is still too large for the caller once trimmed
    /// ([LargeResults#apply]): seen in eval case 68 (300 rules), 110,000 characters, the rules' summaries 98,000 even
    /// with their Data counted; and in cases 35 and 36, 48,000 and 66,000, the sample check's warnings for
    /// extractions that leave fields empty (some 430 characters each, most of it an example). Outlined: each rule in
    /// a line (its conditions, how many fields and Data it writes, and the first Data names, as many as the room
    /// allows); each warning without its example; the changes applied counted. The warnings are outlined first, so
    /// that the room their examples took is left for the rules' Data names. The problems, the saved XSLT, the XSLT
    /// itself when not saved, and what comes next kept whole.
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param key       The build, else the XSLT's name.
    /// @param result    The result, changed in place.
    /// @param limit     The most characters it is given whole in.
    /// @return True if it was given in outline.
    static boolean outlineIfLarge(final ToolCaller caller,
                                  final Optional<SessionWorkspace> workspace,
                                  final String key,
                                  final Map<String, Object> result,
                                  final int limit) {
        return LargeResults.of(BuildTranslationXsltTool.NAME, key)
                .keep("saved", "xslt", "field_mapping_needs", "schema_version", "source_notes_check",
                        "standing_instructions", "changes_applied_to", "events_note", "sample_check.problems")
                .outline(WARNINGS, (value, room) -> withoutExamples(value))
                .outline(EVENTS, BuiltRulesTrim::rulesInBrief)
                .outline("changes_applied", (value, room) -> {
                    final List<String> changes = JsonValues.strings(value);
                    return changes.size() + " change(s) applied: " + LargeResults.head(String.join("; ",
                            changes), CHANGES_CHARS);
                })
                .apply(caller, workspace, result, limit);
    }

    // Each rule in a line: its name, its conditions, how many fields and Data it writes, and its first Data names,
    // as many as fit the room (none, the Data counted only, if even three a rule do not).
    private static Object rulesInBrief(final Object value, final LargeResults.Room room) {
        List<Object> rules = List.of();
        for (final int named : OUTLINE_DATA_NAMED) {
            rules = rulesInBrief(value, named);
            if (LargeResults.length(rules) <= room.target()) {
                return rules;
            }
        }
        return rules;
    }

    private static List<Object> rulesInBrief(final Object value, final int named) {
        final List<Object> rules = new ArrayList<>();
        for (final Object entry : JsonValues.listOrEmpty(value)) {
            if (!(entry instanceof final Map<?, ?> rule)) {
                rules.add(entry);
                continue;
            }
            final List<Object> fields = JsonValues.listOrEmpty(rule.get("fields"));
            final List<String> dataNames = rule.containsKey(DATA_NAMES)
                    ? JsonValues.strings(rule.get(DATA_NAMES))
                    : fields.stream().map(ToolText::str).filter(path -> path.contains(DATA))
                            .map(BuiltRulesTrim::dataName).toList();
            final long data = rule.get("data") instanceof final Number number
                    ? number.longValue()
                    : dataNames.size();
            final long others = fields.stream().filter(field -> !ToolText.str(field).contains(DATA)).count();
            final List<String> when = JsonValues.strings(rule.get("when"));
            rules.add(rule.get("event") + ": " + (when.isEmpty()
                    ? "every record"
                    : String.join(" and ", when)) + " (" + others + " fields, " + data + " Data"
                      + dataNamesSaid(dataNames, data, named) + ")");
        }
        return rules;
    }

    // `: a, b, … N more` of a rule's Data names (already capped, its last entry `… N more`, when from a trimmed
    // entry), at most the given number named; nothing if none are.
    private static String dataNamesSaid(final List<String> dataNames, final long data, final int named) {
        final List<String> names = dataNames.stream().filter(name -> !name.startsWith(MORE_MARK)).toList();
        if (named == 0 || names.isEmpty()) {
            return "";
        }
        final List<String> said = names.subList(0, Math.min(named, names.size()));
        return ": " + String.join(", ", said) + (data > said.size()
                ? ", " + MORE_MARK + (data - said.size()) + " more"
                : "");
    }

    // A Data entry's name from its typed path, `EventDetail/Send/Data[subject]` giving `subject`.
    private static String dataName(final String path) {
        final int start = path.indexOf(DATA) + DATA.length();
        final int end = path.lastIndexOf(']');
        return end > start
                ? path.substring(start, end)
                : path.substring(start);
    }

    // The first so many names, then `… N more` if there are more.
    private static List<String> capped(final List<String> names, final int most) {
        if (names.size() <= most) {
            return names;
        }
        final List<String> capped = new ArrayList<>(names.subList(0, most));
        capped.add(MORE_MARK + (names.size() - most) + " more");
        return capped;
    }

    /// Each warning without its example (from " E.g. "), and the rules it names, when many, counted past the first:
    /// the gist kept. Seen in eval cases 35 and 36, the sample check's warnings for an extraction that leaves fields
    /// empty, most of each an example of the text; in case 68, a warning naming all 300 rules.
    ///
    /// @param value A list of warnings.
    /// @return Them in brief.
    static Object withoutExamples(final Object value) {
        final List<Object> warnings = new ArrayList<>();
        for (final Object entry : JsonValues.listOrEmpty(value)) {
            final String warning = rulesCounted(ToolText.str(entry));
            final int example = warning.indexOf(EXAMPLE);
            warnings.add(LargeResults.head(example > 0
                    ? warning.substring(0, example) + " (its example in the saved result)"
                    : warning, WARNING_CHARS));
        }
        return warnings;
    }

    // A warning's leading list of rules ("[a, b, c, ...] ..."), when long, its first few named and the rest counted.
    private static String rulesCounted(final String warning) {
        final int end = warning.indexOf("] ");
        if (!warning.startsWith("[") || end < RULES_CHARS) {
            return warning;
        }
        final List<String> rules = List.of(warning.substring(1, end).split(", "));
        if (rules.size() <= RULES_NAMED) {
            return warning;
        }
        return "[" + String.join(", ", rules.subList(0, Math.min(RULES_NAMED, rules.size()))) + " and "
               + (rules.size() - RULES_NAMED) + " more rules]" + warning.substring(end + 1);
    }

    // The sample check's warnings are the result's own warnings that start "sample: ": seen in eval cases 35 and 36,
    // a third of results of 69,000 and 100,000 characters, said twice.
    private static void sampleWarningsOnce(final Map<String, Object> result) {
        if (result.get(SAMPLE_CHECK) instanceof final Map<?, ?> check && check.containsKey(WARNINGS)) {
            final Map<String, Object> kept = new LinkedHashMap<>();
            check.forEach((key, value) -> {
                if (!WARNINGS.equals(key)) {
                    kept.put(String.valueOf(key), value);
                }
            });
            kept.put("warnings_note", "The sample check's warnings are in the result's warnings, each starting "
                                      + "'sample: '.");
            result.put(SAMPLE_CHECK, kept);
        }
    }
}
