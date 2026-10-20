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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The registry of {@link StroomTool}s available to the chat agent, as langchain4j tools (see
 * {@link LangChain4jTools}). New tools are picked up automatically once bound in {@link BuiltInToolsModule}.
 */
@Singleton
public class BuiltInTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuiltInTools.class);

    private final Map<ToolSpecification, ToolExecutor> toolMap;
    private final Set<String> readOnlyToolNames;
    private final Set<String> trustedToolNames;
    private final Set<String> boundedToolNames;
    private final Map<String, ToolPermissions> permissions;
    private final Map<String, StroomTool> toolsByName;

    /// @param tools    The tools, as bound in [BuiltInToolsModule].
    /// @param contexts Makes the context of a call in a chat.
    @Inject
    BuiltInTools(final Set<StroomTool> tools, final ChatToolCallContexts contexts) {
        final Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        final Set<String> readOnly = new HashSet<>();
        final Set<String> trusted = new HashSet<>();
        final Set<String> bounded = new HashSet<>();
        final Map<String, ToolPermissions> permissionsByName = new HashMap<>();
        final Map<String, StroomTool> byName = new HashMap<>();
        for (final StroomTool tool : tools) {
            final ToolSpecification specification = LangChain4jTools.specification(tool.definition());
            if (tool.isReadOnly()) {
                readOnly.add(specification.name());
            }
            if (!tool.readsUntrustedContent()) {
                trusted.add(specification.name());
            }
            if (tool.boundsItsOutput()) {
                bounded.add(specification.name());
            }
            permissionsByName.put(specification.name(), tool.permissions());
            byName.put(specification.name(), tool);
            // langchain4j gives the executor the chat's id as its memory id.
            map.put(specification, LangChain4jTools.executor(tool, contexts::forMemoryId));
        }
        toolMap = Map.copyOf(map);
        readOnlyToolNames = Set.copyOf(readOnly);
        trustedToolNames = Set.copyOf(trusted);
        boundedToolNames = Set.copyOf(bounded);
        permissions = Map.copyOf(permissionsByName);
        toolsByName = Map.copyOf(byName);
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
