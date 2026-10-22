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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSampleTexts {

    private static final String PATHS = "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_1.json, "
                                        + "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_2.json";
    private static final String FORTIOS = "date=2026-10-01 time=10:00:00 devname=\"fw01\" srcip=10.0.0.1 "
                                          + "dstip=203.0.113.5 dstport=443 action=\"accept\"\n"
                                          + "date=2026-10-01 time=10:00:05 devname=\"fw01\" srcip=10.0.0.2 "
                                          + "dstip=203.0.113.6 dstport=80 action=\"deny\"\n";

    private static Map<String, Object> named(final Object... keysAndValues) {
        return SampleProfiler.map(keysAndValues);
    }

    @Test
    void pathsAndReferencesAreNotSamples() {
        assertThat(SampleTexts.whyNotData(PATHS, "sample")).contains("is a file path");
        assertThat(SampleTexts.whyNotData("c:\\logs\\fw.log", "sample")).contains("is a file path");
        assertThat(SampleTexts.whyNotData("/var/log/syslog", "sample")).contains("is a file path");
        assertThat(SampleTexts.whyNotData("#file:fortios", "sample")).contains("is a file path");
        assertThat(SampleTexts.whyNotData("   ", "sample")).contains("is empty");
        assertThat(SampleTexts.whyNotData(FORTIOS, "sample")).isNull();
        // Data with commas is not a path list.
        assertThat(SampleTexts.whyNotData("2026-10-01T10:00:00Z,alice,login\n", "sample")).isNull();
        // JSON mentioning a path is data.
        assertThat(SampleTexts.whyNotData("{\"path\": \"c:\\\\x\\\\y.json\"}", "sample")).isNull();
        assertThatThrownBy(() -> SampleTexts.asNamedSamples(named("fortios", PATHS), null))
                .isInstanceOf(SampleRefusal.class)
                .hasMessageContaining("cannot read the client")
                .hasMessageContaining("read the file and pass its text");
        assertThat(SampleTexts.asNamedSamples(List.of(FORTIOS, FORTIOS), null).keySet())
                .containsExactly("sample 1", "sample 2");
        assertThat(SampleTexts.asNamedSamples(null, FORTIOS)).isEqualTo(Map.of("sample", FORTIOS));
    }

    @Test
    void samplesCutUpByTheToolCallAreExplained() {
        // Gemma 4 on vLLM, onboarding a CSV: the file's text cut at ':' and ',' into an object, inside a list.
        final Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("\"sample_firewall_logs_20.csv\"", "\"timestamp");
        broken.put("device,event_type,severity,src_ip\\n2026-10-01T09", "00:12+10:00");
        broken.put("FW-EDGE-01,TRAFFIC,INFO,192.0.2.10\\n2026-10-01T09", "01:05+10:00");

        assertThatThrownBy(() -> SampleTexts.asNamedSamples(List.of(broken), null))
                .isInstanceOf(SampleRefusal.class)
                .hasMessageContaining("samples arrived broken")
                .hasMessageContaining("list of strings");
    }

    @Test
    void aListOfNameAndTextRecordsIsOneSampleEach() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("fw.csv", FORTIOS);
        expected.put("sample 2", FORTIOS);

        assertThat(SampleTexts.asNamedSamples(List.of(named("name", "fw.csv", "text", FORTIOS),
                named("content", FORTIOS)), null)).isEqualTo(expected);
        assertThatThrownBy(() -> SampleTexts.asNamedSamples(List.of(named("name", "fw.csv", "text", PATHS)), null))
                .hasMessageContaining("is a file path");
    }

    @Test
    void aListOfNamedTextsAndDoublyEscapedNewlinesAreRead() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("fw.log", FORTIOS);
        expected.put("sample 2", FORTIOS);

        assertThat(SampleTexts.asNamedSamples(List.of(named("fw.log", FORTIOS), FORTIOS), null)).isEqualTo(expected);
        assertThat(SampleTexts.asNamedSamples(List.of("a,b\\n1,2\\n3,4"), null))
                .isEqualTo(Map.of("sample 1", "a,b\n1,2\n3,4"));
        // JSON escapes kept.
        assertThat(SampleTexts.asNamedSamples(named("fw.json", "{\"msg\": \"a\\nb\"}\n{\"msg\": \"c\"}"), null)
                .get("fw.json")).containsOnlyOnce("\\n");
    }

    @Test
    void aFileAReaderCutShortIsNotSampleData() {
        // Seen: VS Code's read_file cut 755 KB one-line JSON files at 2,000 characters; the agent closed each array
        // after three records, completing the cut one with values it made up, and uploaded that as the sample.
        final String cut = "[{\"a\": \"1\"}, {\"b\": \"2 x=1 [... truncated at 2000 characters]";

        assertThat(SampleTexts.whyNotData(cut, "sample")).contains("holds a file reader's cut ('[... truncated')");
        assertThat(SampleTexts.whyNotData(cut, "samples[0]")).contains("upload_sample files=[their paths]");
        // Data that happens to hold [...].
        assertThat(SampleTexts.whyNotData("msg=\"see [...] below\" n=3\n", "sample")).isNull();
    }

    @Test
    void aSampleThatIsNotTextIsRefused() {
        assertThatThrownBy(() -> SampleTexts.asNamedSamples(named("fw.log", 42L), null))
                .hasMessage("fw.log: a sample is the file's text, not int");
    }

    @Test
    void farMoreTextThanProfilingNeedsIsNotedForNextTime() {
        assertThat(SampleTexts.longSamplesNote(Map.of("small.log", FORTIOS))).isNull();

        final String note = SampleTexts.longSamplesNote(Map.of("big.log", "x".repeat(25_000)));

        assertThat(note).isEqualTo("More sample text than profiling needs: big.log (25,000 characters). About 10 "
                                   + "records a file is enough; for files on the user's disk give their paths "
                                   + "(start_onboarding files=), so the text never passes through you.");
        assertThat(SampleTexts.SAMPLE_GUIDE).startsWith("about 10 records and at most 8,000 characters a file");
    }
}
