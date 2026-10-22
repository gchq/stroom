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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What counts as sample data (the Stroom MCP server's `tests/test_samples.py`, for `utils/samples.py`).
class TestSampleArguments {

    private static final String PATHS = "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_1.json, "
                                        + "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_2.json";
    private static final String FORTIOS = "date=2026-10-01 time=10:00:00 devname=\"fw01\" srcip=10.0.0.1 "
                                          + "dstip=203.0.113.5 dstport=443 action=\"accept\"\n"
                                          + "date=2026-10-01 time=10:00:05 devname=\"fw01\" srcip=10.0.0.2 "
                                          + "dstip=203.0.113.6 dstport=80 action=\"deny\"\n";

    @Test
    void pathsAndReferencesAreNotSamples() {
        assertThat(SampleArguments.whyNotData(PATHS, "sample")).hasValueSatisfying(why ->
                assertThat(why).contains("is a file path"));
        assertThat(SampleArguments.whyNotData("c:\\logs\\fw.log", "sample")).isPresent();
        assertThat(SampleArguments.whyNotData("/var/log/syslog", "sample")).isPresent();
        assertThat(SampleArguments.whyNotData("#file:fortios", "sample")).isPresent();
        assertThat(SampleArguments.whyNotData("   ", "sample")).hasValueSatisfying(why ->
                assertThat(why).contains("is empty"));
        assertThat(SampleArguments.whyNotData(FORTIOS, "sample")).isEmpty();
        // Data with commas is not a path list; JSON mentioning a path is data.
        assertThat(SampleArguments.whyNotData("2026-10-01T10:00:00Z,alice,login\n", "sample")).isEmpty();
        assertThat(SampleArguments.whyNotData("{\"path\": \"c:\\\\x\\\\y.json\"}", "sample")).isEmpty();
        assertThatThrownBy(() -> SampleArguments.namedSamples(Map.of("fortios", PATHS)))
                .hasMessageContaining("cannot read the client");
        assertThat(SampleArguments.namedSamples(List.of(FORTIOS, FORTIOS)).keySet())
                .containsExactly("sample 1", "sample 2");
        assertThat(SampleArguments.namedSamples(FORTIOS)).isEqualTo(Map.of("sample", FORTIOS));
        assertThat(SampleArguments.namedSamples(null)).isEmpty();
    }

    @Test
    void samplesCutUpByTheToolCallAreExplained() {
        final Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("{\"date\"", "2026-10-01 time=10:00:00 srcip=10.0.0.1\\n{\"date\"");
        assertThatThrownBy(() -> SampleArguments.namedSamples(broken))
                .hasMessageContaining("samples arrived broken")
                .hasMessageContaining("list of strings");
    }

    @Test
    void aListOfNameAndTextRecordsIsOneSampleEach() {
        assertThat(SampleArguments.namedSamples(List.of(Map.of("name", "fw.csv", "text", FORTIOS),
                Map.of("content", FORTIOS)))).isEqualTo(Map.of("fw.csv", FORTIOS, "sample 2", FORTIOS));
        assertThatThrownBy(() -> SampleArguments.namedSamples(List.of(Map.of("name", "fw.csv", "text", PATHS))))
                .hasMessageContaining("is a file path");
    }

    @Test
    void aListOfNamedTextsAndDoublyEscapedNewlinesAreRead() {
        assertThat(SampleArguments.namedSamples(List.of(Map.of("fw.log", FORTIOS), FORTIOS)))
                .isEqualTo(Map.of("fw.log", FORTIOS, "sample 2", FORTIOS));
        assertThat(SampleArguments.namedSamples(List.of("a,b\\n1,2\\n3,4")))
                .isEqualTo(Map.of("sample 1", "a,b\n1,2\n3,4"));
        // JSON escapes kept.
        assertThat(SampleArguments.namedSamples(Map.of("fw.json", "{\"msg\": \"a\\nb\"}\n{\"msg\": \"c\"}"))
                .get("fw.json")).containsOnlyOnce("\\n");
    }

    @Test
    void aFileAReaderCutShortIsNotSampleData() {
        // Seen: VS Code's read_file cut 755 KB one-line JSON files at 2,000 characters; the agent closed each array
        // after three records, completing the cut one with values it made up, and uploaded that as the sample.
        final String cut = "[{\"a\": \"1\"}, {\"b\": \"2 x=1 [... truncated at 2000 characters]";
        assertThat(SampleArguments.whyNotData(cut, "sample")).hasValueSatisfying(why ->
                assertThat(why).contains("holds a file reader's cut ('[... truncated')"));
        assertThat(SampleArguments.whyNotData(cut, "samples[0]")).hasValueSatisfying(why ->
                assertThat(why).contains("upload_sample files=[their paths]"));
        // Data that happens to hold [...].
        assertThat(SampleArguments.whyNotData("msg=\"see [...] below\" n=3\n", "sample")).isEmpty();
    }

    @Test
    void aSampleIsText() {
        assertThatThrownBy(() -> SampleArguments.namedSamples(Map.of("a.log", 3)))
                .hasMessage("a.log: a sample is the file's text, not int");
    }

    @Test
    void stringsAreWrittenAsPythonWritesThem() {
        assertThat(SampleArguments.pythonList(List.of("c:\\x", "it's"))).isEqualTo("['c:\\\\x', \"it's\"]");
    }
}
