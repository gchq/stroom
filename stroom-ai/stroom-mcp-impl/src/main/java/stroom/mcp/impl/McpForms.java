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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/// Shows an MCP client's user a form (MCP elicitation) and waits for their answer, for one of Stroom's tools: its
/// questions (e.g. ask_user's), or a request to approve a call that changes something.
///
/// A form holds its call's thread while it waits, so a node lets only so many wait at once (separately from the
/// chat's forms), one user at most half of them, and each waits only so long.
@Singleton
class McpForms {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpForms.class);

    /// A form with no fields: the user only accepts or declines.
    static final Map<String, Object> NO_FIELDS = Map.of("type", "object", "properties", Map.of());

    private final Provider<AiConfig> aiConfigProvider;
    // The forms waiting on this node, and how many of them are each user's, by user UUID. Guarded by this.
    private final Map<String, Integer> waitingByUser = new HashMap<>();
    private int waiting;

    @Inject
    McpForms(final Provider<AiConfig> aiConfigProvider) {
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @param session  The session asking.
    /// @param exchange The request the form is part of.
    /// @param message  What the form is for, shown to the user.
    /// @param schema   Its fields, as a flat JSON schema ([#NO_FIELDS] for a yes or no).
    /// @return The user's answer; or a cancel if the client can't show forms, too many are waiting on this node
    /// (or for this user), or the user doesn't answer in time.
    /// @throws CancellationException if the call is cancelled (its thread interrupted) while the form waits: the
    ///                               thread's interrupt is kept, so the call stops.
    /// @throws ToolRefusal           if the form can't be shown: too large ([ElicitationSchema]), or the client
    ///                               answers it with an error.
    ElicitResult ask(final McpSession session,
                     final McpAsyncServerExchange exchange,
                     final String message,
                     final Map<String, Object> schema) {
        if (!session.canShowForms()) {
            return new ElicitResult(Action.CANCEL, null, null);
        }
        // Checked as the chat checks its forms: a client refusing one too large answers as if the user had declined
        // (seen in testing: an index template's confirmation), so the tool would say the user made no choice.
        final String problem = ElicitationSchema.check(message, schema);
        if (problem != null) {
            LOGGER.warn(() -> LogUtil.message("Not showing a form to {}, as {}",
                    session.owner().toDisplayString(), problem));
            throw new ToolRefusal(ElicitationSchema.cannotShow(problem));
        }
        final McpEndpointConfig config = config();
        final String user = session.owner().getUuid();
        if (!take(user, config.getMaxFormsPerNode())) {
            LOGGER.warn(() -> LogUtil.message(
                    "Not showing a form to {} as {} are already waiting on this node, or half of them for this "
                    + "user (see stroom.ai.mcpEndpoint.maxFormsPerNode)",
                    session.owner().toDisplayString(),
                    config.getMaxFormsPerNode()));
            return new ElicitResult(Action.CANCEL, null, null);
        }
        try {
            final Duration timeout = config.getFormTimeout().getDuration();
            final ElicitResult result = exchange
                    .createElicitation(new ElicitFormRequest(message, schema, null))
                    .block(timeout);
            return result == null
                    ? new ElicitResult(Action.CANCEL, null, null)
                    : result;
        } catch (final RuntimeException e) {
            if (isInterrupt(e)) {
                // The call was cancelled: block() wraps the interrupt, which must not be lost, or the call goes on.
                Thread.currentThread().interrupt();
                throw new CancellationException("The call was cancelled while it waited for the user's answer.");
            }
            final McpError refused = clientError(e);
            if (refused != null) {
                // The client answered with an error: it couldn't show the form, so the user wasn't asked.
                LOGGER.warn(() -> LogUtil.message("The client of {} couldn't show a form: {}",
                        session.owner().toDisplayString(), refused.getMessage()));
                throw new ToolRefusal(ElicitationSchema.cannotShow("the client said: " + refused.getMessage()));
            }
            // E.g. the user didn't answer in time, or the client went away.
            LOGGER.debug(() -> LogUtil.message(
                    "A form for {} wasn't answered: {}",
                    session.owner().toDisplayString(),
                    e.getMessage()),
                    e);
            return new ElicitResult(Action.CANCEL, null, null);
        } finally {
            release(user);
        }
    }

    /// @return How many forms are waiting for answers on this node.
    synchronized int waitingCount() {
        return waiting;
    }

    // A user may have at most half the node's forms waiting, so one can't keep everyone else's from being shown.
    private synchronized boolean take(final String user, final int maxPerNode) {
        final int maxPerUser = Math.max(1, maxPerNode / 2);
        final int usersWaiting = waitingByUser.getOrDefault(user, 0);
        if (waiting >= maxPerNode || usersWaiting >= maxPerUser) {
            return false;
        }
        waiting++;
        waitingByUser.put(user, usersWaiting + 1);
        return true;
    }

    private synchronized void release(final String user) {
        waiting--;
        waitingByUser.computeIfPresent(user, (uuid, count) -> count <= 1
                ? null
                : count - 1);
    }

    // The error the client answered the form with, if that is why it failed.
    private static McpError clientError(final Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof final McpError error) {
                return error;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static boolean isInterrupt(final Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof InterruptedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    private McpEndpointConfig config() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint, new McpEndpointConfig());
    }
}
