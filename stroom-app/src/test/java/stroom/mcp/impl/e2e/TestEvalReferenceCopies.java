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

import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The copies of the evaluation cases' reference mappings that other modules' tests read, kept the cases' own:
///
/// - the generator's golden files of the cases (`golden/generation/0NN-eval-every-case-...json` in
///   `stroom-ai-impl`), each a case's mapping as the generator dumps it (compact);
/// - `offline_cases.json` in `stroom-mcp-impl` (the checks build_translation_xslt makes before it saves, for the
///   cases whose samples are under 60 KB), each case's mapping as its YAML holds it and its sample texts (the
///   splitter a case infers is kept as recorded);
/// - `eval_cases.json` beside the e2e tests, the cases the survey's location test reads: their sample and reference.
///
/// A copy that differs fails the test. Run with `-DtestUpdateGoldenFiles=true` to make the copies the cases' again,
/// then regenerate what is derived from them with the same property: `TestMappingSampleCheck` (the offline cases'
/// problems and warnings) in `stroom-mcp-impl`, and `TestXsltGeneratorGolden` then `TestXsltReaderCases` (twice, as
/// the reader reads the golden XSLTs) in `stroom-ai-impl`; and review the change with `git diff`.
@SuppressWarnings("unchecked")
class TestEvalReferenceCopies {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEvalReferenceCopies.class);

    private static final String UPDATE_PROPERTY = "testUpdateGoldenFiles";
    private static final String GOLDEN_DIR = "stroom-ai/stroom-ai-impl/src/test/resources/stroom/ai/impl/golden/"
                                             + "generation/";
    private static final String GOLDEN_NAME = "-eval-every-case-has-a-reference-mapping-the-schema-accepts.json";
    private static final String OFFLINE_CASES = "stroom-ai/stroom-mcp-impl/src/test/resources/stroom/mcp/impl/tools/"
                                                + "offline_cases.json";
    private static final String EVAL_CASES = "stroom-app/src/test/resources/stroom/mcp/impl/e2e/eval_cases.json";
    // The cases with a golden file, in the files' order from 030: each case whose mapping is another's (37 is 22's,
    // 38 is 02's, 36 is 35's) has none of its own.
    private static final List<String> GOLDEN_CASES = List.of("01_csv_vpn", "02_json_alert",
            "03_json_nested_process", "04_json_embedded", "05_xml_ns_files", "06_syslog5424_ssh",
            "07_syslog3164_sudo", "08_kv_firewall", "09_csv_noheader_copy", "10_xml_attrs_lock",
            "11_jsonl_message_text", "12_xml_fragments_events", "13_json_embedded_message",
            "14_csv_two_files_variants", "15_csv_lookup_reference_data", "16_json_batches_items",
            "17_csv_firewall_mixed", "18_json_event_string_freetext", "19_jsonl_message_layouts",
            "20_csv_coded_with_docs", "21_csv_connections_odd_fields", "22_syslog_cef_gateway",
            "23_pipe_quoted_helpdesk", "24_csv_noheader_quoted_print", "25_syslog_kv_nil_proxy",
            "26_xml_fragments_windows_ns", "27_endash_auth_messages", "28_xml_records_badges",
            "29_csv_large_files_proxy", "30_jsonl_two_shapes_idp", "31_kv_awkward_times_fortigate",
            "32_xml_fragments_windows_many", "33_csv_cp1252_hr", "34_jsonl_schema_traps_nac",
            "35_xml_fragments_secretserver_messages", "39_w3c_bluecoat_proxy", "40_json_cloudtrail_records",
            "41_java_multiline_app", "42_csv_quoted_newlines_tickets", "43_syslog_csv_palo_alto_traffic",
            "44_custom_tilde_delimited", "45_jsonl_gelf", "46_jsonl_ecs_input", "47_kv_fortios_native",
            "48_jsonl_damaged_lines", "49_jsonl_soap_payload", "50_kv_mail_repeated_keys", "51_kv_unicode_ansi",
            "52_postgres_multiline_sql", "53_cri_container_partial_lines");
    // TODO phase 1 (lane A): the cases whose reference mappings use what the generator reads only once the event
    //  type catalogue's conditions land (`not`, the `catch_all` flag): their copies are made the cases' then, and this
    //  set removed.
    private static final Set<String> AWAITING_GENERATOR = Set.of("19_jsonl_message_layouts",
            "35_xml_fragments_secretserver_messages", "36_xml_fragments_secretserver_whole_export",
            "58_jsonl_pega_wrapper");

    @Test
    void theGoldenFilesOfTheCasesHoldTheirMappings() {
        final List<String> differ = new ArrayList<>();
        for (int i = 0; i < GOLDEN_CASES.size(); i++) {
            final String id = GOLDEN_CASES.get(i);
            if (AWAITING_GENERATOR.contains(id)) {
                continue;
            }
            final Path file = root().resolve(GOLDEN_DIR + String.format("%03d", 30 + i) + GOLDEN_NAME);
            final Map<String, Object> golden = (Map<String, Object>) ToolJson.loads(read(file));
            final Map<String, Object> mapping = TranslationMapping.fromJson(mapping(id)).toJson(DumpMode.COMPACT);
            if (!ToolJson.dumps(mapping).equals(ToolJson.dumps(golden.get("mapping")))) {
                differ.add(id);
                if (updating()) {
                    golden.put("mapping", mapping);
                    write(file, ToolJson.dumps(golden, 1) + "\n");
                }
            }
        }
        assertThat(differ).as(howToAccept()).satisfiesAnyOf(list -> assertThat(list).isEmpty(),
                list -> assertThat(updating()).isTrue());
    }

    @Test
    void theOfflineCasesHoldTheirMappings() {
        final Path file = root().resolve(OFFLINE_CASES);
        final List<Map<String, Object>> cases = (List<Map<String, Object>>) ToolJson.loads(read(file));
        final List<String> differ = new ArrayList<>();
        for (final Map<String, Object> offline : cases) {
            final String id = (String) offline.get("id");
            if (AWAITING_GENERATOR.contains(id)) {
                continue;
            }
            final Map<String, Object> mapping = mapping(id);
            final List<String> texts = texts(EvalCases.load(id));
            if (!ToolJson.dumps(mapping).equals(ToolJson.dumps(offline.get("mapping")))
                || !texts.equals(offline.get("texts"))) {
                differ.add(id);
                offline.put("mapping", mapping);
                offline.put("texts", texts);
                offline.put("size", texts.stream().mapToInt(text -> text.codePointCount(0, text.length())).sum());
            }
        }
        if (updating() && !differ.isEmpty()) {
            write(file, ToolJson.dumps(cases, 0, false) + "\n");
        }
        assertThat(differ).as(howToAccept()).satisfiesAnyOf(list -> assertThat(list).isEmpty(),
                list -> assertThat(updating()).isTrue());
    }

    @Test
    void theSurveyLocationCasesAreTheCases() {
        final Path file = root().resolve(EVAL_CASES);
        final Map<String, Object> cases = (Map<String, Object>) ToolJson.loads(read(file));
        final List<String> differ = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : cases.entrySet()) {
            final Map<String, Object> evalCase = EvalCases.load(entry.getKey());
            final Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("sample", evalCase.get("sample"));
            copy.put("reference", evalCase.get("reference"));
            if (!ToolJson.dumps(copy).equals(ToolJson.dumps(entry.getValue()))) {
                differ.add(entry.getKey());
                entry.setValue(copy);
            }
        }
        if (updating() && !differ.isEmpty()) {
            write(file, ToolJson.dumps(cases, 1));
        }
        assertThat(differ).as(howToAccept()).satisfiesAnyOf(list -> assertThat(list).isEmpty(),
                list -> assertThat(updating()).isTrue());
    }

    // The case's sample texts: its samples, or its files' text, whole.
    private static List<String> texts(final Map<String, Object> evalCase) {
        final List<EvalCases.SampleFile> files = EvalCases.files(evalCase);
        return files.isEmpty()
                ? EvalCases.samples(evalCase)
                : files.stream().map(file -> new String(file.data(), file.charset())).toList();
    }

    // The case's reference mapping, as its YAML reads (JSON as the copies hold it).
    private static Map<String, Object> mapping(final String id) {
        final Map<String, Object> reference = (Map<String, Object>) EvalCases.load(id).get("reference");
        return (Map<String, Object>) ToolJson.loads(ToolJson.dumps(reference.get("mapping")));
    }

    private static boolean updating() {
        return Boolean.getBoolean(UPDATE_PROPERTY);
    }

    private static String howToAccept() {
        return "A copy differs from its case's reference: run with -D" + UPDATE_PROPERTY + "=true to make it the "
               + "case's, regenerate what is derived from it, and review it with git diff";
    }

    // The repository's root: above the module's folder when Gradle runs the tests, or the working folder.
    private static Path root() {
        final Path workingDir = Path.of("").toAbsolutePath();
        return Files.isDirectory(workingDir.resolve("stroom-ai"))
                ? workingDir
                : workingDir.getParent();
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(final Path file, final String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
            LOGGER.info("Updated {}", file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
