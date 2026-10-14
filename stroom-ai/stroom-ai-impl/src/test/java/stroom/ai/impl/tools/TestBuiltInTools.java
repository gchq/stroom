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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
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
                new GetUtcTimeOffsetTool()));

        assertThat(registry.toolMap().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrder("get_current_date", "get_current_time",
                        "get_current_datetime", "get_utc_time_offset");
    }

    @Test
    void registryReportsAToolFailureAsTextRatherThanPropagating() {
        final ToolSpecification spec = ToolSpecification.builder().name("boom").build();
        final BuiltInTools registry = new BuiltInTools(Set.of(new BuiltInTool() {
            @Override
            public ToolSpecification specification() {
                return spec;
            }

            @Override
            public String execute(final ToolExecutionRequest request) {
                throw new IllegalStateException("kaboom");
            }
        }));

        final ToolExecutor executor = registry.toolMap().get(spec);
        assertThat(executor.execute(request("{}"), null))
                .contains("Error running built-in tool 'boom'")
                .contains("kaboom");
    }

    private static String run(final BuiltInTool tool, final String argsJson) {
        return tool.execute(request(argsJson));
    }

    private static ToolExecutionRequest request(final String argsJson) {
        return ToolExecutionRequest.builder().id("1").name("t").arguments(argsJson).build();
    }
}
