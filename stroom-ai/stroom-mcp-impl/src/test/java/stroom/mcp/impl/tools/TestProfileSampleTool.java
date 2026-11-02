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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.shared.AiToolCategory;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// profile_sample, the tool.
class TestProfileSampleTool {

    static final String PATHS = "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_1.json, "
                                + "c:\\Users\\pkimb\\Documents\\stroom-mcp\\sample-data\\fortios\\001_2.json";
    static final String FORTIOS = "date=2026-10-01 time=10:00:00 devname=\"fw01\" srcip=10.0.0.1 dstip=203.0.113.5 "
                                  + "dstport=443 action=\"accept\"\n"
                                  + "date=2026-10-01 time=10:00:05 devname=\"fw01\" srcip=10.0.0.2 dstip=203.0.113.6 "
                                  + "dstport=80 action=\"deny\"\n";

    private final SampleStreamTexts streamTexts = Mockito.mock(SampleStreamTexts.class);
    private final ProfileSampleTool tool = new ProfileSampleTool(new SampleProfiling.Profiled(), streamTexts);

    @Test
    void theToolRefusesPathsWithTheInstructionToPassTheText() {
        final Map<String, Object> samples = Map.of("fortios", PATHS);

        final ToolResult result = call(Map.of("samples", samples));

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("read the file and pass its text");
    }

    @Test
    void samplesCutUpByTheToolCallAreExplained() {
        // Gemma 4 on vLLM, onboarding a CSV: the file's text cut at ':' and ',' into an object, inside a list.
        final Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("\"sample_firewall_logs_20.csv\"", "\"timestamp");
        broken.put("device,event_type,severity,src_ip\\n2026-10-01T09", "00:12+10:00");
        broken.put("FW-EDGE-01,TRAFFIC,INFO,192.0.2.10\\n2026-10-01T09", "01:05+10:00");

        final ToolResult result = call(Map.of("samples", List.of(broken)));

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("samples arrived broken").contains("list of strings");
    }

    @Test
    void theToolReadsTheSampleFromItsStreamsInsteadOfItsText() {
        // The text goes through the model once, to upload_sample; the tools after it take stream_ids.
        final String csv = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n2026-10-01T10:05:00Z,bob,logout\n";
        Mockito.when(streamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(Map.of("stream 7", csv),
                List.of("stream 7 is read up to its first 80 characters (the most a sample is read to)")));

        final Map<String, Object> result = json(call(Map.of("stream_ids", List.of(7))));

        assertThat(String.valueOf(result.get("format"))).startsWith("delimited");
        assertThat(result.get("read")).isEqualTo(List.of("stream 7 is read up to its first 80 characters (the most "
                                                         + "a sample is read to)"));
        // Read from the stream: no note on how much text was sent.
        assertThat(result).doesNotContainKey("sample_note");
    }

    @Test
    void oneSampleOrSeveralAreProfiled() {
        assertThat(json(call(Map.of("sample", FORTIOS))).get("format")).isEqualTo("key=value");
        final Map<String, Object> several = json(call(Map.of("samples", List.of(FORTIOS, FORTIOS))));
        assertThat(((Map<?, ?>) several.get("files")).keySet()).isEqualTo(Set.of("sample 1", "sample 2"));
        // A sample given on its own as well joins the samples.
        final Map<String, Object> both = json(call(Map.of("sample", FORTIOS, "samples", Map.of("fw.log", FORTIOS))));
        assertThat(((Map<?, ?>) both.get("files")).keySet()).isEqualTo(Set.of("fw.log", "sample"));
    }

    @Test
    void moreSampleTextThanProfilingNeedsIsNotedForNextTime() {
        final Map<String, Object> result = json(call(Map.of("sample", FORTIOS.repeat(120))));

        assertThat(String.valueOf(result.get("sample_note"))).startsWith("More sample text than profiling needs: "
                                                                        + "sample (");
    }

    @Test
    void noSampleIsRefusedSayingWhatToGive() {
        assertThat(call(Map.of()).text())
                .isEqualTo("Give sample (the file's text), samples (several files' texts), or stream_ids");
    }

    @Test
    void xmlFragmentsComeWithTheirWrapper() {
        final String fragments = "<Event><System><EventID>4624</EventID></System></Event>\n"
                                 + "<Event><System><EventID>4625</EventID></System></Event>\n";

        final Map<String, Object> result = json(call(Map.of("sample", fragments)));

        assertThat(result.get("format")).isEqualTo("xml fragments");
        assertThat(((Map<?, ?>) result.get("text_converter")).get("code"))
                .isEqualTo(SampleProfiler.XML_FRAGMENT_WRAPPER);
    }

    @Test
    void sampleStreamsAreReadOnlyByAUserWhoMayViewData() {
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        final SampleStreamTexts texts = new SampleStreamTexts(Mockito.mock(StreamReader.class),
                Mockito.mock(FeedNames.class), documentAccess);

        assertThatThrownBy(() -> texts.read(List.of(7L))).isInstanceOf(ToolRefusal.class)
                .hasMessage(SampleStreamTexts.NO_VIEW_DATA);
    }

    @Test
    void itOnlyReadsAndIsTheOnboardingStart() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.usesRunSlot()).isFalse();
        assertThat(tool.category()).isEqualTo(AiToolCategory.PROCESSING);
        assertThat(tool.definition().description().toString()).startsWith("Onboarding start: Profile raw data");
    }

    private ToolResult call(final Map<String, Object> arguments) {
        return tool.execute(BuildToolCalls.call(ProfileSampleTool.NAME, JsonUtil.writeValueAsString(arguments,
                false)), BuildToolCalls.context());
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }
}
