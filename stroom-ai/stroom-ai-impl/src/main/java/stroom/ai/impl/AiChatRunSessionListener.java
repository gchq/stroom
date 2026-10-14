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

import stroom.ai.shared.AiChatRunStopReason;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.SessionUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/// Stops the "Ask Stroom AI" agent runs started from an HTTP session when the session ends. A run is never kept
/// going (with the user's tokens) beyond the session that started it. Runs stopped this way can be resumed when
/// the user is back.
@Singleton
class AiChatRunSessionListener implements HttpSessionListener {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiChatRunSessionListener.class);

    private final Provider<AiChatRunManager> runManagerProvider;

    @Inject
    AiChatRunSessionListener(final Provider<AiChatRunManager> runManagerProvider) {
        this.runManagerProvider = runManagerProvider;
    }

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        // Nothing to do.
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        try {
            final HttpSession session = event.getSession();
            final AiChatRunStopReason reason = SessionUtil.isExplicitLogout(session)
                    ? AiChatRunStopReason.SIGN_OUT
                    : AiChatRunStopReason.SESSION_EXPIRED;
            runManagerProvider.get().stopForSession(session.getId(), reason);
        } catch (final RuntimeException e) {
            LOGGER.error("Error stopping Ask Stroom AI runs for an ended session", e);
        }
    }
}
