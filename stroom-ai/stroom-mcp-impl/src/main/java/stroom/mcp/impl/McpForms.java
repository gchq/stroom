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
import stroom.ai.impl.McpEndpointConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/// Shows an MCP client's user a form (MCP elicitation) and waits for their answer, for one of Stroom's tools: its
/// questions (e.g. ask_user's), or a request to approve a call that changes something.
///
/// A form holds its call's thread while it waits, so a node lets only so many wait at once (separately from the
/// chat's forms), and each waits only so long.
@Singleton
class McpForms {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpForms.class);

    /// A form with no fields: the user only accepts or declines.
    static final Map<String, Object> NO_FIELDS = Map.of("type", "object", "properties", Map.of());

    private final Provider<AiConfig> aiConfigProvider;
    private final AtomicInteger waiting = new AtomicInteger();

    @Inject
    McpForms(final Provider<AiConfig> aiConfigProvider) {
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @param session  The session asking.
    /// @param exchange The request the form is part of.
    /// @param message  What the form is for, shown to the user.
    /// @param schema   Its fields, as a flat JSON schema ([#NO_FIELDS] for a yes or no).
    /// @return The user's answer; or a cancel if the client can't show forms, too many are waiting on this node,
    /// or the user doesn't answer in time.
    ElicitResult ask(final McpSession session,
                     final McpAsyncServerExchange exchange,
                     final String message,
                     final Map<String, Object> schema) {
        if (!session.canShowForms()) {
            return new ElicitResult(Action.CANCEL, null, null);
        }
        final McpEndpointConfig config = config();
        if (waiting.incrementAndGet() > config.getMaxFormsPerNode()) {
            waiting.decrementAndGet();
            LOGGER.warn(() -> "Not showing a form in MCP session " + session.id() + " as "
                              + config.getMaxFormsPerNode() + " are already waiting on this node (see "
                              + "stroom.ai.mcpEndpoint.maxFormsPerNode)");
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
            // E.g. the user didn't answer in time, or the client went away.
            LOGGER.debug(() -> "A form in MCP session " + session.id() + " wasn't answered: " + e.getMessage(), e);
            return new ElicitResult(Action.CANCEL, null, null);
        } finally {
            waiting.decrementAndGet();
        }
    }

    /// @return How many forms are waiting for answers on this node.
    int waitingCount() {
        return waiting.get();
    }

    private McpEndpointConfig config() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint, new McpEndpointConfig());
    }
}
