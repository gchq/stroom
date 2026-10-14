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

    @Inject
    BuiltInTools(final Set<BuiltInTool> tools) {
        final Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        final Set<String> readOnly = new HashSet<>();
        for (final BuiltInTool tool : tools) {
            final ToolSpecification specification = tool.specification();
            if (tool.isReadOnly()) {
                readOnly.add(specification.name());
            }
            map.put(specification, (request, memoryId) -> {
                try {
                    return NullSafe.string(tool.execute(request));
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Built-in tool '" + specification.name() + "' failed", e);
                    return "Error running built-in tool '" + specification.name() + "': "
                           + NullSafe.nonBlankStringElse(e.getMessage(), e.toString());
                }
            });
        }
        toolMap = Map.copyOf(map);
        readOnlyToolNames = Set.copyOf(readOnly);
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

    public boolean isEmpty() {
        return toolMap.isEmpty();
    }
}
