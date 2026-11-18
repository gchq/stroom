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

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The pipeline tools' own arguments (their lists and flags are read by [stroom.ai.impl.tools.ToolArgs], tested in
/// `TestToolArgs`).
class TestPipelineArguments {

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
}
