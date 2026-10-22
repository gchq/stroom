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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.PythonStrings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The Field mapping section of a CEF pipeline's documentation: the header, then by event type (the default) a
/// table of the keys every event gets and one per kind of event (kinds with roughly the same keys together); compact,
/// one table with an Event type column; and the values not sent. Ported from the Stroom MCP server's
/// `plan_markdown` in `utils/cef.py`.
public final class CefDocs {

    private static final Pattern QUOTED = Pattern.compile("'[^']*'");
    private static final List<String> TABLE_HEAD = List.of(
            "| Event path | CEF key | ArcSight field | What it holds | Example |",
            "| --- | --- | --- | --- | --- |");

    private CefDocs() {
        // Static only.
    }

    /// `description_key`: how a CEF field's description is kept: by key for every event's, by kind and key
    /// otherwise (a custom slot holds what each kind of event puts in it).
    ///
    /// @param kind The kind of event, or `common`.
    /// @param key  The key.
    /// @return The description's key, e.g. `View: cs1`.
    public static String descriptionKey(final String kind, final String key) {
        return "common".equals(kind)
                ? key
                : kind + ": " + key;
    }

    /// `plan_markdown`: the Field mapping section: the header, then by event type (the default) a table of the
    /// keys every event gets and one per kind of event; compact, one table with an Event type column.
    ///
    /// @param plan      The plan.
    /// @param examples  Example values by (kind or `common` or `header`, key), from sampled lines.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions (what each means here), or null for ArcSight's words.
    /// @return The section's text, ending with a new line.
    public static String planMarkdown(final CefPlan plan,
                                      final Map<KindKey, String> examples,
                                      final String layout,
                                      final Descriptions described) {
        final List<String> out = new ArrayList<>();
        out.add("Each Event becomes one CEF (ArcSight Common Event Format) line: `CEF:0|Device Vendor|Device "
                + "Product|Device Version|Device Event Class ID|Name|Severity|` then key=value pairs. "
                + (CefPlan.KAFKA.equals(plan.output())
                ? "Each line is the value of one Kafka record on topic `" + PythonStrings.str(plan.topic())
                  + "`, one Event a record"
                  + (PythonStrings.truthy(plan.kafkaKey())
                        ? ", keyed by `" + plan.kafkaKey() + "`"
                        : "") + "."
                : "The lines are written as text, one an Event."));
        out.add("Keys outside ArcSight's CEF dictionary are " + (plan.customKeys()
                ? "allowed."
                : "not used: ArcSight indexes only its dictionary's keys. Its custom slots (cs1-cs6 and the rest) "
                  + "carry the values no standard key fits, each named by its Label key."));
        out.addAll(List.of("", "### Header", "", "| Position | CEF field | From | Example |",
                "| --- | --- | --- | --- |", "| 1 | Version | the constant 'CEF:0' | CEF:0 |"));
        for (int n = 0; n < CefKeys.HEADER.size(); n++) {
            final CefKeys.HeaderField field = CefKeys.HEADER.get(n);
            out.add("| " + (n + 2) + " | " + field.said() + " | " + cell(headerFrom(plan.header(field.name())))
                    + " | " + cell(examples.getOrDefault(new KindKey("header", field.name()), "")) + " |");
        }
        out.add("");
        final Tables tables = new Tables(examples, described);
        final boolean compact = FieldDocs.COMPACT.equals(layout);
        if (compact) {
            out.addAll(List.of("### Fields", "", "| Event type | Event path | CEF key | ArcSight field | What it "
                                                 + "holds | Example |", "| --- | --- | --- | --- | --- | --- |"));
            out.addAll(tables.rows(plan.common(), "common", true));
            plan.events().forEach((kind, fields) -> out.addAll(tables.rows(fields, kind, true)));
            out.add("");
        }
        if (!plan.common().isEmpty() && !compact) {
            out.addAll(List.of("### Every event", ""));
            out.addAll(TABLE_HEAD);
            out.addAll(tables.rows(plan.common(), "common", false));
            out.add("");
        }
        // Kinds with roughly the same keys together, under one heading naming each (asked for by the user: Send and
        // Receive, say): one row per key, its paths together, the examples each kind got.
        final Map<String, Set<String>> signatures = new LinkedHashMap<>();
        plan.events().forEach((kind, fields) -> signatures.put(kind, fields.stream()
                .map(f -> FieldDocs.anyActionPath(f.path()) + " " + f.key())
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        final List<List<String>> groups = compact
                ? List.of()
                : FieldDocs.similarGroups(signatures);
        for (final List<String> group : groups) {
            if (group.size() == 1) {
                out.addAll(List.of("### " + group.getFirst() + " events", ""));
                out.addAll(TABLE_HEAD);
                out.addAll(tables.rows(plan.events().get(group.getFirst()), group.getFirst(), false));
                out.add("");
                continue;
            }
            out.addAll(List.of("### " + String.join(", ", group) + " events", ""));
            out.addAll(TABLE_HEAD);
            out.addAll(mergedRows(plan, group, tables, examples));
            out.add("");
        }
        if (!plan.notSent().isEmpty()) {
            out.addAll(List.of("### Not sent", "", "Event values the sample holds that no CEF key carries.", "",
                    "| Event path | Event type | Why |", "| --- | --- | --- |"));
            plan.notSent().forEach(n -> out.add("| `" + cell(n.get("path")) + "` | " + cell(n.get("event_type"))
                                                + " | " + cell(n.get("why")) + " |"));
            out.add("");
        }
        return PythonStrings.rstrip(String.join("\n", out)) + "\n";
    }

    // One row per key of kinds shown together: its paths together, and each kind's example where they differ.
    private static List<String> mergedRows(final CefPlan plan, final List<String> group, final Tables tables,
                                           final Map<KindKey, String> examples) {
        final Map<String, List<Map.Entry<String, CefField>>> rows = new LinkedHashMap<>();
        for (final String kind : group) {
            for (final CefField f : plan.events().get(kind)) {
                rows.computeIfAbsent(FieldDocs.anyActionPath(f.path()) + " " + f.key(), k -> new ArrayList<>())
                        .add(Map.entry(kind, f));
            }
        }
        final List<String> merged = new ArrayList<>();
        for (final List<Map.Entry<String, CefField>> members : rows.values()) {
            final String kind = members.getFirst().getKey();
            final CefField f = members.getFirst().getValue();
            String line = tables.rows(List.of(f), kind, false).getFirst();
            final List<String> paths = members.stream().map(m -> m.getValue().path()).toList();
            if (new LinkedHashSet<>(paths).size() > 1) {
                line = line.replaceFirst(Pattern.quote("| " + cell(pathSaid(f.path())) + " |"),
                        java.util.regex.Matcher.quoteReplacement("| `" + cell(FieldDocs.combinedPath(paths)) + "` |"));
            }
            final Map<String, String> got = new LinkedHashMap<>();
            for (final Map.Entry<String, CefField> member : members) {
                final String example = examples.getOrDefault(new KindKey(member.getKey(), member.getValue().key()),
                        "");
                if (!example.isEmpty()) {
                    got.put(member.getKey(), example);
                }
            }
            if (new LinkedHashSet<>(got.values()).size() > 1 || members.size() < group.size()) {
                final String said = got.entrySet().stream()
                        .map(e -> e.getValue() + " (" + e.getKey() + ")")
                        .collect(Collectors.joining("; "));
                line = rsplitFirst(line) + "| " + cell(said) + " |";
            }
            merged.add(line);
        }
        return merged;
    }

    // An Event path as the documentation shows it: the fallbacks and constants the draft writes, in words.
    private static String pathSaid(final String path) {
        if (path.contains(CefKeys.ACTION_NAME)) {
            final String first = path.startsWith("(")
                    ? path.substring(1, path.indexOf(','))
                    : path;
            return first.equals(CefKeys.ACTION_NAME)
                    ? "the event's action element and its Action"
                    : "`" + first + "`, else the event's action element and its Action";
        }
        if (QUOTED.matcher(path).matches()) {
            return "the event's kind (" + path.substring(1, path.length() - 1) + ")";
        }
        if (path.startsWith("local-name(")) {
            return "the name of the event's action element (" + path.substring("local-name(".length(),
                    path.length() - 1) + ")";
        }
        return "`" + path + "`";
    }

    private static String headerFrom(final CefValue value) {
        if (value.value() != null) {
            return !value.value().isEmpty()
                    ? "the constant '" + value.value() + "'"
                    : "empty";
        }
        String said = pathSaid(value.source() == null
                ? ""
                : value.source());
        if (!value.map().isEmpty()) {
            said += ", mapped " + value.map().entrySet().stream()
                    .map(e -> e.getKey() + " -> " + e.getValue())
                    .collect(Collectors.joining(", "));
        }
        if (PythonStrings.truthy(value.defaultValue())) {
            said += " (else '" + value.defaultValue() + "')";
        }
        return said;
    }

    // A table cell: its pipes escaped, its lines run together.
    private static String cell(final String text) {
        return (text == null
                ? ""
                : text).replace("|", "\\|").replace("\n", " ");
    }

    // Python's line.rsplit('|', 2)[0]: the line before its second-last pipe.
    private static String rsplitFirst(final String line) {
        final int last = line.lastIndexOf('|');
        final int before = last > 0
                ? line.lastIndexOf('|', last - 1)
                : -1;
        return before < 0
                ? line
                : line.substring(0, before);
    }


    // --------------------------------------------------------------------------------


    /// What an example value is of: a kind of event (or `common`, or `header`) and a key (or a header field's
    /// name).
    ///
    /// @param kind The kind of event, `common` or `header`.
    /// @param key  The key, or the header field's name.
    public record KindKey(String kind, String key) {

    }

    // The tables' rows, with their examples and descriptions.
    private record Tables(Map<KindKey, String> examples, Descriptions described) {

        private List<String> rows(final List<CefField> fields, final String kind, final boolean typed) {
            final List<String> rows = new ArrayList<>();
            for (final CefField f : fields) {
                final CefKeys.KeyInfo info = CefKeys.KEYS.getOrDefault(f.key(), new CefKeys.KeyInfo(f.key(), "",
                        null, "key outside the CEF dictionary"));
                String meaning = info.meaning();
                // What the field means here: the agent's, from its path, label and example (asked for by the user:
                // a custom slot like deviceCustomString1 means what this source puts in it); ArcSight's words as
                // fallback.
                if (described != null) {
                    final String example = examples.getOrDefault(new KindKey(kind, f.key()), "");
                    final Map<String, String> says = new LinkedHashMap<>();
                    says.put("ArcSight", meaning);
                    meaning = described.of(descriptionKey(kind, f.key()), f.path() + " -> " + f.key()
                                                                          + (PythonStrings.truthy(f.label())
                            ? " (labelled '" + f.label() + "')"
                            : ""), example.isEmpty()
                            ? List.of()
                            : List.of(example), says, f.path());
                }
                String what = meaning + (PythonStrings.truthy(f.label()) && CefKeys.SLOT_KEYS.contains(f.key())
                        ? "; labelled '" + f.label() + "' (" + f.key() + "Label = " + labelFullName(f.key()) + ")"
                        : "");
                if (CefField.EPOCH_MS.equals(f.transform())) {
                    what += "; milliseconds since 1970";
                } else if (CefField.OUTCOME.equals(f.transform())) {
                    what += "; from Outcome/Success (no Outcome: success)";
                }
                if (info.maxLength() != null) {
                    what += "; at most " + info.maxLength() + " characters";
                }
                rows.add((typed
                        ? "common".equals(kind)
                                ? "| every event "
                                : "| " + cell(kind) + " "
                        : "") + "| " + cell(pathSaid(f.path())) + " | " + f.key() + " | " + info.fullName() + " | "
                         + cell(what) + " | " + cell(examples.getOrDefault(new KindKey(kind, f.key()), "")) + " |");
            }
            return rows;
        }

        private static String labelFullName(final String key) {
            final CefKeys.KeyInfo label = CefKeys.KEYS.get(key + "Label");
            return label != null
                    ? label.fullName()
                    : key + "Label";
        }
    }
}
