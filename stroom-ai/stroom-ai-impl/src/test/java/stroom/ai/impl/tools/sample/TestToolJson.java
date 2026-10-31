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

import stroom.ai.impl.tools.ToolText;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolJson {

    @Test
    void valuesAreReadWithLargeIntegersAndNaN() {
        assertThat(ToolJson.loads(" {\"a\": [1, 2.5, true, null, \"x\"], \"b\": {}} "))
                .isEqualTo(SampleProfiler.map("a", Arrays.asList(1L, 2.5, true, null, "x"), "b", Map.of()));
        assertThat(ToolJson.loads("123456789012345678901234567890")).isEqualTo(
                new BigInteger("123456789012345678901234567890"));
        assertThat((Double) ToolJson.loads("NaN")).isNaN();
        assertThat(ToolJson.loads("\"\\ud83d\\udc40 caf\\u00e9\"")).isEqualTo("👀 café");
    }

    @Test
    void textThatIsNotJsonSaysWhereItFails() {
        assertThatThrownBy(() -> ToolJson.loads("{\"tz\": \"\\+1000\"}"))
                .isInstanceOf(ToolJson.JsonDecodeException.class)
                .hasMessage("Invalid \\escape: line 1 column 9 (char 8)");
        assertThatThrownBy(() -> ToolJson.loads("")).hasMessage("Expecting value: line 1 column 1 (char 0)");
        assertThatThrownBy(() -> ToolJson.loads("{\"a\": 1} x")).hasMessage("Extra data: line 1 column 10 (char 9)");
        assertThatThrownBy(() -> ToolJson.loads("{\"a\" 1}")).hasMessageStartingWith("Expecting ':' delimiter");
        assertThatThrownBy(() -> ToolJson.loads("[1 2]")).hasMessageStartingWith("Expecting ',' delimiter");
        assertThatThrownBy(() -> ToolJson.loads("{'a': 1}"))
                .hasMessageStartingWith("Expecting property name enclosed in double quotes");
        assertThatThrownBy(() -> ToolJson.loads("\"abc")).hasMessageStartingWith("Unterminated string starting at");
        assertThatThrownBy(() -> ToolJson.loads("\"a\tb\"")).hasMessageStartingWith("Invalid control character at");
        assertThatThrownBy(() -> ToolJson.loads("\uFEFF{}")).hasMessageStartingWith("Unexpected UTF-8 BOM");
    }

    @Test
    void oneValueIsReadFromAPlaceInAText() {
        final ToolJson.Decoded decoded = ToolJson.rawDecode("[{\"a\": 1}, {\"b\"", 1);

        assertThat(decoded.value()).isEqualTo(Map.of("a", 1L));
        assertThat(decoded.end()).isEqualTo(9);
        assertThatThrownBy(() -> ToolJson.rawDecode("[{\"a\": 1}, {\"b\"", 11))
                .isInstanceOf(ToolJson.JsonDecodeException.class);
    }

    @Test
    void valuesAreWrittenAsJsonDumpsWritesThem() {
        assertThat(ToolJson.dumps(SampleProfiler.map("a", 1L, "b", List.of(true, 1.0, 1e16, 0.1), "c", "café\n\"",
                "d", Map.of(), "e", Arrays.asList((Object) null))))
                .isEqualTo("{\"a\": 1, \"b\": [true, 1.0, 1e+16, 0.1], \"c\": \"caf\\u00e9\\n\\\"\", \"d\": {}, "
                           + "\"e\": [null]}");
        assertThat(ToolJson.dumps(SampleProfiler.map("a", List.of(1L, 2L), "b", Map.of()), 1))
                .isEqualTo("{\n \"a\": [\n  1,\n  2\n ],\n \"b\": {}\n}");
    }

    @Test
    void charactersPastAsciiAreKeptWithEnsureAsciiOff() {
        // json.dumps({'café': 'naïve\t--'}, indent=1, ensure_ascii=False), as the field descriptions are kept.
        assertThat(ToolJson.dumps(SampleProfiler.map("café", "naïve\t--\u0001"), 1, false))
                .isEqualTo("{\n \"café\": \"naïve\\t--\\u0001\"\n}");
        assertThat(ToolJson.dumps(SampleProfiler.map("café", "x"), 1, true))
                .isEqualTo("{\n \"caf\\u00e9\": \"x\"\n}");
        assertThat(ToolJson.dumps(Map.of(), 1, false)).isEqualTo("{}");
    }

    @Test
    void floatsArePrintedInShortestForm() {
        assertThat(ToolText.floatRepr(1791712800.123)).isEqualTo("1791712800.123");
        assertThat(ToolText.floatRepr(100.0)).isEqualTo("100.0");
        assertThat(ToolText.floatRepr(1.5e-7)).isEqualTo("1.5e-07");
        assertThat(ToolText.floatRepr(0.0001)).isEqualTo("0.0001");
        assertThat(ToolText.floatRepr(1.2345678901234568e17)).isEqualTo("1.2345678901234568e+17");
        assertThat(ToolText.round(2.675, 2)).isEqualTo(2.67);
        assertThat(ToolText.round(66.66666, 1)).isEqualTo(66.7);
        assertThat(ToolText.round(2.5)).isEqualTo(2L);
    }
}
