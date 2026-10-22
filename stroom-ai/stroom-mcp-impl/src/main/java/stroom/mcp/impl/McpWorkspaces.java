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

package stroom.mcp.impl;

import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiRetention;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;

/// The workspace of an MCP session, as a chat has one: files the tools write (e.g. a query's results) for later
/// calls to read. Each is held by a hidden chat of the session's user, made when a call first needs it, never
/// listed with their chats, and deleted when the session ends (or, for a session that ended without saying so, by
/// the retention job once it has been idle long enough).
///
/// A session's calls are counted while they run ([#callStarted], [#callEnded]): a session that ends while calls
/// still run has its workspace deleted once the last of them ends, and no call can make it a new one.
@Singleton
class McpWorkspaces {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpWorkspaces.class);

    /// How often a session's hidden chat is marked as used, at most, so it isn't taken for idle.
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final Provider<AiDao> aiDaoProvider;
    private final Provider<AiRetention> aiRetentionProvider;
    private final SecurityContext securityContext;

    @Inject
    McpWorkspaces(final Provider<AiDao> aiDaoProvider,
                  final Provider<AiRetention> aiRetentionProvider,
                  final SecurityContext securityContext) {
        this.aiDaoProvider = aiDaoProvider;
        this.aiRetentionProvider = aiRetentionProvider;
        this.securityContext = securityContext;
    }

    /// @param session A session.
    /// @return The id of the hidden chat that holds its workspace, made now if it has none yet. It is found or
    /// made under the session's lock, so calls made at once don't each make one.
    /// @throws IllegalStateException if the session has ended.
    int chatIdFor(final McpSession session) {
        final AiDao aiDao = aiDaoProvider.get();
        final State state = session.workspace();
        final int chatId;
        synchronized (state) {
            if (state.ended) {
                throw new IllegalStateException("This MCP session has ended. Start a new one.");
            }
            if (state.chatId == null) {
                state.chatId = aiDao.findSessionChat(session.id())
                        .orElseGet(() -> aiDao.createSessionChat(session.owner(), session.id()).getId());
            }
            chatId = state.chatId;
        }
        aiDao.touchChat(chatId, TOUCH_INTERVAL.toMillis());
        return chatId;
    }

    /// Counts a call of the session as running, so its workspace isn't deleted under it.
    ///
    /// @param session A session.
    /// @return False if the session has ended, so the call shouldn't be made.
    boolean callStarted(final McpSession session) {
        final State state = session.workspace();
        synchronized (state) {
            if (state.ended) {
                return false;
            }
            state.callsRunning++;
            return true;
        }
    }

    /// Counts a call of the session as ended: if the session has ended and this was its last call, its workspace
    /// is deleted now.
    ///
    /// @param session A session, whose [#callStarted] gave true for the call.
    void callEnded(final McpSession session) {
        final State state = session.workspace();
        final Integer chatId;
        synchronized (state) {
            state.callsRunning--;
            chatId = state.ended && state.callsRunning <= 0
                    ? state.takeChatId()
                    : null;
        }
        deleteChat(session, chatId);
    }

    /// Deletes a session's workspace, if it has one, e.g. as the session has ended; once the calls still running
    /// in it have ended, if there are any. No call can make it a new one after this.
    ///
    /// @param session A session.
    void delete(final McpSession session) {
        final State state = session.workspace();
        final boolean callsRunning;
        final Integer chatId;
        synchronized (state) {
            state.ended = true;
            callsRunning = state.callsRunning > 0;
            chatId = callsRunning
                    ? null
                    : state.takeChatId();
        }
        if (callsRunning) {
            LOGGER.debug(() -> "The workspace of MCP session " + session.id() + " is deleted once its calls end");
        }
        deleteChat(session, chatId);
    }

    private void deleteChat(final McpSession session, final Integer chatId) {
        if (chatId != null) {
            try {
                securityContext.asProcessingUser(() -> aiRetentionProvider.get().deleteChatAndFiles(chatId));
                LOGGER.debug(() -> "Deleted the workspace of MCP session " + session.id());
            } catch (final RuntimeException e) {
                // The retention job deletes it later.
                LOGGER.warn(() -> "Unable to delete the workspace of an MCP session of "
                                  + session.owner().toDisplayString(), e);
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The workspace state of one session: its hidden chat (once made), how many of its calls are running, and
    /// whether it has ended. Guarded by itself.
    static final class State {

        private Integer chatId;
        private int callsRunning;
        private boolean ended;

        private Integer takeChatId() {
            final Integer taken = chatId;
            chatId = null;
            return taken;
        }
    }
}
