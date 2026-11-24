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

package stroom.mcp.impl.e2e;

import stroom.util.json.JsonUtil;
import stroom.util.yaml.YamlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The evaluation set (the `eval` resources, a YAML file a case): each case's sample, request, reference solution
/// and what its Events must hold.
///
/// One value may read differently from one YAML reader to another: case 48's drop rule `{..., reason: damaged lines
/// (not JSON), shown to the user}`, whose reason holds a comma. Only the reason's text can differ, which no check
/// reads.
@SuppressWarnings("unchecked")
final class EvalCases {

    /// The cases, by id (the file's name without `.yaml`), in order.
    static final List<String> IDS = List.of("01_csv_vpn", "02_json_alert", "03_json_nested_process",
            "04_json_embedded", "05_xml_ns_files", "06_syslog5424_ssh", "07_syslog3164_sudo", "08_kv_firewall",
            "09_csv_noheader_copy", "10_xml_attrs_lock", "11_jsonl_message_text", "12_xml_fragments_events",
            "13_json_embedded_message", "14_csv_two_files_variants", "15_csv_lookup_reference_data",
            "16_json_batches_items", "17_csv_firewall_mixed", "18_json_event_string_freetext",
            "19_jsonl_message_layouts", "20_csv_coded_with_docs", "21_csv_connections_odd_fields",
            "22_syslog_cef_gateway", "23_pipe_quoted_helpdesk", "24_csv_noheader_quoted_print",
            "25_syslog_kv_nil_proxy", "26_xml_fragments_windows_ns", "27_endash_auth_messages",
            "28_xml_records_badges", "29_csv_large_files_proxy", "30_jsonl_two_shapes_idp",
            "31_kv_awkward_times_fortigate", "32_xml_fragments_windows_many", "33_csv_cp1252_hr",
            "34_jsonl_schema_traps_nac", "35_xml_fragments_secretserver_messages",
            "36_xml_fragments_secretserver_whole_export", "37_cef_gateway_whole_day",
            "38_json_array_one_line_alerts", "39_w3c_bluecoat_proxy", "40_json_cloudtrail_records",
            "41_java_multiline_app", "42_csv_quoted_newlines_tickets", "43_syslog_csv_palo_alto_traffic",
            "44_custom_tilde_delimited", "45_jsonl_gelf", "46_jsonl_ecs_input", "47_kv_fortios_native",
            "48_jsonl_damaged_lines", "49_jsonl_soap_payload", "50_kv_mail_repeated_keys", "51_kv_unicode_ansi",
            "52_postgres_multiline_sql", "53_cri_container_partial_lines", "54_jsonl_session_times",
            "55_kv_two_event_times", "56_jsonl_azure_audit_aggregated", "57_jsonl_collector_paths",
            "58_jsonl_pega_wrapper", "59_jsonl_windows_meta_roles", "60_jsonl_azure_audit_new_group",
            "61_jsonl_azure_audit_join_group", "62_esql_top_uploaders_by_domain", "63_esql_daily_uploads_by_domain",
            "64_esql_unsanctioned_uploads_mb", "65_jsonl_held_loggers_many", "66_jsonl_pega_loggers_event_types",
            "67_jsonl_flat_and_nested_keys", "68_csv_documented_codes_many", "69_json_array_membership_alerts",
            "70_jsonl_uncatalogued_records_proposals", "71_stroomql_join_exports_by_department",
            "72_stroomql_leavers_still_logging_on", "73_sequence_logon_then_export_lucene",
            "74_sequence_logon_then_export_elastic", "75_stroomql_join_exports_past_the_row_limit",
            "76_jsonl_entra_audit_changes", "77_timeline_user_week_activity", "78_timeline_proxy_only_user",
            "79_insider_leaver_week_timeline", "80_insider_working_on_a_day", "81_insider_vpn_session_at_night",
            "82_insider_archive_then_personal_cloud_upload", "83_compare_leaver_before_leaving",
            "84_compare_viewer_against_team", "85_compare_compromised_in_april", "86_compare_decoys_not_flagged",
            "87_compare_done_to_leaver_before_leaving", "88_skill_save_from_investigation",
            "89_skill_run_among_distractors", "90_skill_broken_reported_before_running",
            "91_skill_timeline_from_template", "92_skill_add_vpn_source", "93_skill_default_fourteen_days",
            "94_skill_timeline_learns_pair_editor", "95_skill_timeline_learns_pair_viewer",
            "96_skill_undo_last_change", "97_json_hand_written_add_source_ip", "98_agents_fields_from_description",
            "99_agents_fields_under_wrong_heading", "100_agents_change_user_field",
            "101_agents_fields_disagree_with_plan");

