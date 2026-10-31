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
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// start_onboarding and build_status: the build started from its samples, and the plan.
class TestStartOnboardingTool {

    private final BuildWorld world = new BuildWorld();
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
    private final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
    private final AgentInstructions instructions = Mockito.mock(AgentInstructions.class);
    private final SampleStreamTexts streamTexts = Mockito.mock(SampleStreamTexts.class);
    private final StartOnboardingTool tool = new StartOnboardingTool(world.guard(), plan, new KeyValueProfiler(),
            streamTexts, templates, instructions, world.records());

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

        final ToolResult refused = tool.execute(BuildToolCalls.call(StartOnboardingTool.NAME,
                "{\"source_name\": \"x\", \"samples\": {}}"), BuildToolCalls.context());
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
        final Map<String, Object> result = BuildToolCalls.json(status.execute(BuildToolCalls.call(
                BuildStatusTool.NAME, "{\"build\": \"b\"}"), BuildToolCalls.context()).text());
        assertThat(result.get("build")).isEqualTo("b");
        assertThat(((Map<?, ?>) result.get("next")).get("step")).isEqualTo("feed");
        assertThat(status.definition().description().toString()).startsWith("Onboarding, any step: ");
        assertThat(status.isReadOnly()).isTrue();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.definition().description().toString()).startsWith("Onboarding start: ");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStatusIsBriefUnlessAskedForInFull() {
        // Seen: its 10 KB result saved to the workspace and called for again and again.
        final BuildStatusTool status = new BuildStatusTool(plan);
        final Map<String, Object> brief = BuildToolCalls.json(status.execute(BuildToolCalls.call(
                BuildStatusTool.NAME, "{\"build\": \"b\"}"), BuildToolCalls.context()).text());
        final Map<String, Object> full = BuildToolCalls.json(status.execute(BuildToolCalls.call(
                BuildStatusTool.NAME, "{\"build\": \"b\", \"full\": true}"), BuildToolCalls.context()).text());

        assertThat(brief.keySet()).startsWith("next", "before_promotion", "steps");
        final Map<String, Object> states = (Map<String, Object>) brief.get("steps");
        final List<Map<String, Object>> steps = (List<Map<String, Object>>) full.get("steps");
        assertThat(states).hasSize(steps.size());
        steps.forEach(step -> assertThat(states).containsEntry((String) step.get("step"), step.get("state")));
        assertThat(brief.get("next")).isEqualTo(full.get("next"));
        assertThat((String) brief.get("hint")).contains("full: true");
        assertThat(full).doesNotContainKey("hint");

        assertThat(BuildStatusTool.compact(Map.of("documents", List.of(
                Map.of("type", "XSLT", "name", "Acme - CEF", "uuid", "u1", "role", "CEF"),
                Map.of("type", "Pipeline", "name", "Acme", "uuid", "u2", "working_copy_of", "u3"))))
                .get("documents"))
                .isEqualTo(List.of("XSLT 'Acme - CEF' (CEF) u1", "Pipeline 'Acme' u2, a working copy of u3"));
    }

    private Map<String, Object> call(final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(BuildToolCalls.call(StartOnboardingTool.NAME,
                JsonUtil.writeValueAsString(arguments, false)), BuildToolCalls.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    // Profiles any text as key=value.
    private static final class KeyValueProfiler implements SampleProfiling {

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

    @Test
    void withNoTemplateOfItsParserTheSampleIsOfferedAnotherWithItsParserReplaced() {
        // Seen: a Stroom with no XMLParser template was told only to ask the user, though a parser can be swapped.
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of(
                Map.of("name", "json-in v3", "parser", "JSONParser"),
                Map.of("name", "Acme text v2", "parser", "DSParser"))));

        assertThat(tool.templatesReading("xml fragments"))
                .isEqualTo("json-in v3 with replace_parser='XMLFragmentParser' (no template here has a "
                           + "XMLFragmentParser; its JSONParser is replaced)");
        assertThat(tool.templatesReading("xml")).startsWith("json-in v3 with replace_parser='XMLParser'");
        // One that reads it is preferred.
        assertThat(tool.templatesReading("delimited")).startsWith("Acme text v2 (its parser, DSParser");
    }

    @Test
    void forXmlFragmentsAnXmlParserTemplateIsPreferredToSwap() {
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of(
                Map.of("name", "json-in v3", "parser", "JSONParser"),
                Map.of("name", "xml-in", "parser", "XMLParser"))));

        assertThat(tool.templatesReading("xml fragments")).startsWith("xml-in with replace_parser='XMLFragmentParser'");
    }

    @Test
    void aTemplateIsTakenForItsParserNotItsName() {
        // Named for XML fragments, it holds a JSONParser: offered only with its parser replaced.
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of(
                Map.of("name", "XML Fragment Events", "parser", "JSONParser"))));

        assertThat(tool.templatesReading("xml fragments"))
                .contains("XML Fragment Events with replace_parser='XMLFragmentParser'")
                .contains("its JSONParser is replaced");
        assertThat(tool.templatesReading("json lines")).startsWith("XML Fragment Events (its parser, JSONParser");
    }

    @Test
    void withNoTemplateAtAllAnEventsPipelineOfItsOwnIsTheLastResort() {
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of()));

        assertThat(tool.templatesReading("delimited"))
                .startsWith("none found that reads delimited (a DSParser): ask the user which pipeline to base it on")
                .endsWith("only if they have none, create_pipeline standalone='events' builds one on its own (a "
                          + "last resort)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReferenceTableIsStartedAsReferenceData() {
        // Asked for by the user: a table to look values up in was planned as an events onboarding.
        facts.kind = "reference";
        final Map<String, Object> started = BuildToolCalls.json(tool.execute(BuildToolCalls.call(
                StartOnboardingTool.NAME, "{\"source_name\": \"HR users\", \"files\": [\"users.csv\"], "
                                          + "\"kind\": \"reference\"}"), BuildToolCalls.context()).text());

        assertThat(world.records().kind("onboard-hr-users")).hasValue("reference");
        assertThat(((List<Map<String, Object>>) started.get("plan")).stream().map(step -> step.get("step")))
                .contains("reference_pipeline").doesNotContain("translation");
        assertThat((Map<String, Object>) started.get("next")).containsEntry("step", "feed");
        assertThat(String.valueOf(((Map<String, Object>) ((Map<String, Object>) started.get("next")).get("call"))
                .get("arguments"))).as("its feed takes Raw Reference").contains("Raw Reference");
    }
}
