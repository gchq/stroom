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

package stroom.ai.impl.tools;

import stroom.ai.api.AiService;
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.impl.QueryService;
import stroom.security.api.SecurityContext;

import org.mockito.Mockito;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/// The real built-in tools that the scripted chat runs (`TestScriptedChatRuns`) use, made from outside this
/// package, where their constructors are not visible: read_turn, ask_user, memory and propose_query, registered
/// as Stroom registers them, through [BuiltInTools].
public final class ScriptedRunTools {

    private ScriptedRunTools() {
        // Static only.
    }

    /// @param aiService       Where the chat's messages are.
    /// @param elicitations    Shows ask_user's questions to the user.
    /// @param runManager      The chat's runs.
    /// @param memoryService   The agent's memory.
    /// @param queryService    Checks the queries propose_query is given.
    /// @param registry        The data sources.
    /// @param securityContext Says which data sources the user can use.
    /// @param moreTools       Further tools to register with them (e.g. stand-ins, see [StandInTools]).
    /// @return The tools, registered.
    public static BuiltInTools builtInTools(final AiService aiService,
                                            final McpElicitations elicitations,
                                            final AiChatRunManager runManager,
                                            final AiMemoryService memoryService,
                                            final QueryService queryService,
                                            final DataSourceProviderRegistry registry,
                                            final SecurityContext securityContext,
                                            final StroomTool... moreTools) {
        final DataSourceAccess dataSourceAccess = new DataSourceAccess(() -> registry, securityContext);
        final ChatWorkspace workspace = workspace();
        final Set<StroomTool> tools = new HashSet<>(Set.of(
                new ReadTurnTool(aiService),
                new AskUserTool(),
                new MemoryTool(memoryService),
                new ProposeQueryTool(workspace, dataSourceAccess, () -> queryService)));
        tools.addAll(Arrays.asList(moreTools));
        return new BuiltInTools(tools, new ChatToolCallContexts(workspace, () -> elicitations, () -> runManager));
    }

    // A workspace that runs each call straight away, with the default limits: propose_query only needs its limits.
    @SuppressWarnings("unchecked")
    private static ChatWorkspace workspace() {
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        return workspace;
    }
}
