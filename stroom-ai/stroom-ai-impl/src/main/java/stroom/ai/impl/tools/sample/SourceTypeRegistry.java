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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The source type registry of an aggregated feed: a Documentation doc, `<feed> source types`, with a part for people
/// (an account of the feed and a table, a row a source type) and a machine block the tools read and write
/// (`<!-- stroom-mcp source types {...} -->`), never shown as text. Its format is what is stored, so it changes only
/// in ways a registry already kept still reads.
///
/// The block is JSON: `feed`, `field` (the source type field), `field_by`, `environment` and `system_name` (the
/// feed's, the default for each row), `updated`, `scope` (the build that chose it, `source_types`, `by`, `at`), and
/// `rows` in the order they are tried, each `name`, `match` (`value` or `pattern`), `value` (the value, or the
/// pattern), `system_name`, `status` (`translated`, `parked`, `dropped` or `new`), `decided` (`by`, `at`, `reason`),
/// `group`, `pipeline` and `output_feed` (empty until a source type group handles it; `group_xslt` names the group's
/// XSLT, `uuid` and `name`, once build_translation_xslt saves it), `references` (the reference data its group looks
/// up: `feed` and `loader_pipeline`) and `profile`.
///
/// A row's `pipeline` says which pipeline's dispatcher routes it: `own` (true for a group with a pipeline of its own),
/// `dispatcher` (`uuid` and `name` of that dispatcher's XSLT), `name` (the pipeline's, once made) and `new_version`
/// (true while the feed's pipeline's new version is still to be copied). `output_feed` is the feed its Events go to:
/// absent for the raw feed's own, `""` while one of its own is still to be made. `modules` names the feed's `helpers`
/// and `common` modules (`uuid`, `name`), so a later build finds them beside the feed. Every UUID is the build's
/// document while a build changes it (a working copy), and the original's once promoted.
public final class SourceTypeRegistry {

    /// What a feed's registry is named after.
    public static final String SUFFIX = " source types";
    /// Translated by a source type group.
    public static final String TRANSLATED = "translated";
    /// Left for a later build.
    public static final String PARKED = "parked";
    /// Never to be translated, on the user's word.
    public static final String DROPPED = "dropped";
    /// Seen after the registry was made, not yet placed by the user.
    public static final String NEW = "new";
    /// The statuses a row may have.
    public static final List<String> STATUSES = List.of(TRANSLATED, PARKED, DROPPED, NEW);

    private static final String OPEN = "<!-- stroom-mcp source types ";
    private static final Pattern BLOCK = Pattern.compile("<!-- stroom-mcp source types (\\{.*?\\}) -->",
            Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SourceTypeRegistry() {
        // Static only.
    }

    /// @param feed A feed's name.
    /// @return The name of its registry.
    public static String docName(final String feed) {
        return feed + SUFFIX;
    }

    /// @param markdown A registry's text, or null.
    /// @return What its machine block holds, or null when it has none (or it isn't JSON).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(final String markdown) {
        final Matcher found = BLOCK.matcher(markdown == null
                ? ""
                : markdown);
        if (!found.find()) {
            return null;
        }
        try {
            final Object registry = ToolJson.loads(found.group(1));
            return registry instanceof Map
                    ? (Map<String, Object>) registry
                    : null;
        } catch (final ToolJson.JsonDecodeException e) {
            return null;
        }
    }

    /// @param registry A registry's machine block.
    /// @return Its rows, in the order they are tried.
    public static List<SourceTypeRows.Row> rows(final Map<String, Object> registry) {
        final List<SourceTypeRows.Row> out = new ArrayList<>();
        JsonValues.listOrEmpty(registry.get("rows")).forEach(item -> {
            final Map<String, Object> row = JsonValues.mapOrEmpty(item);
            out.add(new SourceTypeRows.Row(JsonValues.text(row.get("name")), JsonValues.text(row.get("match")),
                    JsonValues.text(row.get("value"))));
        });
        return out;
    }

