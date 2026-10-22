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

import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.BuiltInToolsModule;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolSummary;
import stroom.mcp.impl.tools.PortedToolsModule;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.shared.UserRef;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Makes every one of the AI chat's and MCP endpoint's tools from the REAL modules, in Stroom's injector, as Stroom
/// makes them the first time a chat or MCP client asks for them. A tool that can't be made (e.g. a circle of
/// classes Guice can't proxy: "Tried proxying BuildPlan to support a circular dependency, but it is not an
/// interface", seen once in use) fails here, where unit tests that make the tools on fakes don't notice.
class TestRealAiToolInjection extends AbstractAiIntegrationTest {

    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    @Inject
    private Injector injector;

    @Test
    void everyBoundToolIsMadeWithAValidDefinition() {
        final Set<StroomTool> tools = injector.getInstance(Key.get(new TypeLiteral<Set<StroomTool>>() {
        }));

        // Every tool the two modules bind, and no other.
        assertThat(tools.stream().map(tool -> tool.getClass().getName()).toList())
                .containsExactlyInAnyOrderElementsOf(boundToolClasses());
        for (final StroomTool tool : tools) {
            assertValidDefinition(tool);
        }
    }

    @Test
    void theChatsRegistryHasEveryTool() {
        final BuiltInTools builtInTools = injector.getInstance(BuiltInTools.class);

        final List<String> names = builtInTools.tools().stream()
                .map(tool -> tool.definition().name())
                .toList();
        assertThat(names)
                .hasSameSizeAs(boundToolClasses())
                .doesNotHaveDuplicates()
                .contains("stream_meta", "folder_contents", "read_document", "start_onboarding", "create_feed",
                        "upload_sample", "draft_translation_mapping", "build_translation_xslt", "create_pipeline",
                        "step_sample", "check_events", "create_processor_filter", "wait_for_processing",
                        "write_documentation", "promote_build");
        assertThat(builtInTools.toolMap()).hasSameSizeAs(names);
        assertThat(builtInTools.summaries())
                .extracting(AiToolSummary::getName)
                .containsExactlyInAnyOrderElementsOf(names);
        // The build tools' instructions come with them, from the knowledge McpModule bundles.
        assertThat(builtInTools.buildInstructions(Set.copyOf(names))).isPresent();
    }

    @Test
    void theMcpEndpointListsTheToolsForAnAdministrator() {
        // The server is made as /mcp makes it, with its tools, forms, approvals and workspaces.
        assertThat(injector.getInstance(StroomMcpServer.class).sessionCount()).isZero();
        final McpTools mcpTools = injector.getInstance(McpTools.class);
        final UserRef admin = administrator();
        toolPolicy("Everything", AiToolAccess.READ_WRITE, admin);
        final McpSession session = new McpSession("session-1", admin, new Implementation("test", "1"),
                ClientCapabilities.builder().build());

        final List<Tool> listed = asUser(admin, () -> mcpTools.list(session));

        final BuiltInTools builtInTools = injector.getInstance(BuiltInTools.class);
        final List<String> notChatOnly = builtInTools.tools().stream()
                .filter(tool -> !tool.isChatOnly())
                .map(tool -> tool.definition().name())
                .toList();
        assertThat(listed).extracting(Tool::name).containsExactlyInAnyOrderElementsOf(notChatOnly);
        for (final Tool tool : listed) {
            assertThat(tool.inputSchema()).as(tool.name()).containsEntry("type", "object");
            assertThat(tool.description()).as(tool.name()).isNotBlank();
        }
    }

    private static void assertValidDefinition(final StroomTool tool) {
        final ToolDefinition definition = tool.definition();
        final String name = definition.name();
        assertThat(name).as(tool.getClass().getName()).matches(TOOL_NAME);
        assertThat(definition.description()).as(name).isNotBlank();
        assertThat(tool.category()).as(name).isNotNull();
        assertThat(tool.permissions()).as(name).isNotNull();
        // The schema is an object whose required arguments are among its properties.
        final Map<String, Object> schema = McpToolSchemas.inputSchema(definition.general().parameters());
        assertThat(schema).as(name).containsEntry("type", "object");
        if (definition.parameters() != null) {
            assertThat(definition.parameters().properties().keySet())
                    .as(name)
                    .containsAll(definition.parameters().required() == null
                            ? List.of()
                            : definition.parameters().required());
        }
    }

    // The classes BuiltInToolsModule and PortedToolsModule add to the StroomTool multibinder.
    private static List<String> boundToolClasses() {
        final List<String> classes = new ArrayList<>();
        for (final Element element : Elements.getElements(new BuiltInToolsModule(), new PortedToolsModule())) {
            if (element instanceof final LinkedKeyBinding<?> binding
                && StroomTool.class.equals(binding.getKey().getTypeLiteral().getRawType())) {
                classes.add(binding.getLinkedKey().getTypeLiteral().getRawType().getName());
            }
        }
        return classes;
    }
}
