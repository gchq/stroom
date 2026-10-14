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

import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/// Starts "Ask Stroom AI" agent runs. A run answers one question in a chat. It is recorded in `ai_chat_run` and
/// worked on in the background, as the user who asked, so the request that started it returns straight away
/// and the answer carries on being worked out if the user refreshes the page or closes the tab. The client
/// follows its progress by polling.
@Singleton
public class AiChatRunManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiChatRunManager.class);

    static final String TASK_NAME = "Ask Stroom AI";

    private static final List<AiChatRunStatus> ACTIVE = Arrays.stream(AiChatRunStatus.values())
            .filter(AiChatRunStatus::isActive)
            .toList();

    private final AiChatRunDao runDao;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;

    @Inject
    AiChatRunManager(final AiChatRunDao runDao,
                     final TaskContextFactory taskContextFactory,
                     final ExecutorProvider executorProvider,
                     final NodeInfo nodeInfo,
                     final SecurityContext securityContext) {
        this.runDao = runDao;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
    }

    /// Starts a run in a chat, unless the chat already has one.
    ///
    /// @param chatId The chat.
    /// @param work   Answers the question. It runs in the background as the current user. When it returns the
    ///               run is done; if it throws the run has failed.
    /// @return The new run.
    /// @throws AiChatBusyException If the chat already has an active run.
    public AiChatRun start(final int chatId, final Consumer<AiChatRun> work) {
        final String userUuid = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        final AiChatRun run = runDao.createIfNoneActive(
                        chatId, userUuid, AiChatRunStatus.RUNNING, nodeInfo.getThisNodeName(), null)
                .orElseThrow(() -> new AiChatBusyException(chatId));
        LOGGER.debug(() -> "Started run " + run.getId() + " for chat " + chatId);

        final Runnable task = taskContextFactory.context(TASK_NAME, taskContext -> {
            taskContext.info(() -> "Chat " + chatId + ", run " + run.getId());
            execute(run, work);
        });
        executorProvider.get().execute(task);
        return run;
    }

    /// Runs the work and records how the run ended. Package-private for testing.
    void execute(final AiChatRun run, final Consumer<AiChatRun> work) {
        try {
            work.accept(run);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.DONE, null, null);
        } catch (final TaskTerminatedException e) {
            LOGGER.debug(() -> "Run " + run.getId() + " was terminated");
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.STOPPED,
                    AiChatRunStopReason.TERMINATED, null);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Run " + run.getId() + " for chat " + run.getChatId() + " failed", e);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.FAILED, null,
                    NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    /// Thrown when a question is asked in a chat that is still working on an earlier one.
    public static class AiChatBusyException extends RuntimeException {

        public AiChatBusyException(final int chatId) {
            super("This chat is still working on a question. Wait for it to finish or stop it first.");
            LOGGER.debug(() -> "Chat " + chatId + " is busy");
        }
    }
}
