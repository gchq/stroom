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
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Describes an MCP tool from what its server says about it: what it does (read-only, can modify, destructive,
/// reaches outside Stroom), and documentation as Markdown (its title and description, the tool policy that
/// applies to it, and its parameters).
final class McpToolDocs {

    private McpToolDocs() {
        // Static methods only.
    }

    /// @param tool   The tool, as its server gives it.
    /// @param policy The tool policy that applies to it, or null if none does.
    /// @return The tool's documentation, as Markdown, without its name, which is shown with its classifications.
    static String markdown(final Tool tool, final McpToolPolicy policy) {
        final StringBuilder sb = new StringBuilder();
        final String title = NullSafe.isNonBlankString(tool.title())
                ? tool.title()
                : NullSafe.get(tool.annotations(), ToolAnnotations::title);
        if (NullSafe.isNonBlankString(title) && !title.equals(tool.name())) {
            sb.append("**").append(escape(title.strip())).append("**\n\n");
        }
        if (NullSafe.isNonBlankString(tool.description())) {
            sb.append(tool.description().strip()).append("\n\n");
        }
        sb.append("**Policy:** ").append(policyText(policy)).append("\n\n");
        appendParameters(sb, tool.inputSchema());
        return sb.toString().strip() + "\n";
    }

    /// @param tool The tool, as its server gives it.
    /// @return What its server says it does, as the chat classifies tools: "read-only" or "can modify", then
    /// "destructive" and "reaches outside Stroom" if they apply.
    static List<String> classifications(final Tool tool) {
        final ToolAnnotations annotations = tool.annotations();
        final List<String> classifications = new ArrayList<>();
        classifications.add(annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint())
                ? "read-only"
                : "can modify");
        if (annotations != null && Boolean.TRUE.equals(annotations.destructiveHint())) {
            classifications.add("destructive");
        }
        if (annotations != null && Boolean.TRUE.equals(annotations.openWorldHint())) {
            classifications.add("reaches outside Stroom");
        }
        return classifications;
    }

    private static String policyText(final McpToolPolicy policy) {
        if (policy == null) {
            return "none matches, so on a trusted server it runs without asking unless it is destructive or "
                   + "reaches outside Stroom (or, if it can modify data, once the chat has read an untrusted "
                   + "server's output); on an untrusted server it always asks";
        }
        return switch (policy) {
            case ALLOW -> "Allow (runs without asking)";
            case ASK_ALWAYS -> "Ask every time";
            case DENY -> "Deny (never offered to the model)";
        };
    }

    private static void appendParameters(final StringBuilder sb, final Map<String, Object> schema) {
        final Map<?, ?> properties = schema != null && schema.get("properties") instanceof final Map<?, ?> map
                ? map
                : Map.of();
        if (properties.isEmpty()) {
            sb.append("### Parameters\n\nNone.\n");
            return;
        }
        final Collection<?> required = schema.get("required") instanceof final Collection<?> list
                ? list
                : List.of();
        sb.append("### Parameters\n\n| Name | Type | Required | Description |\n| --- | --- | --- | --- |\n");
        properties.forEach((name, value) -> {
            final Map<?, ?> property = value instanceof final Map<?, ?> map
                    ? map
                    : Map.of();
            sb.append("| `").append(escapeCell(String.valueOf(name))).append("` | ")
                    .append(escapeCell(type(property))).append(" | ")
                    .append(required.contains(name)
                            ? "yes"
                            : "")
                    .append(" | ")
                    .append(escapeCell(description(property)))
                    .append(" |\n");
        });
    }

    /// @return The property's type, e.g. "string", "array of string", or "string (one of: a, b)".
    static String type(final Map<?, ?> property) {
        final Object type = property.get("type");
        String text = type instanceof final Collection<?> types
                ? types.stream().map(String::valueOf).collect(Collectors.joining(" or "))
                : NullSafe.nonBlankStringElse(type == null
                        ? null
                        : String.valueOf(type), "any");
        if ("array".equals(text) && property.get("items") instanceof final Map<?, ?> items) {
            text = "array of " + type(items);
        }
        if (property.get("enum") instanceof final Collection<?> values && !values.isEmpty()) {
            text += " (one of: " + values.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")";
        }
        return text;
    }

    private static String description(final Map<?, ?> property) {
        final Object description = property.get("description");
        final Object defaultValue = property.get("default");
        String text = description == null
                ? ""
                : String.valueOf(description).strip();
        if (defaultValue != null) {
            text += (text.isEmpty()
                    ? ""
                    : " ") + "Default: " + defaultValue + ".";
        }
        return text;
    }

    private static String escape(final String text) {
        return text.replaceAll("([\\\\`*_\\[\\]<>#|])", "\\\\$1");
    }

    private static String escapeCell(final String text) {
        return text.replace("|", "\\|").replaceAll("\\s+", " ");
    }
}
