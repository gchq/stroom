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
import stroom.ai.impl.ToolApprovalRequest.Decision;
import stroom.ai.impl.ToolApprovalRequest.PendingCall;
import stroom.ai.impl.ToolApprovalRequest.Status;
import stroom.ai.shared.AiApprovalScope;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolApproval;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Records the user's answers to requests to approve tool calls (APPROVAL_REQUEST messages of kind TOOL), and the
/// approvals that last for the chat. Answers are written with a compare-and-set, so if the same request is
/// answered twice at once (e.g. from two browser tabs) only one answer counts. A request not answered within 24
/// hours expires: none of its calls are made, and the run waiting for it stops.
public class ToolApprovalService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ToolApprovalService.class);

    static final Duration EXPIRY = Duration.ofHours(24);

    private final AiService aiService;
    private final AiDao aiDao;
    private final AiToolApprovalDao approvalDao;
    private final AiChatRunManager runManager;
    private final SecurityContext securityContext;

    @Inject
    ToolApprovalService(final AiService aiService,
                        final AiDao aiDao,
                        final AiToolApprovalDao approvalDao,
                        final AiChatRunManager runManager,
                        final SecurityContext securityContext) {
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.approvalDao = approvalDao;
        this.runManager = runManager;
        this.securityContext = securityContext;
    }

    /// @param message A message.
    /// @return The tool approval request it holds, if it is one.
    public static Optional<ToolApprovalRequest> read(final AiChatMessage message) {
        if (message.getMessageType() != AiMessageType.APPROVAL_REQUEST) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JsonUtil.readValue(message.getMessage(), ToolApprovalRequest.class))
                    .filter(request -> ToolApprovalRequest.KIND.equals(request.kind()));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    /// @param chatId The chat.
    /// @return The chat's tool approval request that is waiting for an answer, if it has one.
    public Optional<AiChatMessage> pending(final int chatId) {
        return aiService.getMessages(chatId).reversed().stream()
                .filter(message -> read(message)
                        .filter(request -> request.status() == Status.PENDING)
                        .isPresent())
                .findFirst();
    }

    /// Records the user's answer to some or all of a request's calls.
    ///
    /// @param chatId  The chat.
    /// @param message The APPROVAL_REQUEST message.
    /// @param answer  The answer.
    /// @return The request's status after the answer: ANSWERED once every call is decided, when the run can
    /// carry on.
    public Status answer(final int chatId, final AiChatMessage message, final AnswerAiApprovalRequest answer) {
        final ToolApprovalRequest request = read(message)
                .orElseThrow(() -> new RuntimeException("Request " + message.getId() + " is not for tool calls."));
        if (request.status() != Status.PENDING) {
            throw new RuntimeException("This request has already been answered.");
        }
        if (isExpired(message)) {
            expire(message, request);
            return Status.EXPIRED;
        }
        final String owner = NullSafe.get(aiService.getChat(chatId), AiChat::getUserUuid);
        final String user = securityContext.getUserIdentityForAudit();
        final List<PendingCall> calls = new ArrayList<>();
        for (final PendingCall call : request.calls()) {
            if (call.decision() == null
                && (answer.getCallIds() == null || answer.getCallIds().contains(call.callId()))) {
                if (answer.isApproved()) {
                    remember(chatId, owner, user, request, call, answer.getScope());
                    calls.add(call.decided(Decision.ALLOWED, null));
                } else {
                    calls.add(call.decided(Decision.DENIED, answer.getReason()));
                }
            } else {
                calls.add(call);
            }
        }
        final ToolApprovalRequest answered = request.with(Status.PENDING, calls);
        final ToolApprovalRequest updated = answered.allDecided()
                ? answered.with(Status.ANSWERED, calls)
                : answered;
        if (!aiDao.compareAndSetMessageText(message.getId(), message.getMessage(),
                JsonUtil.writeValueAsString(updated, false))) {
            throw new RuntimeException("This request has already been answered.");
        }
        LOGGER.info(() -> "Tool approval " + message.getId() + " in chatId=" + chatId + " answered: "
                          + (answer.isApproved()
                ? "allowed (" + answer.getScope() + ")"
                : "denied"));
        return updated.status();
    }

    /// Denies all of a request's undecided calls, e.g. because the user sent a message instead of answering.
    ///
    /// @return True if it was denied (false if it had been answered in the meantime).
    public boolean denyAll(final AiChatMessage message, final String reason) {
        final ToolApprovalRequest request = read(message).orElse(null);
        if (request == null || request.status() != Status.PENDING) {
            return false;
        }
        final List<PendingCall> calls = request.calls().stream()
                .map(call -> call.decision() == null
                        ? call.decided(Decision.DENIED, reason)
                        : call)
                .toList();
        return aiDao.compareAndSetMessageText(message.getId(), message.getMessage(),
                JsonUtil.writeValueAsString(request.with(Status.ANSWERED, calls), false));
    }

    /// Expires a request not answered in time: none of its undecided calls are made, and the run waiting for it
    /// stops (it can be resumed).
    public void expire(final AiChatMessage message, final ToolApprovalRequest request) {
        final List<PendingCall> calls = request.calls().stream()
                .map(call -> call.decision() == null
                        ? call.decided(Decision.EXPIRED, null)
                        : call)
                .toList();
        if (aiDao.compareAndSetMessageText(message.getId(), message.getMessage(),
                JsonUtil.writeValueAsString(request.with(Status.EXPIRED, calls), false))) {
            LOGGER.info(() -> "Tool approval " + message.getId() + " in chatId=" + message.getChatId()
                              + " expired");
            runManager.stop(message.getChatId(), AiChatRunStopReason.APPROVAL_EXPIRED);
        }
    }

    /// @param chatId The chat.
    /// @return The decisions the user has made about tools in the chat.
    public List<AiToolApproval> list(final int chatId) {
        return approvalDao.list(chatId).stream()
                .map(approval -> new AiToolApproval(approval.id(), approval.serverKey(), approval.toolName(),
                        approval.decision()))
                .toList();
    }

    /// Removes a decision the user made about a tool in a chat, so they are asked again.
    ///
    /// @param chatId     The chat.
    /// @param approvalId The decision.
    /// @return True if it was removed, false if the chat has no such decision.
    public boolean remove(final int chatId, final int approvalId) {
        final boolean found = approvalDao.list(chatId).stream()
                .anyMatch(approval -> approval.id() == approvalId);
        if (found) {
            approvalDao.delete(approvalId);
        }
        return found;
    }

    /// Removes every decision the user made about tools in a chat, so they are asked again.
    ///
    /// @param chatId The chat.
    /// @return How many were removed.
    public int reset(final int chatId) {
        final List<AiToolApprovalDao.Approval> approvals = approvalDao.list(chatId);
        approvals.forEach(approval -> approvalDao.delete(approval.id()));
        return approvals.size();
    }

    /// @return True if the request was made too long ago to be answered.
    static boolean isExpired(final AiChatMessage message) {
        return System.currentTimeMillis() - message.getCreateTimeMs() > EXPIRY.toMillis();
    }

    private void remember(final int chatId,
                          final String owner,
                          final String user,
                          final ToolApprovalRequest request,
                          final PendingCall call,
                          final AiApprovalScope scope) {
        if (call.serverKey() == null || owner == null) {
            return;
        }
        if (scope == AiApprovalScope.CHAT && request.allowForChat() && call.toolHash() != null) {
            approvalDao.save(owner, chatId, call.serverKey(), call.serverTool(), call.toolHash(),
                    AiToolApprovalDecision.ALLOW, user);
        } else if (scope == AiApprovalScope.SERVER_READ_ONLY && request.allowServerReadOnly()
                   && call.serverHash() != null) {
            approvalDao.save(owner, chatId, call.serverKey(), AiToolApprovalDao.ALL_READ_ONLY_TOOLS,
                    call.serverHash(), AiToolApprovalDecision.ALLOW, user);
        }
    }
}
