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

import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/// A tool's side calls over `/mcp` ([ModelSideCalls]): to the client's model by MCP sampling
/// (`sampling/createMessage`), where the client said it can (approved there, as its user and settings say). Stroom
/// has no model of its own on `/mcp`, so a client that can't sample has none ([#available()] false), and the
/// tool says what it left undone.
///
/// The client may ask its user to approve each request, so the wait doesn't count against the call's time limit
/// ([ToolDeadline#paused]), and is as long as a form's.
final class McpSampling implements ModelSideCalls {

    private final McpSession session;
    private final McpAsyncServerExchange exchange;
    private final Duration timeout;

    /// @param session  The session.
    /// @param exchange The request the calls are made in.
    /// @param timeout  The longest a call waits for the client's answer.
    McpSampling(final McpSession session, final McpAsyncServerExchange exchange, final Duration timeout) {
        this.session = Objects.requireNonNull(session, "session");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public boolean available() {
        return session.canSample();
    }

    @Override
    public String via() {
        return session.canSample()
                ? "the client's model, by MCP sampling (" + session.clientName() + ")"
                : session.clientName() + " doesn't offer MCP sampling, and Stroom has no model on /mcp";
    }

    @Override
    public Answer ask(final Question question) {
        if (!session.canSample()) {
            throw new SideCallException(via());
        }
        final CreateMessageRequest request = CreateMessageRequest.builder()
                .messages(List.of(new SamplingMessage(Role.USER, new TextContent(question.user()))))
                .systemPrompt(question.system())
                .maxTokens(question.maxTokens())
                .build();
        final CreateMessageResult result;
        try {
            result = ToolDeadline.paused(() -> exchange.createMessage(request).block(timeout));
        } catch (final RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("The call was cancelled while the client's model answered.");
            }
            throw new SideCallException("the client's model didn't answer: " + NullSafe.nonBlankStringElse(
                    e.getMessage(), e.getClass().getSimpleName()), e);
        }
        if (result == null || !(result.content() instanceof final TextContent text)
            || NullSafe.isBlankString(text.text())) {
            throw new SideCallException("the client's model gave no text");
        }
        return new Answer(text.text().strip(), 0, 0);
    }
}
