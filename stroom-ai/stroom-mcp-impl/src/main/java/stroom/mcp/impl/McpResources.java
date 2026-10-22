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

import stroom.ai.impl.tools.AgentKnowledge;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListResourceTemplatesResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/// The MCP resources Stroom serves on `/mcp`, as the Stroom MCP server registered them: the working guides
/// (`stroom://guide/<name>`, and their index, `stroom://guides`) and the field convention profiles
/// (`stroom://conventions/<name>`), from [AgentKnowledge]. Each guide and profile is listed as a resource of its
/// own, as well as by the two templates, for clients that don't read templates.
class McpResources {

    static final String GUIDES_URI = "stroom://guides";
    static final String GUIDE_PREFIX = "stroom://guide/";
    static final String CONVENTION_PREFIX = "stroom://conventions/";
    static final String GUIDE_TEMPLATE = GUIDE_PREFIX + "{name}";
    static final String CONVENTION_TEMPLATE = CONVENTION_PREFIX + "{name}";
    static final String MARKDOWN = "text/markdown";
    static final String YAML = "text/yaml";
    /// The guide template's description, which names every guide.
    static final String GUIDE_DESCRIPTION = "Working guides: event-logging, xslt, data-splitter, json-input, "
                                            + "reference-data, indexing, cef, documentation, agent-instructions.";
    static final String GUIDES_DESCRIPTION = "Index of the working guides.";
    static final String CONVENTION_DESCRIPTION = "A field convention profile.";

    private final AgentKnowledge knowledge;

    @Inject
    McpResources(final AgentKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /// @return The resources: the guides' index, each guide and each convention profile.
    ListResourcesResult list() {
        final List<Resource> resources = new ArrayList<>();
        resources.add(Resource.builder(GUIDES_URI, "guides")
                .description(GUIDES_DESCRIPTION)
                .mimeType(MARKDOWN)
                .build());
        for (final String name : knowledge.guideNames()) {
            resources.add(Resource.builder(GUIDE_PREFIX + name, "guide/" + name)
                    .title(knowledge.guideTitle(name).orElse(name))
                    .description("A working guide: " + knowledge.guideTitle(name).orElse(name) + ".")
                    .mimeType(MARKDOWN)
                    .build());
        }
        for (final String name : knowledge.conventionNames()) {
            resources.add(Resource.builder(CONVENTION_PREFIX + name, "conventions/" + name)
                    .description(CONVENTION_DESCRIPTION)
                    .mimeType(YAML)
                    .build());
        }
        return new ListResourcesResult(resources, null);
    }

    /// @return The resource templates: a guide by name, and a convention profile by name.
    ListResourceTemplatesResult templates() {
        return new ListResourceTemplatesResult(List.of(
                ResourceTemplate.builder(GUIDE_TEMPLATE, "guide")
                        .description(GUIDE_DESCRIPTION)
                        .mimeType(MARKDOWN)
                        .build(),
                ResourceTemplate.builder(CONVENTION_TEMPLATE, "convention")
                        .description(CONVENTION_DESCRIPTION)
                        .mimeType(YAML)
                        .build()), null);
    }

    /// @param uri A resource's URI, e.g. `stroom://guide/xslt`.
    /// @return Its text.
    /// @throws McpError if there is no such resource, saying what there is.
    ReadResourceResult read(final String uri) {
        if (GUIDES_URI.equals(uri)) {
            return text(uri, MARKDOWN, guidesIndex());
        }
        if (uri != null && uri.startsWith(GUIDE_PREFIX)) {
            final String name = uri.substring(GUIDE_PREFIX.length());
            return knowledge.guide(name)
                    .map(text -> text(uri, MARKDOWN, text))
                    .orElseThrow(() -> notFound("No guide '" + name + "'. Guides: "
                                                + String.join(", ", knowledge.guideNames())));
        }
        if (uri != null && uri.startsWith(CONVENTION_PREFIX)) {
            final String name = uri.substring(CONVENTION_PREFIX.length());
            return knowledge.convention(name)
                    .map(text -> text(uri, YAML, text))
                    .orElseThrow(() -> notFound("No convention '" + name + "'"));
        }
        throw notFound("Unknown resource: " + uri);
    }

    /// @return The guides' index: each guide's URI and title.
    String guidesIndex() {
        final List<String> lines = new ArrayList<>(List.of("# Guides", ""));
        for (final String name : knowledge.guideNames()) {
            lines.add("- `" + GUIDE_PREFIX + name + "`: " + knowledge.guideTitle(name).orElse(""));
        }
        return String.join("\n", lines);
    }

    private static ReadResourceResult text(final String uri, final String mimeType, final String text) {
        return new ReadResourceResult(List.of(new TextResourceContents(uri, mimeType, text)));
    }

    private static McpError notFound(final String message) {
        return McpError.builder(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                .message(message)
                .build();
    }
}
