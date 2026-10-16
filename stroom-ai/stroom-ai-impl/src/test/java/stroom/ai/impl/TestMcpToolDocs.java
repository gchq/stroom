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

package stroom.ai.impl;

import stroom.openai.shared.McpToolPolicy;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolDocs {

    @Test
    void aToolIsDocumentedWithWhatItDoesAndTakes() {
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feed", Map.of("type", "string", "description", "The feed's name"));
        properties.put("mode", Map.of("type", "string", "enum", List.of("full", "quick"), "default", "quick"));
        properties.put("tags", Map.of("type", "array", "items", Map.of("type", "string")));
        final Tool tool = Tool.builder()
                .name("start_build")
                .description("Starts a build of the feed's pipeline.")
                .inputSchema(Map.of("type", "object", "properties", properties, "required", List.of("feed")))
                .annotations(new ToolAnnotations(null, false, true, null, false, null))
                .build();

        final String markdown = McpToolDocs.markdown(tool, McpToolPolicy.ASK_ALWAYS, true);

        assertThat(markdown)
                .startsWith("## start\\_build\n\nStarts a build of the feed's pipeline.")
                .contains("**What its server says it does:** can modify, destructive\n")
                .contains("**Policy:** Ask every time")
                .contains("| `feed` | string | yes | The feed's name |")
                .contains("| `mode` | string (one of: full, quick) |  | Default: quick. |")
                .contains("| `tags` | array of string |  |  |");
    }

    @Test
    void anUntrustedServersClaimsAreMarkedAndNoParametersSaySo() {
        final Tool tool = Tool.builder()
                .name("ping")
                .inputSchema(Map.of("type", "object"))
                .annotations(new ToolAnnotations(null, true, null, null, null, null))
                .build();

        assertThat(McpToolDocs.markdown(tool, null, false))
                .contains("read-only (the server isn't trusted, so this isn't relied on)")
                .contains("none matches")
                .contains("### Parameters\n\nNone.");
        assertThat(McpToolDocs.markdown(tool, McpToolPolicy.DENY, true)).contains("never offered to the model");
    }

    @Test
    void cellsCantBreakTheTable() {
        final Tool tool = Tool.builder()
                .name("x")
                .inputSchema(Map.of("properties", Map.of("q", Map.of("type", "string",
                        "description", "a | b\nc"))))
                .build();

        assertThat(McpToolDocs.markdown(tool, null, true)).contains("| `q` | string |  | a \\| b c |");
    }
}