    /// Writes the registry document.
    ///
    /// @param registry Its machine block.
    /// @return Its Markdown: an account of the feed, a table a row a source type, and the block.
    public static String render(final Map<String, Object> registry) {
        final String feed = JsonValues.text(registry.get("feed"));
        final List<Map<String, Object>> rows = JsonValues.maps(registry.get("rows"));
        final List<String> lines = new ArrayList<>(List.of("# " + docName(feed), "", account(registry, rows), "",
                "Rows are tried in order: the first whose match fits a record's " + code(registry.get("field"))
                + " value takes it (= a value exactly, ~ an anchored pattern on the value). Shares are estimates "
                + "from the records the survey read.", "",
                "| # | Source type | Match | System | Records | Bytes | Message | Top kinds | Signals | Suggested | "
                + "Status | Examples | Decided |",
                "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"));
        int n = 1;
        for (final Map<String, Object> row : rows) {
            final Map<String, Object> profile = JsonValues.mapOrEmpty(row.get("profile"));
            final Map<String, Object> decided = JsonValues.mapOrEmpty(row.get("decided"));
            lines.add("| " + n++ + " | " + cell(row.get("name")) + " | "
                      + cell(("pattern".equals(row.get("match"))
                    ? "~ "
                    : "= ") + JsonValues.text(row.get("value"))) + " | "
                      + cell(firstSet(row.get("system_name"), registry.get("system_name"), "-")) + " | "
                      + cell(records(profile)) + " | " + cell(percent(profile.get("bytes_share_percent"))) + " | "
                      + cell(formats(profile)) + " | " + cell(kinds(profile)) + " | " + cell(signals(profile))
                      + " | " + cell(profile.get("suggested_category") == null
                    ? "-"
                    : profile.get("suggested_category") + " (" + profile.get("confidence") + ")") + " | "
                      + cell(status(row)) + " | " + cell(examples(profile)) + " | "
                      + cell(decided.isEmpty()
                    ? "-"
                    : JsonValues.text(decided.get("by")) + ", " + JsonValues.text(decided.get("at"))
                      + (decided.get("reason") == null
                    ? ""
                    : ": " + decided.get("reason"))) + " |");
        }
        lines.addAll(List.of("", "## Registry state", "",
                "Read and written by the Stroom AI tools; do not edit.", "",
                OPEN + block(registry) + " -->", ""));
        return String.join("\n", lines);
    }

    // The account of the feed: its field, how many source types, which in what state and why.
    private static String account(final Map<String, Object> registry, final List<Map<String, Object>> rows) {
        final StringBuilder out = new StringBuilder("Feed **" + JsonValues.text(registry.get("feed"))
                                                    + "** aggregates " + rows.size() + " source type(s), told apart "
                                                    + "by the field " + code(registry.get("field")) + ".");
        out.append(" Environment: ").append(firstSet(registry.get("environment"), null, "not set"))
                .append("; System Name by default: ").append(firstSet(registry.get("system_name"), null,
                        "not set")).append('.');
        for (final String status : STATUSES) {
            final List<String> named = rows.stream()
                    .filter(row -> status.equals(row.get("status")))
                    .map(row -> JsonValues.text(row.get("name")))
                    .toList();
            if (!named.isEmpty()) {
                out.append(' ').append(Character.toUpperCase(status.charAt(0))).append(status.substring(1))
                        .append(": ").append(String.join(", ", named)).append('.');
            }
        }
        final Map<String, Object> scope = JsonValues.mapOrEmpty(registry.get("scope"));
        if (!scope.isEmpty()) {
            out.append(" Build '").append(JsonValues.text(scope.get("build"))).append("' covers ")
                    .append(String.join(", ", JsonValues.strings(scope.get("source_types"))))
                    .append(" (chosen by ").append(JsonValues.text(scope.get("by"))).append(", ")
                    .append(JsonValues.text(scope.get("at"))).append(").");
        }
        rows.stream()
                .filter(row -> PARKED.equals(row.get("status")) || DROPPED.equals(row.get("status")))
                .map(row -> JsonValues.mapOrEmpty(row.get("decided")).get("reason"))
                .filter(reason -> reason != null && !String.valueOf(reason).isBlank())
                .distinct()
                .forEach(reason -> out.append(" Why: ").append(reason).append('.'));
        return out.toString();
    }

