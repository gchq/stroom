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
        // generated-sha256.json: each file's SHA-256 as the Stroom MCP server's dev/eval (0.18.43) makes it.
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
    void theCasesAreAllThere() {
        assertThat(EvalCases.IDS).hasSize(53).doesNotHaveDuplicates();
        assertThat(EvalCases.load("35_xml_fragments_secretserver_messages"))
                .containsEntry("id", "35_xml_fragments_secretserver_messages")
                .containsKeys("request", "expected", "reference", "sample");
        assertThatThrownBy(() -> EvalCases.load("99_none")).hasMessageContaining("No evaluation case 99_none");
    }

    @Test
    void pythonsRandomIsDrawnFromAsCPythonDraws() {
        // random.Random(1): [r.randint(0, 9) for _ in range(5)], r.random(), r.choice('abcdef'), and
        // r.sample(range(4), 3), as CPython 3.12 gives them.
        final PyRandom rng = new PyRandom(1);
        assertThat(List.of(rng.randint(0, 9), rng.randint(0, 9), rng.randint(0, 9), rng.randint(0, 9),
                rng.randint(0, 9))).containsExactly(2, 9, 1, 4, 1);
        assertThat(rng.random()).isEqualTo(0.49543508709194095);
        assertThat(rng.choice(List.of("a", "b", "c", "d", "e", "f"))).isEqualTo("d");
        assertThat(rng.sample(List.of(0, 1, 2, 3), 3)).containsExactly(3, 2, 1);
        assertThatThrownBy(() -> rng.sample(List.of(1, 2, 3, 4, 5, 6, 7), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonIsWrittenAsPythonsJsonDumpsWritesIt() {
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
