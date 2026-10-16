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

import stroom.ai.impl.ElicitationRecord.Status;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/// Handles the forms MCP tools ask users to fill in while they run (MCP elicitation).
///
/// The form is stored as an ELICITATION message, which the user sees as a card with Respond…, and the run waits
/// for the user, so the chat shows as needing them. The tool call keeps its thread while it waits, so the forms
/// waiting on a node are limited, and each expires (S8). The answer goes to the MCP server, never to the model.
/// Only the MCP client can make these cards; nothing the model writes can.
@Singleton
public class McpElicitations {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpElicitations.class);

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(500);

    private final AiDao aiDao;
    private final AiChatRunDao runDao;
    private final AiChatRunManager runManager;
    private final Provider<AiConfig> aiConfigProvider;
    private final AtomicInteger waiting = new AtomicInteger();

    @Inject
    McpElicitations(final AiDao aiDao,
                    final AiChatRunDao runDao,
                    final AiChatRunManager runManager,
                    final Provider<AiConfig> aiConfigProvider) {
        this.aiDao = aiDao;
        this.runDao = runDao;
        this.runManager = runManager;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// Shows a form from an MCP server to the user, and waits for their answer.
    ///
    /// @param chatId  The chat whose tool call the server is working on.
    /// @param server  The server's name.
    /// @param request The form.
    /// @return The user's answer, or a decline or cancel if the form can't be shown, the user does not answer in
    /// time, or the run is stopped.
    ElicitResult handle(final int chatId, final String server, final ElicitFormRequest request) {
        final String problem = ElicitationSchema.check(request.message(), request.requestedSchema());
        if (problem != null) {
            warn(chatId, "A form from MCP tool server '" + server + "' was declined, as " + problem + ".");
            return new ElicitResult(Action.DECLINE, null);
        }
        final AiConfig config = aiConfigProvider.get();
        if (waiting.incrementAndGet() > config.getMaxElicitationsPerNode()) {
            waiting.decrementAndGet();
            warn(chatId, "A form from MCP tool server '" + server + "' was declined, as too many forms are "
                         + "waiting for answers. Try again later.");
            return new ElicitResult(Action.DECLINE, null);
        }
        try {
            // The run that made the tool call: its stop request is kept once it has ended, unlike the chat's.
            final AtomicBoolean stop = runManager.stopFlag(chatId);
            final long expiresMs = System.currentTimeMillis() + config.getElicitationTimeout().toMillis();
            final ElicitationRecord record = new ElicitationRecord(ElicitationRecord.KIND, Status.PENDING, server,
                    null, request.message(), request.requestedSchema(), expiresMs, null);
            final AiChatMessage message = aiDao.storeMessage(chatId, AiMessageType.ELICITATION,
                    JsonUtil.writeValueAsString(record, false));
            // The chat needs the user, but the run keeps its thread, so it is not carried on by anything else.
            final AiChatRun run = runDao.getActive(chatId).orElse(null);
            final boolean paused = run != null && runDao.compareAndSetStatus(run.getId(),
                    List.of(AiChatRunStatus.RUNNING), AiChatRunStatus.WAITING_FOR_USER,
                    AiChatRunStopReason.NEEDS_INPUT, null);
            try {
                return await(chatId, message.getId(), expiresMs, stop);
            } finally {
                if (paused) {
                    runDao.compareAndSetStatus(run.getId(), List.of(AiChatRunStatus.WAITING_FOR_USER),
                            AiChatRunStatus.RUNNING, null, null);
                }
            }
        } finally {
            waiting.decrementAndGet();
        }
    }

    /// Answers a form for the user.
    ///
    /// @param chatId    The chat.
    /// @param messageId The ELICITATION message.
    /// @param action    ACCEPT, DECLINE or CANCEL.
    /// @param answer    For ACCEPT, each field's value as text (a choice of several as a JSON list).
    /// @throws RuntimeException If the form has already been answered or has expired, or the answer does not fit
    ///                          the form.
    void answer(final int chatId, final int messageId, final String action, final Map<String, String> answer) {
        final AiChatMessage message = find(chatId, messageId)
                .orElseThrow(() -> new RuntimeException("There is no form " + messageId + " in this chat."));
        final ElicitationRecord record = read(message)
                .orElseThrow(() -> new RuntimeException("Message " + messageId + " is not a form."));
        if (record.status() != Status.PENDING) {
            throw new RuntimeException("This form has already been answered, or is no longer waiting.");
        }
        if (System.currentTimeMillis() > record.expiresMs()) {
            throw new RuntimeException("This form has expired.");
        }
        // E.g. the node running the tool has stopped.
        final boolean toolWaiting = runDao.getActive(chatId)
                .filter(run -> run.getStatus() == AiChatRunStatus.WAITING_FOR_USER
                               && run.getStopReason() == AiChatRunStopReason.NEEDS_INPUT)
                .isPresent();
        if (!toolWaiting) {
            aiDao.compareAndSetMessageText(messageId, message.getMessage(),
                    JsonUtil.writeValueAsString(record.answered(Status.CANCELLED, null), false));
            throw new RuntimeException("The tool that asked is no longer waiting for an answer.");
        }
        final ElicitationRecord answered = switch (NullSafe.string(action)) {
            case "ACCEPT" -> record.answered(Status.ACCEPTED, ElicitationSchema.content(record.schema(), answer));
            case "DECLINE" -> record.answered(Status.DECLINED, null);
            case "CANCEL" -> record.answered(Status.CANCELLED, null);
            default -> throw new IllegalArgumentException("Unknown action '" + action + "'.");
        };
        // Only one answer counts, e.g. if it is answered from two browser tabs at once.
        if (!aiDao.compareAndSetMessageText(messageId, message.getMessage(),
                JsonUtil.writeValueAsString(answered, false))) {
            throw new RuntimeException("This form has already been answered.");
        }
        LOGGER.info(() -> "Form " + messageId + " from '" + record.server() + "' in chatId=" + chatId + ": "
                          + answered.status());
    }

    /// @return How many forms are waiting for answers on this node.
    int waitingCount() {
        return waiting.get();
    }

    private ElicitResult await(final int chatId,
                               final int messageId,
                               final long expiresMs,
                               final AtomicBoolean stop) {
        while (true) {
            final AiChatMessage message = find(chatId, messageId).orElse(null);
            final ElicitationRecord record = message != null
                    ? read(message).orElse(null)
                    : null;
            if (record == null) {
                // Deleted, e.g. the chat's messages were deleted.
                return new ElicitResult(Action.CANCEL, null);
            }
            switch (record.status()) {
                case ACCEPTED:
                    return new ElicitResult(Action.ACCEPT, record.content());
                case DECLINED:
                    return new ElicitResult(Action.DECLINE, null);
                case CANCELLED:
                case EXPIRED:
                    return new ElicitResult(Action.CANCEL, null);
                default:
                    break;
            }
            // Stopped, including when the run has given up on the tool call and ended.
            final boolean stopped = stop.get();
            if (stopped || System.currentTimeMillis() > expiresMs) {
                final Status status = stopped
                        ? Status.CANCELLED
                        : Status.EXPIRED;
                if (aiDao.compareAndSetMessageText(messageId, message.getMessage(),
                        JsonUtil.writeValueAsString(record.answered(status, null), false))) {
                    LOGGER.debug(() -> "Form " + messageId + " in chatId=" + chatId + ": " + status);
                    return new ElicitResult(Action.CANCEL, null);
                }
                // Answered just now, so look again.
                continue;
            }
            try {
                Thread.sleep(CHECK_INTERVAL.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ElicitResult(Action.CANCEL, null);
            }
        }
    }

    private Optional<AiChatMessage> find(final int chatId, final int messageId) {
        return aiDao.getMessagesSince(chatId, messageId - 1).stream()
                .filter(message -> message.getId() == messageId
                                   && message.getMessageType() == AiMessageType.ELICITATION)
                .findFirst();
    }

    private static Optional<ElicitationRecord> read(final AiChatMessage message) {
        try {
            return Optional.ofNullable(JsonUtil.readValue(message.getMessage(), ElicitationRecord.class))
                    .filter(record -> ElicitationRecord.KIND.equals(record.kind()));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private void warn(final int chatId, final String text) {
        LOGGER.info(() -> "chatId=" + chatId + ": " + text);
        try {
            aiDao.storeMessage(chatId, AiMessageType.WARNING, text);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to store a warning for chatId=" + chatId, e);
        }
    }
}
