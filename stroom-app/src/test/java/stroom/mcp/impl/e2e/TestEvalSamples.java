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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEvalSamples {

    @Test
    void everyCaseFileIsTheBytesTheStroomMcpServerMakes() {
        // generated-sha256.json: each file's SHA-256, pinned: the bytes the cases' expectations were written for.
        final Map<String, String> expected = expectedHashes();
        final Map<String, String> made = new LinkedHashMap<>();
        for (final String id : EvalCases.IDS) {
            for (final EvalCases.SampleFile file : EvalCases.files(EvalCases.load(id))) {
                made.put(id + "/" + file.name(), sha256(file.data()));
            }
        }

        assertThat(made).isEqualTo(expected);
    }

    @Test
    void aCaseOfFilesShowsTheirFirstLinesAsItsSample() {
        final List<String> shown = EvalCases.samples(EvalCases.load("44_custom_tilde_delimited"));

        assertThat(shown).singleElement().satisfies(sample -> {
            assertThat(sample.lines()).hasSize(6);
            assertThat(sample).startsWith("2026-10-11 08:00:00 ~ ");
        });
        assertThat(EvalCases.samples(EvalCases.load("01_csv_vpn")))
                .containsExactly((String) EvalCases.load("01_csv_vpn").get("sample"));
    }

    @Test
    void aCaseFileMayBeASliceOfATestResource() {
        final List<EvalCases.SampleFile> files = EvalCases.files(EvalCases.load("56_jsonl_azure_audit_aggregated"));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.name()).isEqualTo("m365-audit.json");
            final String text = new String(file.data(), StandardCharsets.UTF_8);
            assertThat(text.lines()).hasSize(400);
            assertThat(text).startsWith("{\"CreationTime\": ").endsWith("}\n");
            // A record holding a line or paragraph separator is still one line.
            assertThat(text.lines().filter(line -> line.contains("\u2028"))).isNotEmpty();
        });
    }

    @Test
    void aCaseWhoseDataIsInTheFeedHasStreamsOfPartsAndShowsTheFirstParts() {
        final Map<String, Object> evalCase = EvalCases.load("59_jsonl_windows_meta_roles");

        final List<List<EvalCases.StreamPart>> streams = EvalCases.streams(evalCase);

        assertThat(streams).hasSize(2).allSatisfy(parts -> assertThat(parts).hasSize(5));
        assertThat(streams.getFirst().getFirst().meta()).containsEntry("WindowsServerInstalledServedRoles",
                "DNS Server, Active Directory Domain Services, File Server");
        assertThat(streams.stream().flatMap(List::stream).mapToLong(part -> part.text().lines().count()).sum())
                .isEqualTo(196);
        assertThat(EvalCases.files(evalCase)).isEmpty();
        assertThat(EvalCases.samples(evalCase)).singleElement().satisfies(sample -> {
            assertThat(sample).startsWith("Part headers: {").contains("WindowsServerInstalledServedRoles");
            assertThat(sample.lines().filter(line -> line.startsWith("Part headers: "))).hasSize(3);
        });
        assertThat(EvalCases.streams(EvalCases.load("01_csv_vpn"))).isEmpty();
    }

    @Test
    void aCaseThatStartsFromAnotherShowsThatCasesSamplesAndHasNoFilesOfItsOwn() {
        final Map<String, Object> evalCase = EvalCases.load("60_jsonl_azure_audit_new_group");
        final Map<String, Object> setup = EvalCases.setup(evalCase);

        assertThat(setup).containsEntry("id", "56_jsonl_azure_audit_aggregated");
        assertThat(EvalCases.files(evalCase)).isEmpty();
        assertThat(EvalCases.samples(evalCase)).isEqualTo(EvalCases.samples(setup));
        assertThat(EvalCases.setup(EvalCases.load("56_jsonl_azure_audit_aggregated"))).isNull();
        assertThatThrownBy(() -> EvalCases.setup(Map.of("id", "x", "setup", Map.of("from_case", 98))))
                .hasMessageContaining("No evaluation case 98 to start x from");
    }

    @Test
    void theCasesAreAllThere() {
        assertThat(EvalCases.IDS).hasSize(78).doesNotHaveDuplicates();
        assertThat(EvalCases.load("35_xml_fragments_secretserver_messages"))
                .containsEntry("id", "35_xml_fragments_secretserver_messages")
                .containsKeys("request", "expected", "reference", "sample");
        assertThatThrownBy(() -> EvalCases.load("99_none")).hasMessageContaining("No evaluation case 99_none");
    }

    @Test
    void theDocumentedCodesCaseDocumentsTheGeneratorsCodesAndSamplesSomeOfThem() {
        final Map<String, Object> evalCase = EvalCases.load("68_csv_documented_codes_many");
        final List<List<String>> codes = EvalSamples.documentedCodes();
        final String docs = (String) evalCase.get("source_docs");

        assertThat(codes).hasSize(300);
        assertThat(docs.lines().filter(line -> line.startsWith("| EV"))).containsExactlyElementsOf(codes.stream()
                .map(code -> "| " + code.getFirst() + " | " + code.get(2) + " |")
                .toList());
        final String text = new String(EvalCases.files(evalCase).getFirst().data(), StandardCharsets.UTF_8);
        assertThat(text.lines().skip(1).map(line -> line.split(",")[2]).distinct()).hasSize(120);
        assertThat(text.lines()).hasSize(361);
    }

    @Test
    void aLaterSampleIsTheCasesOwnAndItsSamplesAreItsSetups() {
        final Map<String, Object> evalCase = EvalCases.load("70_jsonl_uncatalogued_records_proposals");

        assertThat(EvalCases.later(evalCase)).singleElement()
                .satisfies(later -> assertThat(later).contains("\"evt\": \"mfa.verify\""));
        assertThat(EvalCases.samples(evalCase)).isEqualTo(EvalCases.samples(EvalCases.setup(evalCase)));
        assertThat(EvalCases.later(EvalCases.load("30_jsonl_two_shapes_idp"))).isEmpty();
    }

    @Test
    void theSeededRandomDrawsThePinnedSequence() {
        // random.Random(1): [r.randint(0, 9) for _ in range(5)], r.random(), r.choice('abcdef'), and
        // r.sample(range(4), 3), as CPython 3.12 gives them.
        final SeededRandom rng = new SeededRandom(1);
        assertThat(List.of(rng.randint(0, 9), rng.randint(0, 9), rng.randint(0, 9), rng.randint(0, 9),
                rng.randint(0, 9))).containsExactly(2, 9, 1, 4, 1);
        assertThat(rng.random()).isEqualTo(0.49543508709194095);
        assertThat(rng.choice(List.of("a", "b", "c", "d", "e", "f"))).isEqualTo("d");
        assertThat(rng.sample(List.of(0, 1, 2, 3), 3)).containsExactly(3, 2, 1);
        assertThatThrownBy(() -> rng.sample(List.of(1, 2, 3, 4, 5, 6, 7), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonIsWrittenInTheStoredForm() {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", List.of(1, 2.5, 1791676803.7, 1791676800.0));
        value.put("b", "say \"hi\"\né\u001b");
        value.put("c", Map.of());
        value.put("d", List.of());
        value.put("e", null);
        value.put("f", true);

        assertThat(EvalSamples.dumps(value, 0)).isEqualTo("{\"a\": [1, 2.5, 1791676803.7, 1791676800.0], "
                                                         + "\"b\": \"say \\\"hi\\\"\\n\\u00e9\\u001b\", \"c\": {}, "
                                                         + "\"d\": [], \"e\": null, \"f\": true}");
        assertThat(EvalSamples.dumps(Map.of("k", List.of(1, Map.of("x", "y"))), 2))
                .isEqualTo("{\n  \"k\": [\n    1,\n    {\n      \"x\": \"y\"\n    }\n  ]\n}");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> expectedHashes() {
        try (final InputStream in = TestEvalSamples.class.getResourceAsStream("eval/generated-sha256.json")) {
            return JsonUtil.readValue(new String(in.readAllBytes()), Map.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(final byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
