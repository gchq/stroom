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


package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// That every built-in tool has a summary is tested with the real tools, in TestRealAiToolInjection.
class TestToolSummaries {

    @Test
    void testEachSummaryIsOneShortSentenceForAnAdministrator() {
        assertThat(ToolSummaries.toolNames()).isNotEmpty();
        assertThat(ToolSummaries.toolNames()).allSatisfy(name -> assertThat(ToolSummaries.of(name))
                .hasValueSatisfying(summary -> assertThat(summary)
                        .as(name)
                        .hasSizeLessThanOrEqualTo(90)
                        .endsWith(".")
                        .doesNotContain("\n")
                        // Written about the agent, not to it.
                        .doesNotContainPattern("(?i)\\byou(r)?\\b")));
    }

    @Test
    void testAToolWithoutASummaryHasNone() {
        assertThat(ToolSummaries.of("not_a_tool")).isEmpty();
        assertThat(ToolSummaries.of(null)).isEmpty();
    }
}
