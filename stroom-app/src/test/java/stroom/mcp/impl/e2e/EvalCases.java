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

import stroom.util.yaml.YamlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            "52_postgres_multiline_sql", "53_cri_container_partial_lines");

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

    /// The case's sample texts (`samples`, else its one `sample`), or for a case whose samples are files on the user's
    /// disk, what the user shows of each: its first lines.
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
        return shown;
    }

    /// A case's sample files on the user's disk, as bytes: each `text` in its `encoding` (UTF-8 unless given), or made
    /// by its generator ([EvalSamples]).
    ///
    /// @param evalCase The case.
    /// @return Its files, none if its samples are texts.
    static List<SampleFile> files(final Map<String, Object> evalCase) {
        final List<SampleFile> files = new ArrayList<>();
        for (final Map<String, Object> file : (List<Map<String, Object>>) evalCase.getOrDefault("files",
                List.of())) {
            final String name = (String) file.get("name");
            if (file.get("generate") instanceof final Map<?, ?> spec) {
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

    /// A sample file on the user's disk.
    ///
    /// @param name    Its name.
    /// @param data    Its bytes.
    /// @param charset Its encoding.
    record SampleFile(String name, byte[] data, Charset charset) {

    }
}
