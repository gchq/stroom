/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.shared.AiToolCategory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestBuiltInTools {

    @Test
    void getCurrentDate() {
        assertThat(run(new GetCurrentDateTool(), "{}"))
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
    }

    @Test
    void getCurrentTimeIsUtcIso() {
        assertThat(run(new GetCurrentTimeTool(), "{}"))
                .matches("\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    void getCurrentDatetimeIsUtcIsoAndCurrent() {
        final String result = run(new GetCurrentDateTimeTool(), "{}");
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        assertThat(Duration.between(Instant.parse(result), Instant.now()).abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void getUtcTimeOffsetForNamedZone() {
        assertThat(run(new GetUtcTimeOffsetTool(), "{\"timeZone\":\"UTC\"}"))
                .isEqualTo("UTC is currently +00:00 from UTC.");
        // Etc/GMT-5 is a fixed UTC+5 zone (POSIX sign convention).
        assertThat(run(new GetUtcTimeOffsetTool(), "{\"timeZone\":\"Etc/GMT-5\"}"))
                .isEqualTo("Etc/GMT-5 is currently +05:00 from UTC.");
    }

    @Test
    void getUtcTimeOffsetRejectsUnknownZoneWithoutThrowing() {
        assertThat(run(new GetUtcTimeOffsetTool(), "{\"timeZone\":\"Nowhere/Special\"}"))
                .contains("Unknown time zone 'Nowhere/Special'");
    }

    @Test
    void registryExposesEveryToolAsAnExecutor() {
        final BuiltInTools registry = new BuiltInTools(Set.of(
                new GetCurrentDateTool(),
                new GetCurrentTimeTool(),
                new GetCurrentDateTimeTool(),
                new GetUtcTimeOffsetTool()), ToolCalls.chatContexts());

        assertThat(registry.toolMap().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrder("get_current_date", "get_current_time",
                        "get_current_datetime", "get_utc_time_offset");
    }

    @Test
    void registryReportsAToolFailureAsTextRatherThanPropagating() {
        final ToolSpecification spec = ToolSpecification.builder().name("boom").build();
        final BuiltInTools registry = new BuiltInTools(Set.of(new StroomTool() {
            @Override
            public AiToolCategory category() {
                return AiToolCategory.CORE;
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder().name("boom").build();
            }

            @Override
            public ToolResult execute(final ToolCall request, final ToolCallContext context) {
                throw new IllegalStateException("kaboom");
            }
        }), ToolCalls.chatContexts());

        final ToolExecutor executor = registry.toolMap().get(spec);
        assertThat(executor.execute(ToolExecutionRequest.builder().id("1").name("t").arguments("{}").build(), 1))
                .contains("Error running built-in tool 'boom'")
                .contains("kaboom");
    }

    @Test
    void theBuildToolsInstructionsComeWithTheBuildTools() {
        final BuiltInTools registry = new BuiltInTools(Set.of(new GetCurrentDateTool()), ToolCalls.chatContexts());
        // Without bundled knowledge there are none.
        assertThat(registry.buildInstructions(Set.of("start_onboarding"))).isEmpty();

        registry.setKnowledge(Optional.of(new AgentKnowledge() {
            @Override
            public List<String> guideNames() {
                return List.of();
            }

            @Override
            public Optional<String> guide(final String name) {
                return Optional.empty();
            }

            @Override
            public List<String> conventionNames() {
                return List.of();
            }

            @Override
            public Optional<String> convention(final String name) {
                return Optional.empty();
            }

            @Override
            public Optional<String> buildInstructions() {
                return Optional.of("Order of work: start_onboarding first.\n");
            }
        }));

        assertThat(registry.buildInstructions(Set.of("start_onboarding", "read_file")))
                .contains("## Instructions for Stroom's build tools\n\nOrder of work: start_onboarding first.");
        // Not offered the build tools (e.g. a read-only policy without Content): not given their instructions.
        assertThat(registry.buildInstructions(Set.of("read_file"))).isEmpty();
    }

    private static String run(final StroomTool tool, final String argsJson) {
        return tool.execute(request(argsJson), ToolCalls.outsideChat()).text();
    }

    private static ToolCall request(final String argsJson) {
        return new ToolCall("1", "t", argsJson);
    }
}
