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
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The registry of {@link BuiltInTool}s available to the chat agent. New tools are picked up
 * automatically once bound in {@code AiModule}.
 */
@Singleton
public class BuiltInTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuiltInTools.class);

    private final Map<ToolSpecification, ToolExecutor> toolMap;
    private final Set<String> readOnlyToolNames;
    private final Set<String> trustedToolNames;
    private final Set<String> boundedToolNames;
    private final Set<String> alwaysAskToolNames;

    @Inject
    BuiltInTools(final Set<BuiltInTool> tools) {
        final Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        final Set<String> readOnly = new HashSet<>();
        final Set<String> trusted = new HashSet<>();
        final Set<String> bounded = new HashSet<>();
        final Set<String> alwaysAsk = new HashSet<>();
        for (final BuiltInTool tool : tools) {
            final ToolSpecification specification = tool.specification();
            if (tool.isReadOnly()) {
                readOnly.add(specification.name());
            }
            if (!tool.readsUntrustedContent()) {
                trusted.add(specification.name());
            }
            if (tool.boundsItsOutput()) {
                bounded.add(specification.name());
            }
            if (tool.alwaysAsks()) {
                alwaysAsk.add(specification.name());
            }
            map.put(specification, (request, memoryId) -> {
                try {
                    return NullSafe.string(memoryId instanceof final Integer chatId
                            ? tool.execute(request, chatId)
                            : tool.execute(request));
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Built-in tool '" + specification.name() + "' failed", e);
                    return "Error running built-in tool '" + specification.name() + "': "
                           + NullSafe.nonBlankStringElse(e.getMessage(), e.toString());
                }
            });
        }
        toolMap = Map.copyOf(map);
        readOnlyToolNames = Set.copyOf(readOnly);
        trustedToolNames = Set.copyOf(trusted);
        boundedToolNames = Set.copyOf(bounded);
        alwaysAskToolNames = Set.copyOf(alwaysAsk);
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

    /**
     * @param toolName A tool name.
     * @return True if it is a built-in tool the user must approve every call of.
     */
    public boolean alwaysAsks(final String toolName) {
        return alwaysAskToolNames.contains(toolName);
    }

    public boolean isEmpty() {
        return toolMap.isEmpty();
    }
}
