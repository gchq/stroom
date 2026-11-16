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

import stroom.ai.impl.AiDao.IdleChat;
import stroom.ai.impl.AskStroomAIService.IdleCompactionResult;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// The 'AI Chat Idle Compaction' job, which compacts 'Ask Stroom AI' chats left idle before their users come back,
/// so a question asked on return need not wait for a compaction (see [AiCompactionConfig#getIdleAfter()]).
///
/// Each run is cheap:
///
/// - candidates are found with one query ([AiDao#findIdleChats]): chats whose last message is older than the idle
///   time but no more than [#LOOK_BACK] older, is not a compaction, and that have no active run and no idle
///   compaction run since that message;
/// - at most [#MAX_CHECKED_PER_RUN] are measured, cheaply, with no model call, and at most
///   [#MAX_STARTED_PER_RUN] runs are started;
/// - a chat measured as under the idle threshold is remembered (by its last message), so is not measured again
///   until it has a new message.
///
/// What is remembered is kept in memory, bounded by the look back window: after a node restarts, a chat that went
/// idle within the window may be measured once more, and chats idle for longer are never looked at again. A chat
/// compacted, or whose idle compaction was stopped or failed, is left alone by the query itself, through its
/// compaction message or the run, so that holds across restarts and nodes. Several nodes running the job compact a
/// chat at most once, as a run is only started if the chat has no active run, and the run checks the chat has not
/// changed since it was found.
@Singleton
public class AiIdleCompaction {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiIdleCompaction.class);

    public static final String JOB_NAME = "AI Chat Idle Compaction";
    /// How long after going idle a chat is still looked at. A chat missed for longer (e.g. while the job or its
    /// node was down) is compacted, if it needs it, when the user next asks a question, as without this job.
    static final Duration LOOK_BACK = Duration.ofHours(1);
    /// The most idle chats measured in one run.
    static final int MAX_CHECKED_PER_RUN = 50;
    /// The most compactions started in one run.
    static final int MAX_STARTED_PER_RUN = 5;

    private final AiDao aiDao;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<AskStroomAIService> serviceProvider;
    private final UserService userService;
    private final SecurityContext securityContext;
    /// The last messages of idle chats found to need no compaction, with when each was stored.
    private final Map<Integer, Long> needNothing = new ConcurrentHashMap<>();

    @Inject
    AiIdleCompaction(final AiDao aiDao,
                     final Provider<AiConfig> aiConfigProvider,
                     final Provider<AskStroomAIService> serviceProvider,
                     final UserService userService,
                     final SecurityContext securityContext) {
        this.aiDao = aiDao;
        this.aiConfigProvider = aiConfigProvider;
        this.serviceProvider = serviceProvider;
        this.userService = userService;
        this.securityContext = securityContext;
    }

    /// Runs the job.
    public void exec() {
        securityContext.asProcessingUser(() -> exec(System.currentTimeMillis()));
    }

    /// Runs the job as if it were a time. Package-private for testing.
    ///
    /// @param nowMs The time.
    /// @return How many compactions were started.
    int exec(final long nowMs) {
        final AiCompactionConfig config = NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getCompaction,
                new AiCompactionConfig());
        if (!config.isIdleCompactionEnabled()) {
            LOGGER.debug("Idle compaction is turned off");
            return 0;
        }
        final long idleBeforeMs = nowMs - config.getIdleAfter().toMillis();
        final long idleSinceMs = idleBeforeMs - LOOK_BACK.toMillis();
        // Chats that went idle before the window are never found again, so need not be remembered.
        needNothing.values().removeIf(timeMs -> timeMs < idleSinceMs);

        final List<IdleChat> chats = aiDao.findIdleChats(idleBeforeMs, idleSinceMs, Set.copyOf(needNothing.keySet()),
                MAX_CHECKED_PER_RUN);
        int started = 0;
        for (final IdleChat chat : chats) {
            if (started >= MAX_STARTED_PER_RUN) {
                break;
            }
            final IdleCompactionResult result = compact(chat);
            LOGGER.debug(() -> "Idle chat " + chat.chatId() + ": " + result);
            switch (result) {
                case STARTED -> started++;
                case UNDER_THRESHOLD, CANNOT_COMPACT -> remember(chat);
                case BUSY -> {
                    // Tried again next time.
                }
            }
        }
        final int startedCount = started;
        if (startedCount > 0) {
            LOGGER.info(() -> LogUtil.message("{}: started compacting {} idle chat(s), of {} found",
                    JOB_NAME, startedCount, chats.size()));
        }
        return startedCount;
    }

    /// @return The number of idle chats remembered as needing no compaction. Package-private for testing.
    int rememberedCount() {
        return needNothing.size();
    }

    // Remembers that the chat, as its last message left it, needs no compaction.
    private void remember(final IdleChat chat) {
        needNothing.put(chat.lastMessageId(), chat.lastMessageTimeMs());
    }

    // Compacts the chat as its owner, if it needs it.
    private IdleCompactionResult compact(final IdleChat chat) {
        try {
            final UserRef owner = userService.loadByUuid(chat.userUuid())
                    .filter(User::isEnabled)
                    .map(User::asRef)
                    .orElse(null);
            if (owner == null) {
                LOGGER.debug(() -> "Not compacting idle chat " + chat.chatId() + " as its user is disabled or gone");
                return IdleCompactionResult.CANNOT_COMPACT;
            }
            return securityContext.asUserResult(owner, () -> serviceProvider.get().compactWhileIdle(chat));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Unable to compact idle chat " + chat.chatId() + ": " + e.getMessage(), e);
            return IdleCompactionResult.CANNOT_COMPACT;
        }
    }
}
