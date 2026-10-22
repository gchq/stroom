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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The pipeline tools' list arguments (the Stroom MCP server's `utils/params.py` `ONE_OR_MORE`, and its test in
/// `tests/test_params.py`).
class TestPipelineArguments {

    @Test
    void oneOrMoreReadsWhatModelsSend() {
        assertThat(PipelineArguments.oneOrMore(null)).isEmpty();
        assertThat(PipelineArguments.oneOrMore(List.of(1, 2))).containsExactly(1, 2);
        assertThat(PipelineArguments.oneOrMore(15783601)).containsExactly(15783601);
        assertThat(PipelineArguments.oneOrMore("['Feed']")).containsExactly("Feed");
        assertThat(PipelineArguments.oneOrMore("[\"Feed\", \"Pipeline\"]")).containsExactly("Feed", "Pipeline");
        assertThat(PipelineArguments.oneOrMore("[1, 2]")).containsExactly(1L, 2L);
        assertThat(PipelineArguments.oneOrMore("Feed")).containsExactly("Feed");
        assertThat(PipelineArguments.oneOrMore("Feed, Pipeline")).containsExactly("Feed", "Pipeline");
        assertThat(PipelineArguments.oneOrMore("  ")).isEmpty();
        assertThat(PipelineArguments.oneOrMore("[{\"field\": \"UserId\", \"value\": \"alice\"}]"))
                .containsExactly(Map.of("field", "UserId", "value", "alice"));
        assertThat(PipelineArguments.oneOrMore("{\"field\": \"UserId\", \"value\": \"alice\"}"))
                .containsExactly(Map.of("field", "UserId", "value", "alice"));
    }

    @Test
    void aMappingOfTextIsAnObjectOrItsJson() {
        assertThat(PipelineArguments.textMap(Map.of("V1.2", "V1.3"), "rename")).containsEntry("V1.2", "V1.3");
        assertThat(PipelineArguments.textMap("{\"-v1\": \"-v2\"}", "rename")).containsEntry("-v1", "-v2");
        assertThat(PipelineArguments.textMap("{'V1': 'V2'}", "rename")).containsEntry("V1", "V2");
        assertThat(PipelineArguments.textMap(null, "rename")).isEmpty();
        assertThatThrownBy(() -> PipelineArguments.textMap("V1 to V2", "rename"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("rename is an object of text replacements");
    }

    @Test
    void aFlagIsTrueOrItsText() {
        assertThat(PipelineArguments.flag(true)).isTrue();
        assertThat(PipelineArguments.flag(" true ")).isTrue();
        assertThat(PipelineArguments.flag("false")).isFalse();
        assertThat(PipelineArguments.flag(null)).isFalse();
    }
}
