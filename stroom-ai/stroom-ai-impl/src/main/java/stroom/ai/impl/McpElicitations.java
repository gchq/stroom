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

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
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
    // Who asks the questions of Stroom AI's own agent.
    private static final String STROOM_AI = "Stroom AI";

    private final AiDao aiDao;
    private final AiChatRunDao runDao;
    private final AiChatRunManager runManager;
    private final Provider<AiConfig> aiConfigProvider;
    private final AtomicInteger waiting = new AtomicInteger();
    private AnswerLinks answerLinks;

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

    /// @param answerLinks Links the documents and streams a form's message names, as an answer's are.
    @Inject
    void setAnswerLinks(final AnswerLinks answerLinks) {
        this.answerLinks = answerLinks;
    }

    /// Shows a form from an MCP server to the user, and waits for their answer.
    ///
    /// @param chatId  The chat whose tool call the server is working on.
    /// @param server  The server's name.
    /// @param request The form.
    /// @return The user's answer, or a decline or cancel if too many forms are waiting, the user does not answer in
    /// time, or the run is stopped.
    /// @throws McpError If the form can never be shown (e.g. its message is too long), so that the server is told
    ///                  of an error rather than that the user declined, which they didn't.
    ElicitResult handle(final int chatId, final String server, final ElicitFormRequest request) {
        final String problem = ElicitationSchema.check(request.message(), request.requestedSchema());
        if (problem != null) {
            warn(chatId, "MCP tool server '" + server + "' sent a form that Stroom can't show, as " + problem
                         + ". This is a problem with the MCP server, not something you did. Stroom has told the "
                         + "server so, rather than that you declined. Ask an administrator to report it to whoever "
                         + "looks after the server.");
            throw McpError.builder(ErrorCodes.INVALID_PARAMS)
                    .message("The form can't be shown to the user, as " + problem + " (at most "
                             + ElicitationSchema.MAX_MESSAGE_CHARS + " characters of message, "
                             + ElicitationSchema.MAX_FIELDS + " fields). The user has not answered it.")
                    .build();
        }
        return handle(chatId, server, null, request);
    }

    /// Asks the user questions for Stroom AI's own agent (its ask_user tool), as a form shown in the chat.
    ///
    /// Asked in a tool call of the chat's run, the run doesn't wait for the answer: it stops, giving up its thread,
    /// and carries on from the call when the user answers (see [#answer]), however long that takes. The call is then
    /// made again, and given the answer. Asked outside a tool call, it waits, as for a form from an MCP server.
    ///
    /// @param chatId  The chat.
    /// @param tool    The tool asking, which says how the chat shows the form.
    /// @param callId  The id of the tool call asking, or null if it isn't asked in one.
    /// @param message What the questions are for.
    /// @param schema  The questions, as a form.
    /// @return The user's answer, or a decline or cancel if the form can't be shown, the user sent a message
    /// instead, or the run was stopped.
    /// @throws AiChatRunManager.WaitingForUserException if the run is to stop until the user answers.
    public ElicitResult askUser(final int chatId,
                                final String tool,
                                final String callId,
                                final String message,
                                final Map<String, Object> schema) {
        if (callId == null) {
            return handle(chatId, STROOM_AI, tool, new ElicitFormRequest(message, schema, null));
        }
        // Asked before the run stopped: the answer, if there is one now.
        final ElicitationRecord asked = findForCall(chatId, callId).flatMap(McpElicitations::read).orElse(null);
        if (asked != null) {
            return switch (asked.status()) {
                case ACCEPTED -> new ElicitResult(Action.ACCEPT, asked.content());
                case DECLINED -> new ElicitResult(Action.DECLINE, null);
                case CANCELLED, EXPIRED, SUPERSEDED -> new ElicitResult(Action.CANCEL, null);
                case PENDING -> throw waitForAnswers(chatId);
            };
        }
        final String problem = ElicitationSchema.check(message, schema);
        if (problem != null) {
            // Declined, as the form can never be shown as it is, and asking again would fail the same way.
            warn(chatId, "A form from " + STROOM_AI + " was declined, as " + problem + ".");
            return new ElicitResult(Action.DECLINE, null);
        }
        // It waits for as long as the user takes, as nothing is held while it does.
        aiDao.storeMessage(chatId, AiMessageType.ELICITATION, JsonUtil.writeValueAsString(
                new ElicitationRecord(ElicitationRecord.KIND, Status.PENDING, STROOM_AI, tool,
                        withLinks(chatId, message), schema, Long.MAX_VALUE, null, callId), false));
        throw waitForAnswers(chatId);
    }

    // The message with the documents and streams the chat's tools gave linked (asked for by the user: seen, the
    // pipeline a form names not opening from it), as an answer's are; as it is if they can't be. Checked for
    // size before, as the server sent it.
    private String withLinks(final int chatId, final String message) {
        return answerLinks == null
                ? message
                : answerLinks.link(chatId, message);
    }

    private static AiChatRunManager.WaitingForUserException waitForAnswers(final int chatId) {
        return new AiChatRunManager.WaitingForUserException("Questions in chat " + chatId + " need answers",
                AiChatRunStopReason.NEEDS_ANSWERS);
    }

    private ElicitResult handle(final int chatId,
                                final String server,
                                final String tool,
                                final ElicitFormRequest request) {
        final String problem = ElicitationSchema.check(request.message(), request.requestedSchema());
        if (problem != null) {
            // Only Stroom AI's own ask_user gets here (MCP servers' forms are checked first): declined, as the
            // form can never be shown as it is, and asking again would fail the same way.
            warn(chatId, "A form from " + server + " was declined, as " + problem + ".");
            return new ElicitResult(Action.DECLINE, null);
        }
        final AiConfig config = aiConfigProvider.get();
        if (waiting.incrementAndGet() > config.getMaxElicitationsPerNode()) {
            waiting.decrementAndGet();
            warn(chatId, "A form from MCP tool server '" + server + "' could not be shown, as too many forms "
                         + "are waiting for answers on this Stroom node. You didn't decline it, but Stroom had to "
                         + "answer the server with a decline, so the tool may report that you did not agree. Ask "
                         + "the agent to try again in a few minutes. If this keeps happening, ask an administrator "
                         + "to raise maxElicitationsPerNode.");
            return new ElicitResult(Action.DECLINE, null);
        }
        try {
            // The run that made the tool call: its stop request is kept once it has ended, unlike the chat's.
            final AtomicBoolean stop = runManager.stopFlag(chatId);
            final long expiresMs = System.currentTimeMillis() + config.getElicitationTimeout().toMillis();
            final ElicitationRecord record = new ElicitationRecord(ElicitationRecord.KIND, Status.PENDING, server,
                    tool, withLinks(chatId, request.message()), request.requestedSchema(), expiresMs, null, null);
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
    /// @return True if the run stopped to wait for the answer, so is to be carried on now (questions from Stroom AI's
    /// own agent); false if the tool call that asked is waiting for it.
    /// @throws RuntimeException If the form has already been answered or has expired, or the answer does not fit
    ///                          the form.
    boolean answer(final int chatId, final int messageId, final String action, final Map<String, String> answer) {
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
        // The run stopped to wait for questions from Stroom AI's own agent; a form from an MCP server has its tool
        // call waiting for it.
        final boolean parked = record.callId() != null;
        final boolean canAnswer = parked
                ? canCarryOn(chatId, messageId)
                : isWaiting(chatId, AiChatRunStopReason.NEEDS_INPUT);
        // E.g. the node running the tool has stopped, or the user has asked something else since.
        if (!canAnswer) {
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
        return parked;
    }

    /// Puts aside the forms in a chat that are waiting for answers, as the user has sent a message instead: the tools
    /// that asked are told they weren't answered, and the run carries on, with the message.
    ///
    /// @param chatId The chat.
    /// @return How many forms were put aside.
    int supersedePending(final int chatId) {
        int count = 0;
        for (final AiChatMessage message : aiDao.getMessages(chatId)) {
            if (message.getMessageType() != AiMessageType.ELICITATION) {
                continue;
            }
            final ElicitationRecord record = read(message).orElse(null);
            // Only one answer counts, e.g. if the form is answered as the message is sent.
            if (record != null && record.status() == Status.PENDING
                && aiDao.compareAndSetMessageText(message.getId(), message.getMessage(),
                    JsonUtil.writeValueAsString(record.answered(Status.SUPERSEDED, null), false))) {
                LOGGER.debug(() -> "Form " + message.getId() + " in chatId=" + chatId + ": " + Status.SUPERSEDED);
                count++;
            }
        }
        return count;
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
                case SUPERSEDED:
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

    // True if the chat's run is waiting for the user, for the reason given.
    private boolean isWaiting(final int chatId, final AiChatRunStopReason reason) {
        return runDao.getActive(chatId)
                .filter(run -> run.getStatus() == AiChatRunStatus.WAITING_FOR_USER && run.getStopReason() == reason)
                .isPresent();
    }

    // True if the answer to questions a run stopped for can carry the chat on: its run is waiting for them, or has
    // ended (e.g. it was stopped, or the user's session ended) while they are part of the chat's latest question,
    // so a new run can carry on from them.
    private boolean canCarryOn(final int chatId, final int messageId) {
        if (runDao.getActive(chatId).isPresent()) {
            return isWaiting(chatId, AiChatRunStopReason.NEEDS_ANSWERS);
        }
        final int latestQuestionId = aiDao.getMessages(chatId).stream()
                .filter(message -> message.getMessageType() == AiMessageType.USER_MESSAGE)
                .mapToInt(AiChatMessage::getId)
                .max()
                .orElse(Integer.MAX_VALUE);
        return messageId > latestQuestionId;
    }

    // The questions a tool call asked, in a run that stopped to wait for the answers.
    private Optional<AiChatMessage> findForCall(final int chatId, final String callId) {
        return aiDao.getMessages(chatId).stream()
                .filter(message -> message.getMessageType() == AiMessageType.ELICITATION)
                .filter(message -> read(message).map(record -> callId.equals(record.callId())).orElse(false))
                .reduce((first, second) -> second);
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
