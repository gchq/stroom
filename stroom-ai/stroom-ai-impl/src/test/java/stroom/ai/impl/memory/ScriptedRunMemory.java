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

package stroom.ai.impl.memory;

import stroom.ai.api.AiService;
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiMemoryDao;
import stroom.ai.impl.ConsentGates;
import stroom.ai.shared.AiMemory;
import stroom.security.api.SecurityContext;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The real memory service for the scripted chat runs (`TestScriptedChatRuns`), made from outside this package,
/// where its constructor is not visible, over memory kept in a list rather than the database.
public final class ScriptedRunMemory {

    private ScriptedRunMemory() {
        // Static only.
    }

    /// @param aiService       Where the chat's messages are.
    /// @param runManager      The chat's runs, which say whether a change needs the user's approval.
    /// @param securityContext The user.
    /// @param aiDao           Where approval requests are answered.
    /// @return The memory service, with no memory to begin with.
    public static AiMemoryService memoryService(final AiService aiService,
                                                final AiChatRunManager runManager,
                                                final SecurityContext securityContext,
                                                final AiDao aiDao) {
        return new AiMemoryService(memoryDao(), aiService, ConsentGates.chat(null, aiService, runManager),
                securityContext, aiDao);
    }

    // Memory in a list, versioned as the database versions it.
    private static AiMemoryDao memoryDao() {
        final List<AiMemory> memories = new ArrayList<>();
        final AiMemoryDao memoryDao = Mockito.mock(AiMemoryDao.class);
        Mockito.when(memoryDao.list(Mockito.anyString(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> memories.stream()
                        .filter(memory -> memory.getScope() == invocation.getArgument(1)
                                          && memory.getScopeKey().equals(invocation.getArgument(2)))
                        .toList());
        Mockito.when(memoryDao.get(Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> memories.stream()
                        .filter(memory -> memory.getScope() == invocation.getArgument(1)
                                          && memory.getScopeKey().equals(invocation.getArgument(2))
                                          && memory.getName().equals(invocation.getArgument(3)))
                        .findFirst());
        Mockito.when(memoryDao.create(Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any())).thenAnswer(invocation -> {
                    final AiMemory memory = new AiMemory(memories.size() + 1, 1, invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), 0L,
                            invocation.getArgument(5));
                    memories.add(memory);
                    return Optional.of(memory);
                });
        return memoryDao;
    }
}