    // The types of a question's columns ([#table]).
    private static final List<String> QUESTION_TYPES = List.of("text", "long", "date");
    // What a case whose data is in the feed already shows of it: the first stream's first parts, each part's first
    // lines.
    private static final int SHOWN_PARTS = 3;
    private static final int SHOWN_PART_LINES = 3;
    // The Secret Server export is made of case 35's records.
    private static final String SECRET_SERVER_KINDS = "35_xml_fragments_secretserver_messages";

    private EvalCases() {
        // Static only.
    }

    /// @param id The case's id, e.g. `01_csv_vpn`.
    /// @return The case, as its YAML reads.
    static Map<String, Object> load(final String id) {
        try (final InputStream in = EvalCases.class.getResourceAsStream("eval/" + id + ".yaml")) {
            if (in == null) {
                throw new IllegalStateException("No evaluation case " + id);
            }
            final Map<String, Object> evalCase = new LinkedHashMap<>(YamlUtil.getVanillaMapper()
                    .readValue(in, Map.class));
            evalCase.put("id", id);
            return evalCase;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Whether a case is a question about data already in Stroom (`kind: question`): answered, not built, with its
    /// data put in Stroom before the user asks (`elastic_index`, `lucene_index`, `data_connections`: [#table]), and
    /// scored on the tools used and the answer ([EvalScore#scoreQuestion]).
    ///
    /// @param evalCase The case.
    /// @return True for a question.
    static boolean isQuestion(final Map<String, Object> evalCase) {
        return "question".equals(evalCase.get("kind"));
    }

    /// Whether a case is a chat about skills (`kind: skill`, or standing instructions, `kind: instructions`): run in
    /// Stroom's own chat ([stroom.ai.impl.EvalChat]), where `propose_skill` (or `propose_instructions`) offers its
    /// 'Review and apply' button, after its setup ([AbstractEvalTest#setUpSkill]): a question's data, the skills docs
    /// (or AGENTS docs and index plans), the user who chats and the chat's earlier turns. Scored on the calls made,
    /// the proposals and what the agent told the user ([EvalScore#scoreSkill]).
    ///
    /// @param evalCase The case.
    /// @return True for a skill case.
    static boolean isSkill(final Map<String, Object> evalCase) {
        return "skill".equals(evalCase.get("kind")) || isInstructions(evalCase);
    }

    /// Whether a case is a chat about standing instructions (`kind: instructions`): run as a skill case is
    /// ([#isSkill], so it counts as one), its proposals the AGENTS docs `propose_instructions` offers, scored by
    /// what the tools would read of them and what `check_instructions` finds ([EvalScore#scoreSkill]).
    ///
    /// @param evalCase The case.
    /// @return True if it is.
    static boolean isInstructions(final Map<String, Object> evalCase) {
        return "instructions".equals(evalCase.get("kind"));
    }

    /// Whether a case's reference drafts a translation from its sample: every case but a question, a skill case and
    /// a hand-written one (which reads the translation back from the pipeline written by hand).
    ///
    /// @param evalCase The case.
    /// @return True for a case whose reference drafts its translation.
    static boolean isDrafted(final Map<String, Object> evalCase) {
        return !isQuestion(evalCase) && !isSkill(evalCase) && handWritten(evalCase) == null;
    }

    /// A hand-written case's setup (`setup: {hand_written: ...}`): a feed whose translation was written by hand in
    /// Stroom and never by the tools ([AbstractEvalTest#setUpHandWritten]), for a build that starts from it.
    ///
    /// @param evalCase The case.
    /// @return The setup's spec, or null for a case that has none.
    static Map<String, Object> handWritten(final Map<String, Object> evalCase) {
        return map(evalCase.get("setup")).get("hand_written") instanceof final Map<?, ?> spec
                ? (Map<String, Object>) spec
                : null;
    }

    /// The Lucene indexes of a question's or skill case's data (`lucene_index`: one, or a list of them).
    ///
    /// @param evalCase The case.
    /// @return Each index's spec, none if it has none.
    static List<Map<String, Object>> luceneIndexes(final Map<String, Object> evalCase) {
        final Object given = evalCase.get("lucene_index");
        return given instanceof final Map<?, ?> one
                ? List.of((Map<String, Object>) one)
                : maps(given);
    }

    /// @param value A list of strings in a case, or null.
    /// @return Its items as text, none for null.
    static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    /// @param value A list of maps in a case, or null.
    /// @return Its maps, none for null.
    static List<Map<String, Object>> maps(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    /// @param value A map in a case, or null.
    /// @return It, empty for null.
    static Map<String, Object> map(final Object value) {
        return value instanceof final Map<?, ?> given
                ? (Map<String, Object>) given
                : Map.of();
    }

    /// The case a case starts from (`setup: {from_case: 56}`): built with its reference solution and promoted before
    /// the case's own build starts, so the case finds that build's feed, data and documents in place.
    ///
    /// @param evalCase The case.
    /// @return The case it starts from, or null for a case that starts from an empty Stroom.
    static Map<String, Object> setup(final Map<String, Object> evalCase) {
        if (!(evalCase.get("setup") instanceof final Map<?, ?> setup) || setup.get("from_case") == null) {
            return null;
        }
        final String number = String.format("%02d", ((Number) setup.get("from_case")).intValue());
        return load(IDS.stream()
                .filter(id -> id.startsWith(number + "_"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No evaluation case " + number + " to start "
                                                             + evalCase.get("id") + " from")));
    }

    /// A case's later data (`later_sample`): records that reach the feed after the case it starts from was built
    /// and promoted ([#setup]), stored in the feed as a stream received then, and processed by the case's own build
    /// alone, e.g. records of a shape the promoted build doesn't recognise.
    ///
    /// @param evalCase The case.
    /// @return Its later sample texts, none if it has none.
    static List<String> later(final Map<String, Object> evalCase) {
        return evalCase.get("later_sample") instanceof final String later && !later.isEmpty()
                ? List.of(later)
                : List.of();
    }

    /// The case's sample texts (`samples`, else its one `sample`), or for a case whose samples are files on the user's
    /// disk, what the user shows of each: its first lines; or for a case whose data is in the feed already
    /// (`streams`), its first stream's first parts: each part's receipt headers and first lines; or for a case with
    /// none of these that starts from another ([#setup]), that case's.
    ///
    /// @param evalCase The case.
    /// @return Its samples.
    static List<String> samples(final Map<String, Object> evalCase) {
        if (evalCase.get("samples") instanceof final List<?> samples && !samples.isEmpty()) {
            return (List<String>) samples;
        }
        if (evalCase.get("sample") instanceof final String sample && !sample.isEmpty()) {
            return List.of(sample);
        }
        final List<String> shown = new ArrayList<>();
        for (final SampleFile file : files(evalCase)) {
            shown.add(String.join("\n", new String(file.data(), file.charset()).lines().limit(6).toList()) + "\n");
        }
        final List<List<StreamPart>> streams = streams(evalCase);
        if (shown.isEmpty() && !streams.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final StreamPart part : streams.getFirst().subList(0, Math.min(SHOWN_PARTS,
                    streams.getFirst().size()))) {
                sb.append("Part headers: ").append(part.meta()).append('\n');
                part.text().lines().limit(SHOWN_PART_LINES).forEach(line -> sb.append(line).append('\n'));
            }
            shown.add(sb.toString());
        }
        final Map<String, Object> setup = setup(evalCase);
        if (shown.isEmpty() && setup != null) {
            return samples(setup);
        }
        return shown;
    }

    /// A case's sample files on the user's disk, as bytes: each `text` in its `encoding` (UTF-8 unless given), made
    /// by its generator ([EvalSamples]), or a slice of a test resource next to the e2e tests (`resource`, from line
    /// `from`, 0 unless given, `lines` lines, all unless given), e.g. one of the aggregated feeds' samples.
    ///
    /// @param evalCase The case.
    /// @return Its files, none if its samples are texts.
    static List<SampleFile> files(final Map<String, Object> evalCase) {
        final List<SampleFile> files = new ArrayList<>();
        for (final Map<String, Object> file : (List<Map<String, Object>>) evalCase.getOrDefault("files",
                List.of())) {
            final String name = (String) file.get("name");
            if (file.get("resource") instanceof final String resource) {
                final String text = slice(resource, ((Number) file.getOrDefault("from", 0)).intValue(),
                        file.get("lines") instanceof final Number lines
                                ? lines.intValue()
                                : Integer.MAX_VALUE);
                files.add(new SampleFile(name, text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            } else if (file.get("generate") instanceof final Map<?, ?> spec) {
                final String text = EvalSamples.generate((Map<String, Object>) spec,
                        (String) load(SECRET_SERVER_KINDS).get("sample"));
                files.add(new SampleFile(name, text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            } else {
                final Charset charset = file.get("encoding") instanceof final String encoding
                        ? Charset.forName(encoding)
                        : StandardCharsets.UTF_8;
                files.add(new SampleFile(name, ((String) file.get("text")).getBytes(charset), charset));
            }
        }
        return files;
    }

    /// A case's data already in the feed when the user asks (`streams`: a test resource next to the e2e tests, as
    /// `aggregated/windows-meta/windows-meta.json` lays it out: `streams`, each a list of parts, each part its
    /// receipt headers, `meta`, and its records, `lines`), as a proxy aggregates them: streams of several parts, each
    /// part with the headers it was received with. The cases' tests store them in the case's feed before the build
    /// starts, each part's `Feed` header set to the feed's name.
    ///
    /// @param evalCase The case.
    /// @return Its streams, each a list of parts; none if it has no `streams`.
    static List<List<StreamPart>> streams(final Map<String, Object> evalCase) {
        if (!(evalCase.get("streams") instanceof final Map<?, ?> spec)) {
            return List.of();
        }
        final Map<String, Object> laidOut = JsonUtil.readValue(slice((String) spec.get("resource"), 0,
                Integer.MAX_VALUE), Map.class);
        final List<List<StreamPart>> streams = new ArrayList<>();
        for (final List<Map<String, Object>> parts : (List<List<Map<String, Object>>>) laidOut.get("streams")) {
            final List<StreamPart> stream = new ArrayList<>();
            for (final Map<String, Object> part : parts) {
                final Map<String, String> meta = new LinkedHashMap<>();
                ((Map<String, Object>) part.get("meta")).forEach((key, value) -> meta.put(key,
                        String.valueOf(value)));
                stream.add(new StreamPart(meta, String.join("\n", (List<String>) part.get("lines")) + "\n"));
            }
            streams.add(stream);
        }
        return streams;
    }

    /// The records of a question's data (`events` of its `lucene_index` or `elastic_index`, `csv` of a
    /// `data_connections` entry): a CSV test resource next to the e2e tests, its first line the column names, with
    /// no quoted values.
    ///
    /// @param resource The resource, e.g. `questions/staff-activity.csv`.
    /// @return Each record's values, by column, in the order of the columns.
    static List<Map<String, String>> records(final String resource) {
        final List<String> lines = slice(resource, 0, Integer.MAX_VALUE).lines()
                .filter(line -> !line.isBlank())
                .toList();
        final List<String> columns = List.of(lines.getFirst().split(",", -1));
        final List<Map<String, String>> records = new ArrayList<>();
        for (final String line : lines.subList(1, lines.size())) {
            final String[] values = line.split(",", -1);
            if (values.length != columns.size()) {
                throw new IllegalStateException(resource + ": " + values.length + " values where there are "
                                                + columns.size() + " columns: " + line);
            }
            final Map<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                record.put(columns.get(i), values[i]);
            }
            records.add(record);
        }
        return records;
    }

    /// The question's data that isn't the proxy uploads ([EvalProxyUploads]): a table of records the case gives
    /// (`events` of its `lucene_index` or `elastic_index`, `csv` of a `data_connections` entry), with each column's
    /// type (`fields`, or a data connection's `columns`): `text`, `long` or `date` (ISO 8601). Records too many to
    /// keep in a resource are made by a generator instead (`generate`, [EvalQuestionRecords#generate]). With `recent:
    /// true` the records are moved by whole days so that the latest falls on the day before today (UTC), their times
    /// of day kept, for a case whose questions run back from now (a skill's "the last 7 days").
    ///
    /// @param spec The `lucene_index`, `elastic_index` or `data_connections` entry.
    /// @return Its records, or null for an `elastic_index` of the proxy uploads, which has no `events`.
    static QuestionTable table(final Map<String, Object> spec) {
        final Object resource = spec.containsKey("events")
                ? spec.get("events")
                : spec.get("csv");
        final Object generate = spec.get("generate");
        if (resource == null && generate == null) {
            return null;
        }
        final Map<String, String> types = new LinkedHashMap<>();
        ((Map<String, Object>) spec.getOrDefault("fields", spec.getOrDefault("columns", Map.of())))
                .forEach((column, type) -> types.put(column, String.valueOf(type)));
        final List<Map<String, String>> read = resource != null
                ? records(String.valueOf(resource))
                : EvalQuestionRecords.generate((Map<String, Object>) generate);
        final List<Map<String, String>> records = Boolean.TRUE.equals(spec.get("recent"))
                ? recent(read, types, LocalDate.now(ZoneOffset.UTC).minusDays(1))
                : read;
        for (final String column : records.isEmpty()
                ? List.<String>of()
                : records.getFirst().keySet()) {
            final String type = types.get(column);
            if (type == null || !QUESTION_TYPES.contains(type)) {
                throw new IllegalStateException(Objects.requireNonNullElse(resource, generate) + ": column " + column
                                                + " has type " + type + ", not one of " + QUESTION_TYPES);
            }
        }
        return new QuestionTable(String.valueOf(spec.get("name")), types, records);
    }

    /// Records moved by whole days so that the latest of their dates (the `date` columns') falls on a day, each
    /// value's time of day kept.
    ///
    /// @param records The records.
    /// @param types   Each column's type.
    /// @param day     The day the latest is to fall on.
    /// @return The records, moved.
    static List<Map<String, String>> recent(final List<Map<String, String>> records,
                                            final Map<String, String> types,
                                            final LocalDate day) {
        final List<String> dates = types.entrySet().stream()
                .filter(entry -> "date".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        final LocalDate latest = records.stream()
                .flatMap(record -> dates.stream().map(record::get))
                .filter(value -> value != null && !value.isEmpty())
                .map(value -> Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(day);
        final long days = ChronoUnit.DAYS.between(latest, day);
        final List<Map<String, String>> moved = new ArrayList<>();
        for (final Map<String, String> record : records) {
            final Map<String, String> copy = new LinkedHashMap<>(record);
            for (final String column : dates) {
                final String value = copy.get(column);
                if (value != null && !value.isEmpty()) {
                    copy.put(column, DateTimeFormatter.ISO_INSTANT.format(Instant.parse(value)
                            .plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)).replaceFirst(
                            "(T\\d{2}:\\d{2}:\\d{2})Z$", "$1.000Z"));
                }
            }
            moved.add(copy);
        }
        return moved;
    }

    // So many lines of a test resource next to the e2e tests, from a line, each ending in a new line.
    private static String slice(final String resource, final int from, final int lines) {
        try (final InputStream in = EvalCases.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No test resource " + resource);
            }
            final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return String.join("\n", text.lines().skip(from).limit(lines).toList()) + "\n";
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// A sample file on the user's disk.
    ///
    /// @param name    Its name.
    /// @param data    Its bytes.
    /// @param charset Its encoding.
    record SampleFile(String name, byte[] data, Charset charset) {

    }

    /// A question's table of records ([#table]).
    ///
    /// @param name    The data source's name: its index's or data connection's.
    /// @param types   Each column's type: `text`, `long` or `date`.
    /// @param records Each record's values, by column.
    record QuestionTable(String name, Map<String, String> types, List<Map<String, String>> records) {

        /// @return The first `date` column, the data source's time field; null if there is none.
        String timeField() {
            return types.entrySet().stream()
                    .filter(entry -> "date".equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }

    /// A part of a stream in the feed.
    ///
    /// @param meta The receipt headers it came with.
    /// @param text Its records.
    record StreamPart(Map<String, String> meta, String text) {

    }
}
