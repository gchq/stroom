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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.ToolResult;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// start_onboarding and build_status (the Stroom MCP server's `tests/test_plan.py`).
class TestStartOnboardingTool {

    private final BuildWorld world = new BuildWorld();
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(),
            new Consent(Mockito.mock(SecurityContext.class), () -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
    private final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
    private final AgentInstructions instructions = Mockito.mock(AgentInstructions.class);
    private final SampleStreamTexts streamTexts = Mockito.mock(SampleStreamTexts.class);
    private final StartOnboardingTool tool = new StartOnboardingTool(world.guard(), plan, new KeyValueProfiler(),
            streamTexts, templates, instructions);

    TestStartOnboardingTool() {
        Mockito.when(instructions.targets(any(), any(), any(), any()))
                .thenReturn(new AgentInstructions.Targets(List.of("/"), List.of()));
        Mockito.when(instructions.applicableInstructions(any(), anyString(), any()))
                .thenReturn(Map.of("instructions", List.of()));
    }

    @Test
    void startOnboardingProfilesEveryFileAndReturnsThePlan() {
        // The environment's own templates, whatever they're called: no 'Event Data (Text)' is assumed.
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of(
                Map.of("name", "json-in v3", "parser", "JSONParser"),
                Map.of("name", "Acme text v2", "parser", "DSParser"))));
        final Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("a.log", "date=2026-10-01 time=10:00:00 srcip=10.0.0.1 action=accept\n".repeat(3));
        samples.put("b.log", "date=2026-10-02 time=10:00:00 srcip=10.0.0.2 action=deny dstport=443\n".repeat(3));
        final Map<String, Object> result = call(Map.of("source_name", "FortiOS firewall", "samples", samples));
        assertThat(result.get("build")).isEqualTo("onboard-fortios-firewall");
        assertThat(result.get("done")).isEqualTo(false);
        assertThat(result.get("folder")).isEqualTo("System/" + BuildWorld.WORKSPACE + "/onboard-fortios-firewall");
        assertThat(((Map<?, ?>) result.get("profile")).get("format")).isEqualTo("key=value");
        assertThat(result.get("parser")).isEqualTo("DSParser");
        assertThat(String.valueOf(result.get("template"))).startsWith("Acme text v2 (its parser, DSParser");
        assertThat(String.valueOf(result.get("text_converter"))).startsWith("needed: build_data_splitter");
        final Map<?, ?> next = (Map<?, ?>) result.get("next");
        assertThat(next.get("step")).isEqualTo("feed");
        assertThat((List<?>) result.get("plan")).hasSize(15);
        assertThat(result.get("task_list")).isEqualTo(BuildPlan.TASK_LIST);
        assertThat(String.valueOf(result.get("task_list"))).contains("next.progress");
        assertThat(next.get("call")).isEqualTo(Map.of("tool", "create_feed", "arguments", Map.of(
                "build", "onboard-fortios-firewall", "name", "<the feed name the user confirmed>")));
        assertThat(result.get("standing_instructions")).isEqualTo(Map.of("instructions", List.of()));

