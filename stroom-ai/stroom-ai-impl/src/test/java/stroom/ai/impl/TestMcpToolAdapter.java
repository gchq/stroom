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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolAdapter {

    @Test
    void testToToolSpecification() {
        final Tool tool = Tool.builder()
                .name("find_documents")
                .description("Finds Stroom documents")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "Name to search for")),
                        "required", List.of("name")))
                .build();

        final ToolSpecification spec = McpToolAdapter.toToolSpecification(tool);

        assertThat(spec.name()).isEqualTo("find_documents");
        assertThat(spec.description()).isEqualTo("Finds Stroom documents");
        assertThat(spec.parameters().properties()).containsKey("name");
        assertThat(spec.parameters().properties().get("name")).isInstanceOf(JsonStringSchema.class);
        assertThat(spec.parameters().required()).containsExactly("name");
    }

    @Test
    void testToToolSpecification_fallsBackToTitleThenName() {
        final Tool withTitle = Tool.builder().name("t1").title("Tool one").inputSchema(Map.of()).build();
        final Tool bare = Tool.builder().name("t2").inputSchema(Map.of()).build();

        assertThat(McpToolAdapter.toToolSpecification(withTitle).description()).isEqualTo("Tool one");
        assertThat(McpToolAdapter.toToolSpecification(bare).description()).isEqualTo("t2");
        assertThat(McpToolAdapter.toToolSpecification(bare).parameters()).isNull();
    }

    @Test
    void testToParameters_unconvertibleSchemaGivesEmptyObject() {
        final JsonObjectSchema schema = McpToolAdapter.toParameters(Map.of("type", "string"));

        assertThat(schema).isNotNull();
        assertThat(schema.properties()).isEmpty();
    }

    @Test
    void testParseArguments() {
        assertThat(McpToolAdapter.parseArguments(null)).isEmpty();
        assertThat(McpToolAdapter.parseArguments("  ")).isEmpty();
        assertThat(McpToolAdapter.parseArguments("{\"a\":1,\"b\":\"x\"}"))
                .containsEntry("a", 1)
                .containsEntry("b", "x");
    }

    @Test
    void testRenderResult_text() {
        final CallToolResult result = CallToolResult.builder()
                .content(List.of(new TextContent("first"), new TextContent("second")))
                .isError(false)
                .build();

        assertThat(McpToolAdapter.renderResult(result)).isEqualTo("first\n\nsecond");
    }

    @Test
    void testRenderResult_error() {
        final CallToolResult result = CallToolResult.builder()
                .content(List.of(new TextContent("feed not found")))
                .isError(true)
                .build();

        assertThat(McpToolAdapter.renderResult(result)).isEqualTo("Error: feed not found");
    }

    @Test
    void testRenderResult_binaryContentIsDescribedNotIncluded() {
        final CallToolResult result = CallToolResult.builder()
                .content(List.of(
                        new ImageContent(null, "aGVsbG8=", "image/png"),
                        new EmbeddedResource(null, new BlobResourceContents("stroom://x", "application/zip", "UEs="))))
                .build();

        final String text = McpToolAdapter.renderResult(result);

        assertThat(text)
                .contains("[Image (image/png) not shown]")
                .contains("[Binary resource stroom://x (application/zip) not shown]")
                .doesNotContain("aGVsbG8=")
                .doesNotContain("UEs=");
    }

    @Test
    void testRenderResult_structuredContentWhenNoText() {
        final CallToolResult result = CallToolResult.builder()
                .structuredContent(Map.of("count", 42))
                .build();

        assertThat(McpToolAdapter.renderResult(result)).isEqualTo("{\"count\":42}");
    }

    @Test
    void testRenderResource() {
        final ReadResourceResult result = new ReadResourceResult(List.of(
                new TextResourceContents("stroom://guide/xslt", "text/markdown", "# XSLT guide")));

        assertThat(McpToolAdapter.renderResource(result)).isEqualTo("# XSLT guide");
    }

    @Test
    void testRenderResourceList() {
        final Resource resource = Resource.builder()
                .uri("stroom://guides")
                .name("guides")
                .title("Working guides")
                .description("Index of the guides")
                .build();

        assertThat(McpToolAdapter.renderResourceList(List.of(resource)))
                .isEqualTo("- stroom://guides (Working guides): Index of the guides");
        assertThat(McpToolAdapter.renderResourceList(List.of())).isEqualTo("No resources");
    }
}
