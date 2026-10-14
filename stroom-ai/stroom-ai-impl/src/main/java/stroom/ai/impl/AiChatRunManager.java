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

import stroom.ai.api.AiService;
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

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/// Starts and keeps track of "Ask Stroom AI" agent runs. A run answers one question in a chat. It is recorded in
/// `ai_chat_run` and worked on in the background, as the user who asked, so the request that started it returns
/// straight away and the answer carries on being worked out if the user refreshes the page or closes the tab.
/// The client follows its progress by polling.
///
/// While a run is being worked on, this node updates its heartbeat every [#HEARTBEAT_INTERVAL]. The same tick
/// passes on stop requests made through the database (so a stop works whichever node receives it), and marks as
/// interrupted any run, on any node, whose heartbeat is older than [#STALE_AFTER], as its node must have stopped.
@Singleton
public class AiChatRunManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiChatRunManager.class);

    static final String TASK_NAME = "Ask Stroom AI";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    static final Duration STALE_AFTER = Duration.ofSeconds(60);

    /// The statuses a run can be stopped from.
    private static final Set<AiChatRunStatus> STOPPABLE = EnumSet.of(
            AiChatRunStatus.QUEUED, AiChatRunStatus.RUNNING, AiChatRunStatus.WAITING_FOR_USER);

    /// The statuses a run can be resumed from.
    private static final Set<AiChatRunStatus> RESUMABLE = EnumSet.of(
            AiChatRunStatus.STOPPED, AiChatRunStatus.INTERRUPTED, AiChatRunStatus.FAILED);

    private static final List<AiChatRunStatus> ACTIVE = Arrays.stream(AiChatRunStatus.values())
            .filter(AiChatRunStatus::isActive)
            .toList();

    private final AiChatRunDao runDao;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final AiService aiService;

    /// The runs being worked on by this node, by chat id.
    private final Map<Integer, LocalRun> localRuns = new ConcurrentHashMap<>();
    private volatile ScheduledExecutorService heartbeatExecutor;

    @Inject
    AiChatRunManager(final AiChatRunDao runDao,
                     final TaskContextFactory taskContextFactory,
                     final ExecutorProvider executorProvider,
                     final NodeInfo nodeInfo,
                     final SecurityContext securityContext,
                     final AiService aiService) {
        this.runDao = runDao;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.aiService = aiService;
    }

    /// Starts a run in a chat, unless the chat already has one.
    ///
    /// @param chatId The chat.
    /// @param work   Answers the question. It runs in the background as the current user, and should stop
    ///               early (returning normally) when [#isStopRequested(int)] says so. When it returns the run is
    ///               done (or stopped, if a stop was asked for); if it throws the run has failed.
    /// @return The new run.
    /// @throws AiChatBusyException If the chat already has an active run.
    public AiChatRun start(final int chatId, final Consumer<AiChatRun> work) {
        final String userUuid = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        final AiChatRun run = runDao.createIfNoneActive(
                        chatId, userUuid, AiChatRunStatus.RUNNING, nodeInfo.getThisNodeName(), null)
                .orElseThrow(() -> new AiChatBusyException(chatId));
        LOGGER.debug(() -> "Started run " + run.getId() + " for chat " + chatId);
        localRuns.put(chatId, new LocalRun(run.getId(), new AtomicBoolean(false)));
        ensureHeartbeat();

        final Runnable task = taskContextFactory.context(TASK_NAME, taskContext -> {
            taskContext.info(() -> "Chat " + chatId + ", run " + run.getId());
            execute(run, work);
        });
        try {
            executorProvider.get().execute(task);
        } catch (final RuntimeException e) {
            localRuns.remove(chatId);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.FAILED, null, e.getMessage());
            throw e;
        }
        return run;
    }

    /// Runs the work and records how the run ended. Package-private for testing.
    void execute(final AiChatRun run, final Consumer<AiChatRun> work) {
        try {
            work.accept(run);
            finish(run);
        } catch (final TaskTerminatedException e) {
            LOGGER.debug(() -> "Run " + run.getId() + " was terminated");
            stopped(run, AiChatRunStopReason.TERMINATED);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Run " + run.getId() + " for chat " + run.getChatId() + " failed", e);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.FAILED, null,
                    NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        } finally {
            localRuns.remove(run.getChatId(), localRuns.get(run.getChatId()));
        }
    }

    private void finish(final AiChatRun run) {
        if (!runDao.compareAndSetStatus(run.getId(), List.of(AiChatRunStatus.RUNNING), AiChatRunStatus.DONE,
                null, null)) {
            // A stop was asked for while it worked, so it stopped early.
            stopped(run, null);
        }
    }

    /// Marks a run that has stopped working as stopped, keeping the reason it was asked to stop for, if any.
    private void stopped(final AiChatRun run, final AiChatRunStopReason defaultReason) {
        final AiChatRun current = runDao.get(run.getId()).orElse(run);
        final AiChatRunStopReason reason = current.getStopReason() != null
                ? current.getStopReason()
                : defaultReason;
        runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.STOPPED, reason, null);
    }

    /// Asks the chat's active run to stop. The run stops at its next safe point. The request is recorded in the
    /// database, so it works whichever node is running it.
    ///
    /// @param chatId The chat.
    /// @param reason Why it is being stopped.
    /// @return True if there was a run to stop.
    public boolean stop(final int chatId, final AiChatRunStopReason reason) {
        final AiChatRun run = runDao.getActive(chatId).orElse(null);
        if (run == null) {
            return false;
        }
        final boolean stopping = runDao.compareAndSetStatus(
                run.getId(), STOPPABLE, AiChatRunStatus.STOPPING, reason, null);
        // If it is running on this node, tell it now rather than at the next heartbeat.
        NullSafe.consume(localRuns.get(chatId), LocalRun::requestStop);
        LOGGER.debug(() -> "Stop requested for run " + run.getId() + " of chat " + chatId + ": " + stopping);
        return stopping;
    }

    /// @param chatId The chat.
    /// @return True if the chat's run on this node has been asked to stop.
    public boolean isStopRequested(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        return localRun != null && localRun.stopRequested().get();
    }

    /// @param chatId The chat.
    /// @return The flag that is set when the chat's run on this node is asked to stop. A detached flag is
    /// returned if the chat has no run on this node.
    public AtomicBoolean stopFlag(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        return localRun != null
                ? localRun.stopRequested()
                : new AtomicBoolean(false);
    }

    /// @param run A run.
    /// @return True if the run ended without finishing and can be resumed.
    public static boolean isResumable(final AiChatRun run) {
        return run != null && RESUMABLE.contains(run.getStatus());
    }

    /// Updates the heartbeats of this node's runs, passes on stop requests made through the database, and
    /// interrupts runs that have stopped sending heartbeats. Package-private for testing.
    void tick() {
        try {
            final Map<Integer, LocalRun> runs = Map.copyOf(localRuns);
            final List<Integer> runIds = runs.values().stream().map(LocalRun::runId).toList();
            runDao.heartbeat(runIds);
            final Map<Integer, AiChatRunStatus> statuses = runDao.getStatuses(runIds);
            runs.values().forEach(localRun -> {
                if (statuses.get(localRun.runId()) == AiChatRunStatus.STOPPING) {
                    localRun.requestStop();
                }
            });

            for (final AiChatRun interrupted : runDao.interruptStale(
                    System.currentTimeMillis() - STALE_AFTER.toMillis())) {
                LOGGER.info(() -> "Run " + interrupted.getId() + " of chat " + interrupted.getChatId()
                                  + " has stopped sending heartbeats, so has been marked as interrupted");
                // The progress line would otherwise be shown for ever.
                aiService.deleteWorkingMessages(interrupted.getChatId());
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Error updating Ask Stroom AI run heartbeats", e);
        }
    }

    private void ensureHeartbeat() {
        if (heartbeatExecutor == null) {
            synchronized (this) {
                if (heartbeatExecutor == null) {
                    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                        final Thread thread = new Thread(runnable, "ask-stroom-ai-run-heartbeat");
                        thread.setDaemon(true);
                        return thread;
                    });
                    executor.scheduleWithFixedDelay(this::tick,
                            HEARTBEAT_INTERVAL.toMillis(), HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                    heartbeatExecutor = executor;
                }
            }
        }
    }

    /// A run being worked on by this node.
    ///
    /// @param runId         The run's id.
    /// @param stopRequested Set when the run is asked to stop.
    private record LocalRun(int runId, AtomicBoolean stopRequested) {

        void requestStop() {
            stopRequested.set(true);
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