    // A row's status, with the source type group translating it where one does, the pipeline that runs it and the
    // feed its Events go to.
    private static String status(final Map<String, Object> row) {
        final Object group = row.get("group");
        if (row.get("status") == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder(JsonValues.text(row.get("status")));
        if (group != null && !JsonValues.text(group).isBlank()) {
            out.append(" by group ").append(JsonValues.text(group));
        }
        final Map<String, Object> pipeline = JsonValues.mapOrEmpty(row.get("pipeline"));
        if (!pipeline.isEmpty()) {
            final Object name = pipeline.get("name");
            out.append(", in ").append(name == null || JsonValues.text(name).isBlank()
                    ? (JsonValues.truthy(pipeline.get("own"))
                            ? "a pipeline of its own (to be made)"
                            : "the feed's pipeline")
                    : "pipeline " + JsonValues.text(name));
        }
        final Object feed = row.get("output_feed");
        if (feed != null) {
            out.append(", to ").append(JsonValues.text(feed).isBlank()
                    ? "an output feed of its own (to be made)"
                    : "feed " + JsonValues.text(feed));
        }
        return out.toString();
    }

    private static String records(final Map<String, Object> profile) {
        if (profile.isEmpty()) {
            return "-";
        }
        final long read = profile.get("records_read") instanceof final Number n
                ? n.longValue()
                : 0;
        if (read == 0) {
            return JsonValues.text(profile.get("rests_on"));
        }
        return "about " + percent(profile.get("share_percent")) + " (" + read + " read, about "
               + profile.get("records_estimate") + " in the streams read"
               + (read < SourceTypeProfiles.FEW_RECORDS
                ? "; " + profile.get("rests_on")
                : "") + ")";
    }

    private static String percent(final Object value) {
        return value == null
                ? "-"
                : JsonValues.text(value) + "%";
    }

    private static String formats(final Map<String, Object> profile) {
        final Map<String, Object> formats = JsonValues.mapOrEmpty(profile.get("formats"));
        if (formats.isEmpty()) {
            return "-";
        }
        final List<String> said = new ArrayList<>();
        formats.forEach((format, n) -> said.add(format + " " + n));
        final Object failed = profile.get("failed_to_parse");
        return String.join(", ", said) + (failed instanceof final Number number && number.longValue() > 0
                ? "; " + number + " failed to parse"
                : "");
    }

    private static String kinds(final Map<String, Object> profile) {
        final List<String> said = new ArrayList<>();
        JsonValues.maps(profile.get("top_kinds")).forEach(kind -> said.add(JsonValues.text(kind.get("kind")) + " "
                                                                           + JsonValues.text(kind.get("records"))));
        return said.isEmpty()
                ? "-"
                : String.join(", ", said);
    }

    private static String signals(final Map<String, Object> profile) {
        final List<String> said = new ArrayList<>();
        JsonValues.maps(profile.get("signals")).forEach(signal -> said.add(JsonValues.text(signal.get("signal"))
                                                                           + " " + percent(signal.get(
                "share_percent"))));
        return said.isEmpty()
                ? "-"
                : String.join(", ", said);
    }

    private static String examples(final Map<String, Object> profile) {
        final List<String> said = new ArrayList<>();
        JsonValues.maps(profile.get("examples")).forEach(location -> said.add(JsonValues.text(location.get("stream"))
                                                                              + ":" + JsonValues.text(location.get(
                "part")) + ":" + JsonValues.text(location.get("record"))));
        return said.isEmpty()
                ? "-"
                : String.join(", ", said);
    }

    // The block's JSON on one line, a `-->` in a value written so it can't end the comment.
    private static String block(final Map<String, Object> registry) {
        return ToolJson.dumps(registry).replace("-->", "--\\u003e");
    }

    private static String code(final Object value) {
        return "`" + JsonValues.text(value).replace("`", "'") + "`";
    }

    private static String cell(final Object value) {
        final String flat = WHITESPACE.matcher(value == null
                ? "-"
                : JsonValues.text(value)).replaceAll(" ").replace("|", "\\|");
        return ToolText.len(flat) <= 160
                ? flat
                : ToolText.head(flat, 159) + "…";
    }


    // The first of the values set, as text; otherwise the text given.
    private static String firstSet(final Object value, final Object fallback, final String otherwise) {
        if (value != null && !JsonValues.text(value).isBlank()) {
            return JsonValues.text(value);
        }
        return fallback != null && !JsonValues.text(fallback).isBlank()
                ? JsonValues.text(fallback)
                : otherwise;
    }
}
