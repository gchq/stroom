/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolSummary;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The registry of {@link StroomTool}s available to the chat agent, as langchain4j tools (see
 * {@link LangChain4jTools}). New tools are picked up automatically once bound in {@link BuiltInToolsModule}.
 */
@Singleton
public class BuiltInTools {

    /// The tool that starts a build, whose being offered brings the build tools' instructions with it.
    static final String BUILD_ENTRY_TOOL = "start_onboarding";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuiltInTools.class);

    private final Map<ToolSpecification, ToolExecutor> toolMap;
    private final Set<String> readOnlyToolNames;
    private final Set<String> readOnlyCallToolNames;
    private final Set<String> trustedToolNames;
    private final Set<String> boundedToolNames;
    private final Map<String, ToolPermissions> permissions;
    private final Map<String, StroomTool> toolsByName;
    private final Map<String, AiToolCategory> categories;
    // The knowledge bundled for agents, set by injection where a module bundles it.
    private Optional<AgentKnowledge> knowledge = Optional.empty();

    /// @param tools    The tools, as bound in [BuiltInToolsModule].
    /// @param contexts Makes the context of a call in a chat.
    BuiltInTools(final Set<StroomTool> tools, final ChatToolCallContexts contexts) {
        this(tools, contexts, new ToolCallGuard());
    }

    /// @param tools    The tools, as bound in [BuiltInToolsModule].
    /// @param contexts Makes the context of a call in a chat.
    /// @param guard    What every call goes through (see [ToolCallGuard]).
    @Inject
    BuiltInTools(final Set<StroomTool> tools, final ChatToolCallContexts contexts, final ToolCallGuard guard) {
        final Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        final Set<String> readOnly = new HashSet<>();
        final Set<String> readOnlyCalls = new HashSet<>();
        final Set<String> trusted = new HashSet<>();
        final Set<String> bounded = new HashSet<>();
        final Map<String, ToolPermissions> permissionsByName = new HashMap<>();
        final Map<String, StroomTool> byName = new HashMap<>();
        final Map<String, AiToolCategory> categoriesByName = new HashMap<>();
        for (final StroomTool tool : tools) {
            final ToolSpecification specification = LangChain4jTools.specification(tool.definition());
            if (tool.isReadOnly()) {
                readOnly.add(specification.name());
            }
            if (tool.hasReadOnlyCalls()) {
                readOnlyCalls.add(specification.name());
            }
            if (!tool.readsUntrustedContent()) {
                trusted.add(specification.name());
            }
            if (tool.boundsItsOutput()) {
                bounded.add(specification.name());
            }
            permissionsByName.put(specification.name(), tool.permissions());
            byName.put(specification.name(), tool);
            categoriesByName.put(specification.name(), Objects.requireNonNull(tool.category(),
                    () -> "Tool '" + specification.name() + "' has no category"));
            // langchain4j gives the executor the chat's id as its memory id.
            map.put(specification, LangChain4jTools.executor(tool, contexts::forMemoryId, guard));
        }
        toolMap = Map.copyOf(map);
        readOnlyToolNames = Set.copyOf(readOnly);
        readOnlyCallToolNames = Set.copyOf(readOnlyCalls);
        trustedToolNames = Set.copyOf(trusted);
        boundedToolNames = Set.copyOf(bounded);
        permissions = Map.copyOf(permissionsByName);
        toolsByName = Map.copyOf(byName);
        categories = Map.copyOf(categoriesByName);
        LOGGER.debug(() -> "Registered " + toolMap.size() + " built-in tool(s): "
                           + toolMap.keySet().stream().map(ToolSpecification::name).sorted().toList());
    }

    /**
     * @return the built-in tools as an immutable {@code specification -> executor} map, ready to merge
     * with any MCP tools.
     */
    public Map<ToolSpecification, ToolExecutor> toolMap() {
        return toolMap;
    }

    /// @param knowledge The knowledge bundled for agents, if a module bundles it.
    @Inject
    void setKnowledge(final Optional<AgentKnowledge> knowledge) {
        this.knowledge = Objects.requireNonNullElse(knowledge, Optional.empty());
    }

    /// The instructions for the tools that build Stroom content, for a chat offered them: the Stroom MCP server
    /// gave them to every client when it connected, and the build tools' replies assume them (the order of work,
    /// `next`, how to ask the user).
    ///
    /// @param offered The names of the tools the chat is offered.
    /// @return The instructions under a heading, or empty if the chat isn't offered the build tools (their entry
    /// point, start_onboarding) or none are bundled.
    public Optional<String> buildInstructions(final Set<String> offered) {
        if (!offered.contains(BUILD_ENTRY_TOOL)) {
            return Optional.empty();
        }
        return knowledge.flatMap(AgentKnowledge::buildInstructions)
                .filter(text -> !text.isBlank())
                .map(text -> "## Instructions for Stroom's build tools\n\n" + text.strip());
    }

    /// @return Every built-in tool, by name.
    public List<StroomTool> tools() {
        return toolsByName.values().stream()
                .sorted(Comparator.comparing(tool -> tool.definition().name()))
                .toList();
    }

    /// @param toolName A tool name.
    /// @return The built-in tool, if there is one by that name.
    public Optional<StroomTool> tool(final String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    /// @param toolName A tool name.
    /// @return True if it is a built-in tool some of whose calls (or all) cannot change anything.
    public boolean hasReadOnlyCalls(final String toolName) {
        return readOnlyCallToolNames.contains(toolName);
    }

    /// @return Every built-in tool, as an administrator choosing what a tool policy grants sees it, by name: what
    /// it does in a line ([ToolSummaries]), not the description its agent is given.
    public List<AiToolSummary> summaries() {
        return toolsByName.values().stream()
                .map(tool -> {
                    final ToolDefinition general = tool.definition().general();
                    return new AiToolSummary(general.name(), tool.category(), tool.isReadOnly(),
                            !tool.isReadOnly() && tool.hasReadOnlyCalls(),
                            ToolSummaries.of(general.name()).orElseGet(general::description));
                })
                .sorted(Comparator.comparing(AiToolSummary::getName))
                .toList();
    }

    /// @param toolName A tool name.
    /// @return The built-in tool's category, or empty if it isn't a built-in tool.
    public Optional<AiToolCategory> category(final String toolName) {
        return Optional.ofNullable(categories.get(toolName));
    }

    /**
     * @param toolName A tool name.
     * @return True if it is a built-in tool that cannot change anything.
     */
    public boolean isReadOnly(final String toolName) {
        return readOnlyToolNames.contains(toolName);
    }

    /// @param toolName  A tool name.
    /// @param arguments The call's arguments as JSON, or null if not known.
    /// @return True if it is a built-in tool and this call of it cannot change anything (see
    /// [StroomTool#isReadOnly(ToolCall)]). False for anything else.
    public boolean isReadOnlyCall(final String toolName, final String arguments) {
        final StroomTool tool = toolsByName.get(toolName);
        return tool != null && tool.isReadOnly(new ToolCall(null, toolName, arguments));
    }

    /// @param toolName  A tool name.
    /// @param arguments The call's arguments as JSON, or null if not known (then a tool with some calls that change
    ///                  something is judged as if this call changes something).
    /// @return True if it is a built-in tool and this call changes something, the tool doesn't confirm the change
    /// itself ([StroomTool#confirmsItself()]) and its changes need approval once the chat has read untrusted
    /// content ([StroomTool#changesNeedApprovalAfterUntrustedContent()]). False for anything else.
    public boolean changeNeedsApprovalAfterUntrustedContent(final String toolName, final String arguments) {
        final StroomTool tool = toolsByName.get(toolName);
        return tool != null
               && !tool.isReadOnly(new ToolCall(null, toolName, arguments))
               && !tool.confirmsItself()
               && tool.changesNeedApprovalAfterUntrustedContent();
    }

    /**
     * @param toolName A tool name.
     * @return True if what the tool returns could hold instructions planted by someone other than the user. True
     * for any tool that is not a built-in tool.
     */
    public boolean readsUntrustedContent(final String toolName) {
        return !trustedToolNames.contains(toolName);
    }

    /**
     * @param toolName A tool name.
     * @return True if it is a built-in tool that limits how much it returns itself.
     */
    public boolean boundsItsOutput(final String toolName) {
        return boundedToolNames.contains(toolName);
    }

    /// @param toolName A tool name.
    /// @return The permissions the built-in tool needs, as it declares them (see [StroomTool#permissions()]), or
    /// [ToolPermissions#NONE] if it is not a built-in tool.
    public ToolPermissions permissions(final String toolName) {
        return permissions.getOrDefault(toolName, ToolPermissions.NONE);
    }

    public boolean isEmpty() {
        return toolMap.isEmpty();
    }
}
