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
    /// @return The id of the hidden chat that holds its workspace, made now if it has none yet.
    int chatIdFor(final McpSession session) {
        final AiDao aiDao = aiDaoProvider.get();
        final Integer chatId = session.workspaceChatId().updateAndGet(existing -> existing != null
                ? existing
                : aiDao.findSessionChat(session.id())
                        .orElseGet(() -> aiDao.createSessionChat(session.owner(), session.id()).getId()));
        aiDao.touchChat(chatId, TOUCH_INTERVAL.toMillis());
        return chatId;
    }

    /// Deletes a session's workspace, if it has one, e.g. as the session has ended.
    ///
    /// @param session A session.
    void delete(final McpSession session) {
        final Integer chatId = session.workspaceChatId().getAndSet(null);
        if (chatId != null) {
            try {
                securityContext.asProcessingUser(() -> aiRetentionProvider.get().deleteChatAndFiles(chatId));
                LOGGER.debug(() -> "Deleted the workspace of MCP session " + session.id());
            } catch (final RuntimeException e) {
                // The retention job deletes it later.
                LOGGER.warn(() -> "Unable to delete the workspace of MCP session " + session.id(), e);
            }
        }
    }
}
