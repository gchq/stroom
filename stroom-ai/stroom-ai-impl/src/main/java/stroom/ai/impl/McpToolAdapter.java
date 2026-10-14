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

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.modelcontextprotocol.spec.McpSchema.AudioContent;
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ResourceLink;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Converts between the MCP Java SDK's types and the langchain4j types used by the agent loop: MCP tools become
/// [ToolSpecification]s, and tool results and resources become the text that is passed back to the model.
final class McpToolAdapter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolAdapter.class);

    /// Put before the text of a failed tool call.
    static final String ERROR_PREFIX = "Error: ";

    private McpToolAdapter() {
        // Static only.
    }

    /// @param tool An MCP tool, as listed by its server.
    /// @return The tool as a langchain4j tool specification, with the MCP input schema as its parameters.
    static ToolSpecification toToolSpecification(final Tool tool) {
        final ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.name())
                .description(describe(tool));
        final JsonObjectSchema parameters = toParameters(tool.inputSchema());
        if (parameters != null) {
            builder.parameters(parameters);
        }
        return builder.build();
    }

    /// @param inputSchema An MCP tool's input schema, as a JSON schema map.
    /// @return The schema as a langchain4j object schema, an empty object schema if it cannot be converted, or
    /// null if there is no schema.
    static JsonObjectSchema toParameters(final Map<String, Object> inputSchema) {
        if (NullSafe.isEmptyMap(inputSchema)) {
            return null;
        }
        try {
            final JsonSchemaElement element = JsonSchemaElementJsonUtils.fromMap(inputSchema);
            if (element instanceof final JsonObjectSchema objectSchema) {
                return objectSchema;
            }
            LOGGER.debug(() -> "MCP tool input schema is not an object schema: " + inputSchema);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to convert MCP tool input schema: " + inputSchema, e);
        }
        return JsonObjectSchema.builder().build();
    }

    /// @param argumentsJson The arguments the model supplied for a tool call, as a JSON object.
    /// @return The arguments as a map, empty if there are none.
    static Map<String, Object> parseArguments(final String argumentsJson) {
        if (NullSafe.isBlankString(argumentsJson)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked") final Map<String, Object> args = JsonUtil.readValue(argumentsJson, Map.class);
        return NullSafe.map(args);
    }

    /// @param result The result of an MCP tool call.
    /// @return The text to give back to the model. Images, audio and binary content are described rather than
    /// included. A failed call is prefixed with `Error: `.
    static String renderResult(final CallToolResult result) {
        if (result == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final Content content : NullSafe.list(result.content())) {
            appendContent(sb, content);
        }
        if (sb.isEmpty() && result.structuredContent() != null) {
            sb.append(JsonUtil.writeValueAsString(result.structuredContent(), false));
        }
        final String text = sb.toString().strip();
        return Boolean.TRUE.equals(result.isError())
                ? ERROR_PREFIX + text
                : text;
    }

    /// @param result The contents of an MCP resource.
    /// @return The text of the resource. Binary content is described rather than included.
    static String renderResource(final ReadResourceResult result) {
        if (result == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final ResourceContents contents : NullSafe.list(result.contents())) {
            appendResourceContents(sb, contents);
        }
        return sb.toString().strip();
    }

    /// @param resources The resources listed by a server.
    /// @return A short description of each resource, one per line.
    static String renderResourceList(final List<Resource> resources) {
        if (NullSafe.isEmptyCollection(resources)) {
            return "No resources";
        }
        final StringBuilder sb = new StringBuilder();
        for (final Resource resource : resources) {
            sb.append("- ").append(resource.uri());
            final String name = NullSafe.nonBlankStringElse(resource.title(), resource.name());
            if (NullSafe.isNonBlankString(name)) {
                sb.append(" (").append(name).append(")");
            }
            if (NullSafe.isNonBlankString(resource.description())) {
                sb.append(": ").append(resource.description().strip());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }

    /// @param resources The resources listed by a server.
    /// @return The resources keyed by URI, in the order they were listed.
    static Map<String, Resource> byUri(final List<Resource> resources) {
        final Map<String, Resource> map = new LinkedHashMap<>();
        for (final Resource resource : NullSafe.list(resources)) {
            map.put(resource.uri(), resource);
        }
        return map;
    }

    private static String describe(final Tool tool) {
        if (NullSafe.isNonBlankString(tool.description())) {
            return tool.description();
        } else if (NullSafe.isNonBlankString(tool.title())) {
            return tool.title();
        } else {
            return tool.name();
        }
    }

    private static void appendContent(final StringBuilder sb, final Content content) {
        separate(sb);
        switch (content) {
            case final TextContent textContent -> sb.append(NullSafe.string(textContent.text()));
            case final EmbeddedResource embeddedResource -> appendResourceContents(sb, embeddedResource.resource());
            case final ResourceLink link -> sb
                    .append("Resource link: ")
                    .append(NullSafe.nonBlankStringElse(link.title(), NullSafe.string(link.name())))
                    .append(" ")
                    .append(link.uri());
            case final ImageContent image -> sb
                    .append("[Image (")
                    .append(NullSafe.string(image.mimeType()))
                    .append(") not shown]");
            case final AudioContent audio -> sb
                    .append("[Audio (")
                    .append(NullSafe.string(audio.mimeType()))
                    .append(") not shown]");
            default -> sb
                    .append("[")
                    .append(content.getClass().getSimpleName())
                    .append(" not shown]");
        }
    }

    private static void appendResourceContents(final StringBuilder sb, final ResourceContents contents) {
        separate(sb);
        if (contents instanceof final TextResourceContents text) {
            sb.append(NullSafe.string(text.text()));
        } else if (contents instanceof final BlobResourceContents blob) {
            sb.append("[Binary resource ")
                    .append(blob.uri())
                    .append(" (")
                    .append(NullSafe.string(blob.mimeType()))
                    .append(") not shown]");
        } else if (contents != null) {
            sb.append("[Resource ").append(contents.uri()).append(" not shown]");
        }
    }

    private static void separate(final StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
    }
}
