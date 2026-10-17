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

import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// The tasks working for a chat on this node other than its run, e.g. downloading search results attached to it,
/// so that stopping the chat stops them too.
@Singleton
public class AiBackgroundTasks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiBackgroundTasks.class);

    private final Provider<TaskManager> taskManagerProvider;
    private final Map<Integer, Set<TaskContext>> tasks = new ConcurrentHashMap<>();

    @Inject
    AiBackgroundTasks(final Provider<TaskManager> taskManagerProvider) {
        this.taskManagerProvider = taskManagerProvider;
    }

    /// Records that a task has started working for a chat. Call [#ended(int, TaskContext)] when it ends, however
    /// it ends.
    ///
    /// @param chatId      The chat.
    /// @param taskContext The task's context.
    public void started(final int chatId, final TaskContext taskContext) {
        tasks.computeIfAbsent(chatId, id -> ConcurrentHashMap.newKeySet()).add(taskContext);
    }

    /// Records that a task working for a chat has ended.
    ///
    /// @param chatId      The chat.
    /// @param taskContext The task's context, as given to [#started(int, TaskContext)].
    public void ended(final int chatId, final TaskContext taskContext) {
        tasks.computeIfPresent(chatId, (id, set) -> {
            set.remove(taskContext);
            return set.isEmpty()
                    ? null
                    : set;
        });
    }

    /// Terminates the tasks working for a chat on this node. Each task records how it ended, e.g. an attachment
    /// that was downloading is marked as failed.
    ///
    /// @param chatId The chat.
    /// @return How many tasks were asked to terminate.
    public int terminate(final int chatId) {
        final List<TaskContext> working = List.copyOf(tasks.getOrDefault(chatId, Set.of()));
        for (final TaskContext taskContext : working) {
            LOGGER.debug(() -> "Terminating task " + taskContext.getTaskId() + " of chat " + chatId);
            taskManagerProvider.get().terminate(taskContext.getTaskId());
        }
        return working.size();
    }
}
