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

package stroom.mcp.impl;

import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.BooleanSchema;
import stroom.ai.impl.tools.ToolSchema.EnumSchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Describes one of Stroom's own tools as an MCP client sees it: its general description and parameters (the
/// chat-only parts left out, see [ToolDefinition#general()]), as JSON Schema, with annotations saying whether it
/// only reads, may change or delete things, and may return content someone else wrote.
final class McpToolSchemas {

    private McpToolSchemas() {
        // Static only.
    }

    /// @param tool One of Stroom's tools.
    /// @return It, as an MCP tool.
    static Tool toMcpTool(final StroomTool tool) {
        final ToolDefinition general = tool.definition().general();
        final boolean readOnly = tool.isReadOnly();
        return Tool.builder()
                .name(general.name())
                .description(general.description())
                .inputSchema(inputSchema(general.parameters()))
                .annotations(new ToolAnnotations(
                        null,
                        readOnly,
                        // A tool that changes things may also overwrite or delete them.
                        !readOnly && !tool.hasReadOnlyCalls(),
                        null,
                        tool.readsUntrustedContent(),
                        null))
                .build();
    }

    /// @param parameters A tool's parameters, or null if it takes none.
    /// @return Their JSON Schema: an object, with no properties if it takes none.
    static Map<String, Object> inputSchema(final ObjectSchema parameters) {
        if (parameters == null) {
            return Map.of("type", "object", "properties", Map.of());
        }
        return toJsonSchema(parameters);
    }

    /// @param schema A value's schema.
    /// @return It, as JSON Schema.
    static Map<String, Object> toJsonSchema(final ToolSchema schema) {
        final Map<String, Object> json = new LinkedHashMap<>();
        switch (schema) {
            case final ObjectSchema object -> {
                json.put("type", "object");
                final Map<String, Object> properties = new LinkedHashMap<>();
                object.properties().forEach((name, property) -> properties.put(name, toJsonSchema(property)));
                json.put("properties", properties);
                final List<String> required = object.required();
                if (required != null && !required.isEmpty()) {
                    json.put("required", required);
                }
            }
            case final ArraySchema array -> {
                json.put("type", "array");
                if (array.items() != null) {
                    json.put("items", toJsonSchema(array.items()));
                }
            }
            case final StringSchema ignored -> json.put("type", "string");
            case final IntegerSchema ignored -> json.put("type", "integer");
            case final BooleanSchema ignored -> json.put("type", "boolean");
            case final EnumSchema enumSchema -> {
                json.put("type", "string");
                json.put("enum", enumSchema.values());
            }
        }
        final String description = schema.description();
        if (description != null && !description.isBlank()) {
            json.put("description", description);
        }
        return json;
    }
}
