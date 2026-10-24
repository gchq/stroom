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
import stroom.ai.shared.AiMessageType;
import stroom.node.api.NodeInfo;
import stroom.security.api.HasSessionId;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// Starts and keeps track of "Ask Stroom AI" agent runs. A run answers one question in a chat. It is recorded in
/// `ai_chat_run` and worked on in the background, as the user who asked, so the request that started it returns
/// straight away and the answer carries on being worked out if the user refreshes the page or closes the tab.
/// The client follows its progress by polling.
///
/// While a run is being worked on, this node updates its heartbeat every [#HEARTBEAT_INTERVAL]. The same tick:
///
/// - passes on stop requests made through the database, so a stop works whichever node receives it;
/// - marks as interrupted any run, on any node, whose heartbeat is older than [#STALE_AFTER], as its node must have
///   stopped;
/// - stops runs that have reached the time limit, or whose user's account has been disabled or deleted;
/// - marks runs as unattended when no browser has checked on any of the user's chats for a while, so that they may
///   only use read-only tools.
///
/// Runs are also stopped when the session that started them ends: with reason [AiChatRunStopReason#SIGN_OUT] if the
/// user signed out, or [AiChatRunStopReason#SESSION_EXPIRED] if it expired. Either way they can be resumed.
@Singleton
public class AiChatRunManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiChatRunManager.class);

    static final String TASK_NAME = "Ask Stroom AI";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    static final Duration STALE_AFTER = Duration.ofSeconds(60);
    /// The types of the messages the user sent to steer a run that it has yet to pick up.
    static final Set<AiMessageType> QUEUED_STEERS = Set.of(AiMessageType.QUEUED_STEER_MESSAGE,
            AiMessageType.SEND_NOW_STEER_MESSAGE);

    /// How often a browser checking on a chat is recorded, at most.
    static final Duration PRESENCE_RECORD_INTERVAL = Duration.ofSeconds(30);

    /// The statuses of runs that are not being worked on by a thread, so stop straight away.
    private static final Set<AiChatRunStatus> STOPPABLE_AT_ONCE = EnumSet.of(
            AiChatRunStatus.QUEUED, AiChatRunStatus.WAITING_FOR_USER);

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
    private final AiDao aiDao;
    private final UserService userService;
    private final Provider<AiConfig> aiConfigProvider;
    private final HttpServletRequestHolder requestHolder;

    /// The runs being worked on, or queued, by this node, by chat id.
    private final Map<Integer, LocalRun> localRuns = new ConcurrentHashMap<>();
    /// The chats with queued runs, in the order they were asked.
    private final Deque<Integer> queue = new ArrayDeque<>();
    // Heavy tool calls made over MCP on this node, which each take one of their user's run slots.
    private final Map<String, AtomicInteger> toolCallsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger toolCallsOnNode = new AtomicInteger();
    private volatile ScheduledExecutorService heartbeatExecutor;

    @Inject
    AiChatRunManager(final AiChatRunDao runDao,
                     final TaskContextFactory taskContextFactory,
                     final ExecutorProvider executorProvider,
                     final NodeInfo nodeInfo,
                     final SecurityContext securityContext,
                     final AiService aiService,
                     final AiDao aiDao,
                     final UserService userService,
                     final Provider<AiConfig> aiConfigProvider,
                     final HttpServletRequestHolder requestHolder) {
        this.runDao = runDao;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.userService = userService;
        this.aiConfigProvider = aiConfigProvider;
        this.requestHolder = requestHolder;
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
        final String sessionIdHash = sessionIdHash(securityContext.getUserIdentity() instanceof
                final HasSessionId hasSessionId
                ? hasSessionId.getSessionId()
                : null);
        synchronized (queue) {
            final AiChatRunStatus status = hasFreeSlot(userUuid)
                    ? AiChatRunStatus.RUNNING
                    : AiChatRunStatus.QUEUED;
            final AiChatRun run = runDao.createIfNoneActive(
                            chatId, userUuid, status, nodeInfo.getThisNodeName(), sessionIdHash)
                    .orElseThrow(() -> new AiChatBusyException(chatId));
            LOGGER.debug(() -> "Created " + status + " run " + run.getId() + " for chat " + chatId);
            schedule(chatId, run, status, userUuid, work);
            return run;
        }
    }

    /// Carries on with a chat's run that is waiting for the user (e.g. for an approval they have now given), by
    /// doing more work in it. It starts straight away if there is a free slot, otherwise it is queued.
    ///
    /// @param chatId The chat.
    /// @param work   What to do.
    /// @return The run.
    /// @throws AiChatBusyException if the chat has no run waiting for the user.
    public AiChatRun continueRun(final int chatId, final Consumer<AiChatRun> work) {
        final String userUuid = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        synchronized (queue) {
            // A run waiting for a form to be filled in still has its thread, so it is not carried on.
            final AiChatRun run = runDao.getActive(chatId)
                    .filter(active -> active.getStatus() == AiChatRunStatus.WAITING_FOR_USER
                                      && active.getStopReason() != AiChatRunStopReason.NEEDS_INPUT)
                    .orElseThrow(() -> new AiChatBusyException(chatId));
            final AiChatRunStatus status = hasFreeSlot(userUuid)
                    ? AiChatRunStatus.RUNNING
                    : AiChatRunStatus.QUEUED;
            if (!runDao.compareAndSetStatus(run.getId(), List.of(AiChatRunStatus.WAITING_FOR_USER), status, null,
                    null)) {
                throw new AiChatBusyException(chatId);
            }
            LOGGER.debug(() -> "Continuing run " + run.getId() + " of chat " + chatId + " as " + status);
            schedule(chatId, run, status, userUuid, work);
            return run;
        }
    }

    private void schedule(final int chatId,
                          final AiChatRun run,
                          final AiChatRunStatus status,
                          final String userUuid,
                          final Consumer<AiChatRun> work) {
        // The user is here, as they have just asked or answered something.
        aiDao.recordPoll(chatId, 0);

        // The task is made now, so that it runs as the user asking, whenever it starts.
        final Runnable task = taskContextFactory.context(TASK_NAME, taskContext -> {
            // A run serves no HTTP request. Its thread may have been made while one was being served, and inherited
            // it (see HttpServletRequestHolder): long finished, it fails when read, e.g. by the audit log for the
            // client's address, or by a search.
            requestHolder.set(null);
            taskContext.info(() -> "Chat " + chatId + ", run " + run.getId());
            execute(run, work, taskContext::isTerminated);
        });
        final LocalRun localRun = new LocalRun(run.getId(), userUuid, task,
                new AtomicLong(System.currentTimeMillis()), new AtomicBoolean(false),
                new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false),
                new AtomicBoolean(false));
        localRuns.put(chatId, localRun);
        ensureHeartbeat();

        if (status == AiChatRunStatus.RUNNING) {
            submit(chatId, localRun);
        } else {
            // Started when a slot is free, see startQueued.
            queue.add(chatId);
        }
    }

    /// @return True if another run may start working now for the user, without going over the user's or this
    /// node's limit. Runs waiting for the user do not count.
    private boolean hasFreeSlot(final String userUuid) {
        final AiConfig aiConfig = aiConfigProvider.get();
        final long workingOnNode = localRuns.values().stream()
                .filter(localRun -> localRun.working().get())
                .count() + toolCallsOnNode.get();
        return workingOnNode < aiConfig.getMaxRunsPerNode()
               && (userUuid == null
                   || runDao.countWorking(userUuid) + toolCalls(userUuid) < aiConfig.getMaxRunsPerUser());
    }

    /// Takes one of the user's run slots for a heavy tool call made over MCP (e.g. a query), as a chat run takes
    /// one, so MCP calls and chat runs count against the same limits. Calls on other nodes aren't counted.
    ///
    /// @param userUuid The user making the call.
    /// @return The slot, to close when the call ends; or empty if the user, or this node, has none free.
    public Optional<RunSlot> takeToolCallSlot(final String userUuid) {
        synchronized (queue) {
            if (!hasFreeSlot(userUuid)) {
                return Optional.empty();
            }
            toolCallsByUser.computeIfAbsent(userUuid, uuid -> new AtomicInteger()).incrementAndGet();
            toolCallsOnNode.incrementAndGet();
        }
        final AtomicBoolean closed = new AtomicBoolean();
        return Optional.of(() -> {
            if (closed.compareAndSet(false, true)) {
                synchronized (queue) {
                    NullSafe.consume(toolCallsByUser.get(userUuid), AtomicInteger::decrementAndGet);
                    toolCallsByUser.computeIfPresent(userUuid, (uuid, count) -> count.get() <= 0
                            ? null
                            : count);
                    toolCallsOnNode.decrementAndGet();
                }
                // A slot has come free. The call's result is made already, so a failure to start a queued run
                // (which marks that run as failed) is logged rather than thrown from here.
                try {
                    startQueued();
                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to start a queued run once a tool call's run slot came free", e);
                }
            }
        });
    }

    private int toolCalls(final String userUuid) {
        return NullSafe.getOrElse(toolCallsByUser.get(userUuid), AtomicInteger::get, 0);
    }

    private void submit(final int chatId, final LocalRun localRun) {
        localRun.working().set(true);
        localRun.startTimeMs().set(System.currentTimeMillis());
        try {
            executorProvider.get().execute(localRun.task());
        } catch (final RuntimeException e) {
            localRuns.remove(chatId, localRun);
            runDao.compareAndSetStatus(localRun.runId(), ACTIVE, AiChatRunStatus.FAILED, null, e.getMessage());
            throw e;
        }
    }

    /// Starts queued runs, in the order they were asked for, while there are free slots. Package-private for
    /// testing.
    void startQueued() {
        synchronized (queue) {
            final Iterator<Integer> iterator = queue.iterator();
            while (iterator.hasNext()) {
                final int chatId = iterator.next();
                final LocalRun localRun = localRuns.get(chatId);
                if (localRun == null) {
                    iterator.remove();
                } else if (hasFreeSlot(localRun.userUuid())) {
                    iterator.remove();
                    if (runDao.compareAndSetStatus(localRun.runId(), List.of(AiChatRunStatus.QUEUED),
                            AiChatRunStatus.RUNNING, null, null)) {
                        LOGGER.debug(() -> "Starting queued run " + localRun.runId() + " of chat " + chatId);
                        submit(chatId, localRun);
                    } else {
                        // Stopped while it was queued.
                        localRuns.remove(chatId, localRun);
                    }
                }
            }
        }
    }

    /// Runs the work and records how the run ended. Package-private for testing.
    ///
    /// @param run        The run.
    /// @param work       The work.
    /// @param terminated Whether the run's task has been terminated, e.g. because its session ended. The work
    ///                   may return normally when that happens.
    void execute(final AiChatRun run, final Consumer<AiChatRun> work, final BooleanSupplier terminated) {
        try {
            work.accept(run);
            if (terminated.getAsBoolean()) {
                stopped(run, AiChatRunStopReason.TERMINATED);
            } else {
                finish(run);
            }
        } catch (final WaitingForUserException e) {
            // Give up the thread and the slot until the user answers (S8).
            LOGGER.debug(() -> "Run " + run.getId() + " is waiting for the user: " + e.getMessage());
            if (!runDao.compareAndSetStatus(run.getId(), List.of(AiChatRunStatus.RUNNING),
                    AiChatRunStatus.WAITING_FOR_USER, e.getStopReason(), null)) {
                // A stop was asked for while it worked.
                stopped(run, null);
            }
        } catch (final TaskTerminatedException e) {
            LOGGER.debug(() -> "Run " + run.getId() + " was terminated");
            stopped(run, AiChatRunStopReason.TERMINATED);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Run " + run.getId() + " for chat " + run.getChatId() + " failed", e);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.FAILED, null,
                    NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        } catch (final Error e) {
            // E.g. a class Stroom can't load (seen: its jar rewritten under the running node). The run fails, saying
            // why, rather than being left for its heartbeats to stop and taken for one whose node has. The error
            // goes on up, as one the JVM may not recover from.
            LOGGER.error(() -> "Run " + run.getId() + " for chat " + run.getChatId() + " failed", e);
            runDao.compareAndSetStatus(run.getId(), ACTIVE, AiChatRunStatus.FAILED, null, e.toString());
            throw e;
        } finally {
            // Anything the user sent that the run didn't pick up stays in the conversation, as sent, for the model to
            // see on the chat's next run, rather than being shown as waiting forever.
            aiDao.changeMessageTypes(run.getChatId(), QUEUED_STEERS, AiMessageType.STEER_MESSAGE);
            localRuns.remove(run.getChatId(), localRuns.get(run.getChatId()));
            // A slot has come free.
            startQueued();
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
        return runDao.getActive(chatId)
                .map(run -> stopRun(run, reason))
                .orElse(false);
    }

    private boolean stopRun(final AiChatRun run, final AiChatRunStopReason reason) {
        final boolean stopped;
        if (run.getStatus() == AiChatRunStatus.RUNNING) {
            // Being worked on, so ask it to stop at its next safe point.
            stopped = runDao.compareAndSetStatus(
                    run.getId(), List.of(AiChatRunStatus.RUNNING), AiChatRunStatus.STOPPING, reason, null);
        } else {
            // Nothing is working on it, so it can stop now.
            stopped = runDao.compareAndSetStatus(
                    run.getId(), STOPPABLE_AT_ONCE, AiChatRunStatus.STOPPED, reason, null);
        }
        // If it is running on this node, tell it now rather than at the next heartbeat.
        NullSafe.consume(localRuns.get(run.getChatId()), LocalRun::requestStop);
        LOGGER.debug(() -> "Stop requested for run " + run.getId() + " of chat " + run.getChatId()
                           + " (" + reason + "): " + stopped);
        return stopped;
    }

    /// Stops the runs started from an HTTP session that has ended.
    ///
    /// @param sessionId The session's id.
    /// @param reason    Why it ended: [AiChatRunStopReason#SIGN_OUT] or [AiChatRunStopReason#SESSION_EXPIRED].
    public void stopForSession(final String sessionId, final AiChatRunStopReason reason) {
        final String hash = sessionIdHash(sessionId);
        if (hash == null) {
            return;
        }
        runDao.getActiveBySession(hash).forEach(run -> stopRun(run, reason));
        // The session's tasks may already have been terminated, before this was told the session had ended.
        runDao.correctStopReason(hash, AiChatRunStopReason.TERMINATED, reason,
                System.currentTimeMillis() - STALE_AFTER.toMillis());
    }

    /// @param chatId The chat.
    /// @return True if the chat's run on this node is unattended, i.e. its user has gone, so it may only use
    /// read-only tools.
    public boolean isUnattended(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        return localRun != null && localRun.unattended().get();
    }

    /// Records that the chat's run has read something that could hold instructions planted by someone else (tool
    /// output, workspace files or Stroom data), so changes it makes that outlive the chat need the user's approval.
    ///
    /// @param chatId The chat.
    public void markTainted(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        if (localRun != null && !localRun.tainted().getAndSet(true)) {
            LOGGER.debug(() -> "The run of chatId=" + chatId + " is now tainted");
        }
    }

    /// @param chatId The chat.
    /// @return True if the chat's run has read something that could hold planted instructions, or if the chat has
    /// no run on this node (so nothing is known about what was read).
    public boolean isTainted(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        return localRun == null || localRun.tainted().get();
    }

    /// Records that the chat's run has read the output of an untrusted MCP server's tool, so trusted servers' tools
    /// that modify data need the user's approval from now on.
    ///
    /// @param chatId The chat.
    public void markReadUntrustedServer(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        if (localRun != null && !localRun.readUntrustedServer().getAndSet(true)) {
            LOGGER.debug(() -> "The run of chatId=" + chatId + " has read an untrusted MCP server's output");
        }
    }

    /// @param chatId The chat.
    /// @return True if the chat's run has read an untrusted MCP server's tool output, or if the chat has no run on
    /// this node (so nothing is known about what was read).
    public boolean hasReadUntrustedServer(final int chatId) {
        final LocalRun localRun = localRuns.get(chatId);
        return localRun == null || localRun.readUntrustedServer().get();
    }

    /// Adds to the tokens and tool calls used by the chat's run on this node, if it has one.
    ///
    /// @param chatId       The chat.
    /// @param inputTokens  Input tokens to add.
    /// @param outputTokens Output tokens to add.
    /// @param toolCalls    Tool calls to add.
    public void addUsage(final int chatId, final long inputTokens, final long outputTokens, final int toolCalls) {
        final LocalRun localRun = localRuns.get(chatId);
        if (localRun != null) {
            runDao.addUsage(localRun.runId(), inputTokens, outputTokens, toolCalls);
        }
    }

    /// Records that a browser is checking on a chat, i.e. its user is still there.
    ///
    /// @param chatId The chat.
    public void recordPresence(final int chatId) {
        aiDao.recordPoll(chatId, PRESENCE_RECORD_INTERVAL.toMillis());
    }

    /// @param sessionId An HTTP session id, or null.
    /// @return A hash of the id, as stored with runs (the id itself is not stored), or null if there is no id.
    static String sessionIdHash(final String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
            runs.forEach(this::checkLimits);
            // Slots may have come free on other nodes.
            startQueued();

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

    /// Stops a run that has reached the time limit or whose user can no longer use Stroom, and marks whether it is
    /// unattended.
    private void checkLimits(final int chatId, final LocalRun localRun) {
        final AiConfig aiConfig = aiConfigProvider.get();
        final long now = System.currentTimeMillis();
        final AiChatRun run = runDao.get(localRun.runId()).orElse(null);
        if (run == null || run.getStatus() != AiChatRunStatus.RUNNING) {
            return;
        }

        if (now - localRun.startTimeMs().get() >= aiConfig.getMaxRunDuration().toMillis()) {
            LOGGER.info(() -> "Run " + localRun.runId() + " of chat " + chatId + " has reached the time limit");
            stopRun(run, AiChatRunStopReason.RUN_LIMIT);
            return;
        }

        if (!isUserAllowed(localRun.userUuid())) {
            LOGGER.info(() -> "Stopping run " + localRun.runId() + " of chat " + chatId
                              + " as its user is disabled or deleted");
            stopRun(run, AiChatRunStopReason.ACCOUNT_DISABLED);
            return;
        }

        final Long lastPollMs = aiDao.getLastPollMs(localRun.userUuid());
        final boolean unattended = lastPollMs == null
                                   || now - lastPollMs > aiConfig.getUnattendedAfter().toMillis();
        if (localRun.unattended().getAndSet(unattended) != unattended) {
            LOGGER.debug(() -> "Run " + localRun.runId() + " of chat " + chatId + " unattended: " + unattended);
            runDao.setUnattended(localRun.runId(), unattended);
        }
    }

    private boolean isUserAllowed(final String userUuid) {
        if (userUuid == null) {
            return true;
        }
        return securityContext.asProcessingUserResult(() -> userService.loadByUuid(userUuid)
                .map(User::isEnabled)
                .orElse(false));
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

    /// A run being worked on, or queued, by this node.
    ///
    /// @param runId         The run's id.
    /// @param userUuid      The user it runs as.
    /// @param task          The task that works on it, as the user.
    /// @param startTimeMs   When it started being worked on.
    /// @param working       Set while a thread is working on it (it is not queued).
    /// @param stopRequested Set when the run is asked to stop.
    /// @param unattended    Set while the user has gone.
    private record LocalRun(int runId,
                            String userUuid,
                            Runnable task,
                            AtomicLong startTimeMs,
                            AtomicBoolean working,
                            AtomicBoolean stopRequested,
                            AtomicBoolean unattended,
                            AtomicBoolean tainted,
                            AtomicBoolean readUntrustedServer) {

        void requestStop() {
            stopRequested.set(true);
        }
    }

    /// Thrown by a run's work when it has to wait for the user (e.g. to approve tool calls), to give up its thread
    /// and its place in the limits until they answer.
    public static class WaitingForUserException extends RuntimeException {

        private final AiChatRunStopReason stopReason;

        /// @param message Why the run waits, for the log.
        public WaitingForUserException(final String message) {
            this(message, AiChatRunStopReason.NEEDS_APPROVAL);
        }

        /// @param message    Why the run waits, for the log.
        /// @param stopReason What the run waits for, as the chat shows it, e.g. [AiChatRunStopReason#NEEDS_ANSWERS].
        public WaitingForUserException(final String message, final AiChatRunStopReason stopReason) {
            super(message);
            this.stopReason = stopReason;
        }

        /// @return What the run waits for.
        public AiChatRunStopReason getStopReason() {
            return stopReason;
        }
    }

    /// One of a user's run slots, taken by a heavy tool call made over MCP (see [#takeToolCallSlot(String)]).
    @FunctionalInterface
    public interface RunSlot extends AutoCloseable {

        /// Gives the slot back. Closing it again does nothing.
        @Override
        void close();
    }

    /// Thrown when a question is asked in a chat that is still working on an earlier one.
    public static class AiChatBusyException extends RuntimeException {

        public AiChatBusyException(final int chatId) {
            super("This chat is still working on a question. Wait for it to finish or stop it first.");
            LOGGER.debug(() -> "Chat " + chatId + " is busy");
        }
    }
}