        final ToolResult refused = tool.execute(PortedToolCalls.call(StartOnboardingTool.NAME,
                "{\"source_name\": \"x\", \"samples\": {}}"), PortedToolCalls.context());
        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("Give the sample files");
    }

    @Test
    void onboardingFilesOnDiskStartsWithTheBuildNotTheirText() {
        // Seen (Gemma 4 31B, VS Code): 100 lines of the sample read into the conversation to be written out again as
        // start_onboarding's samples, at ~22 tokens a second. Given paths, nothing of the files passes through the
        // model.
        final Map<String, Object> result = call(Map.of("source_name", "SecretServer", "files",
                List.of("c:\\samples\\secretserver.xml")));
        assertThat(result.get("build")).isEqualTo("onboard-secretserver");
        assertThat(result).doesNotContainKey("profile");
        assertThat(result.get("done")).isEqualTo(false);
        assertThat(((Map<?, ?>) result.get("next")).get("call")).isEqualTo(Map.of("tool", "create_feed", "arguments",
                Map.of("build", "onboard-secretserver", "name", "<the feed name the user confirmed>")));
        final List<?> then = (List<?>) result.get("then");
        assertThat(String.valueOf(then.get(0))).contains("files=['c:\\\\samples\\\\secretserver.xml']");
        assertThat(String.valueOf(then.get(1))).contains("build='onboard-secretserver' stream_ids=");
    }

    @Test
    void moreSampleTextThanProfilingNeedsIsNotedForNextTime() {
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of()));
        // 23,600 characters.
        final String text = "date=2026-10-01 time=10:00:00 srcip=10.0.0.1 action=accept\n".repeat(400);
        final Map<String, Object> result = call(Map.of("source_name", "x", "samples", Map.of("a.log", text)));
        final Map<String, Object> shorter = call(Map.of("source_name", "x", "samples",
                Map.of("a.log", text.substring(0, 6000))));
        assertThat(String.valueOf(result.get("sample_note")))
                .startsWith("More sample text than profiling needs: a.log (23,600 characters)")
                .contains("start_onboarding files=");
        assertThat(shorter).doesNotContainKey("sample_note");
        assertThat(String.valueOf(result.get("template"))).startsWith("none found that reads key=value (a DSParser)");
    }

    @Test
    void uploadedSamplesAreReadFromTheirStreams() {
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of()));
        Mockito.when(streamTexts.read(List.of(7L, 8L))).thenReturn(new SampleStreamTexts.Texts(Map.of(
                "stream 7", "a=1\n", "stream 8", "a=2\n"), List.of("stream 8 is read up to its first 4 characters")));
        final Map<String, Object> result = call(Map.of("source_name", "Acme", "build", "acme-v1", "stream_ids",
                List.of(7, 8)));
        assertThat(result.get("build")).isEqualTo("acme-v1");
        assertThat(result.get("read")).isEqualTo(List.of("stream 8 is read up to its first 4 characters"));
        assertThat(result).doesNotContainKey("sample_note");
        // The build itself says what is next: its feed, here.
        assertThat(((Map<?, ?>) result.get("next")).get("step")).isEqualTo("feed");
    }

    @Test
    void aBuildIsNamedFromItsSource() {
        assertThat(StartOnboardingTool.buildName("Acme door controller", null))
                .isEqualTo("onboard-acme-door-controller");
        assertThat(StartOnboardingTool.buildName("  (Acme) ", null)).isEqualTo("onboard-acme");
        assertThat(StartOnboardingTool.buildName("Acme", "mine")).isEqualTo("mine");
    }

    @Test
    void buildStatusGivesThePlan() {
        final BuildStatusTool status = new BuildStatusTool(plan);
        final Map<String, Object> result = PortedToolCalls.json(status.execute(PortedToolCalls.call(
                BuildStatusTool.NAME, "{\"build\": \"b\"}"), PortedToolCalls.context()).text());
        assertThat(result.get("build")).isEqualTo("b");
        assertThat(((Map<?, ?>) result.get("next")).get("step")).isEqualTo("feed");
        assertThat(status.definition().description().toString()).startsWith("Onboarding, any step: ");
        assertThat(status.isReadOnly()).isTrue();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.definition().description().toString()).startsWith("Onboarding start: ");
    }

    private Map<String, Object> call(final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(PortedToolCalls.call(StartOnboardingTool.NAME,
                JsonUtil.writeValueAsString(arguments, false)), PortedToolCalls.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    // Profiles any text as key=value.
    private static final class KeyValueProfiler implements SampleProfiler {

        @Override
        public Map<String, Object> profile(final String sample) {
            return FakeBuildFacts.profileOf("key=value");
        }

        @Override
        public Map<String, Object> profileMany(final Map<String, String> samples) {
            final Map<String, Object> profile = new LinkedHashMap<>(FakeBuildFacts.profileOf("key=value"));
            profile.put("files", samples.keySet());
            return profile;
        }
    }
}
