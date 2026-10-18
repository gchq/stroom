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
import stroom.ai.impl.UserActionRecord.Status;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.processor.api.ProcessorFilterService;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Does what a button that a tool's result offered does, when the user clicks it (see [UserActions]).
///
/// The click is the user's consent, so it is only done for the user who owns the chat, once, within its expiry,
/// and only if it can still be done. A filter is changed as the user, so their permissions apply. Each click is
/// recorded in the audit log, and noted in the chat so the model knows on its next turn.
public class UserActionService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserActionService.class);

    private final AiService aiService;
    private final AiDao aiDao;
    private final UserActions userActions;
    private final UserActionLookup lookup;
    private final ProcessorFilterService processorFilterService;
    private final AiAuditLog auditLog;

    @Inject
    UserActionService(final AiService aiService,
                      final AiDao aiDao,
                      final UserActions userActions,
                      final UserActionLookup lookup,
                      final ProcessorFilterService processorFilterService,
                      final AiAuditLog auditLog) {
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.userActions = userActions;
        this.lookup = lookup;
        this.processorFilterService = processorFilterService;
        this.auditLog = auditLog;
    }

    /// Does a user action, if it can still be done.
    ///
    /// @param chatId    The chat, which the user must own.
    /// @param messageId The tool result message the action is in.
    /// @param actionId  The action's id.
    /// @return What happened.
    public AiUserActionResult doUserAction(final int chatId, final int messageId, final String actionId) {
        aiService.verifyOwnership(chatId);
        final AiChatMessage message = aiDao.getMessagesSince(chatId, messageId - 1).stream()
                .filter(m -> m.getId() == messageId && m.getMessageType() == AiMessageType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
        final ToolResultRecord result = message == null
                ? null
                : read(message.getMessage());
        final UserActionRecord action = result == null
                ? null
                : find(result, actionId);
        if (action == null) {
            return new AiUserActionResult(false, "This action is not in the chat.", null);
        }
        if (action.status() != Status.PENDING) {
            return new AiUserActionResult(false, "This has already been done.", null);
        }
        if (System.currentTimeMillis() > action.expiresMs()) {
            return new AiUserActionResult(false, "This button has expired. Ask again for an up-to-date one.", null);
        }

        return switch (action.action()) {
            case UserActions.OPEN_DOCUMENT -> openDocument(chatId, action);
            case UserActions.ENABLE_PROCESSOR_FILTER, UserActions.DISABLE_PROCESSOR_FILTER ->
                changeFilter(chatId, message, result, action);
            case UserActions.CREATE_QUERY -> createQuery(chatId, action);
            // Only shown, for the cluster admin to run. Stroom never runs these.
            default -> new AiUserActionResult(false, "Stroom does not run this action.", null);
        };
    }

    private AiUserActionResult openDocument(final int chatId, final UserActionRecord action) {
        // Opening a document changes nothing, so the button can be used again.
        final Optional<?> found = action.doc() == null
                ? Optional.empty()
                : lookup.find(action.doc());
        if (found.isEmpty()) {
            auditLog.userAction(chatId, action.action(), action.label(), "Not found", false);
            return new AiUserActionResult(false, "The document no longer exists, or you cannot see it.", null);
        }
        auditLog.userAction(chatId, action.action(), action.label(), "Opened", true);
        return new AiUserActionResult(true, null, action.doc());
    }

    /// Lets the browser open Stroom's New Query dialog with the suggested name, if the user may still run the
    /// query. Nothing is created here: the user creates the query in the dialog, which checks their permission to
    /// create it in the folder they choose and logs it as Stroom logs any new document. As it changes nothing, the
    /// button can be used again (e.g. after the dialog was cancelled).
    private AiUserActionResult createQuery(final int chatId, final UserActionRecord action) {
        if (action.request() == null || lookup.usableDataSource(action.request()).isEmpty()) {
            final String problem = "The query's data source no longer exists, or you can't use it.";
            auditLog.userAction(chatId, action.action(), action.label(), problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        auditLog.userAction(chatId, action.action(), action.label(), "Opened the New Query dialog", true);
        return new AiUserActionResult(true, null, null, action.name(), action.request());
    }

    private AiUserActionResult changeFilter(final int chatId,
                                            final AiChatMessage message,
                                            final ToolResultRecord result,
                                            final UserActionRecord action) {
        final boolean enable = UserActions.ENABLE_PROCESSOR_FILTER.equals(action.action());
        final Optional<String> problem = userActions.recheck(action);
        if (problem.isPresent()) {
            update(message, result, action.withStatus(Status.FAILED, problem.get()));
            auditLog.userAction(chatId, action.action(), action.label(), problem.get(), false);
            return new AiUserActionResult(false, problem.get(), null);
        }

        // Claim the button first, so a second click (e.g. from another tab) does nothing.
        final String done = (enable
                ? "Enabled"
                : "Disabled") + " processor filter " + action.filterId() + ".";
        final String claimed = update(message, result, action.withStatus(Status.DONE, done));
        if (claimed == null) {
            return new AiUserActionResult(false, "This has already been done.", null);
        }
        try {
            processorFilterService.setEnabled(action.filterId(), enable);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to change processor filter " + action.filterId(), e);
            final String failed = "Unable to " + (enable
                    ? "enable"
                    : "disable") + " processor filter " + action.filterId() + ": " + e.getMessage();
            aiDao.compareAndSetMessageText(message.getId(), claimed,
                    JsonUtil.writeValueAsString(replace(result, action.withStatus(Status.FAILED, failed)), false));
            auditLog.userAction(chatId, action.action(), action.label(), failed, false);
            return new AiUserActionResult(false, failed, null);
        }
        auditLog.userAction(chatId, action.action(), action.label(), done, true);
        // So the model knows on its next turn (or now, if it is working).
        final String note = "(With a button in the chat) I " + (enable
                ? "enabled"
                : "disabled") + " processor filter " + action.filterId() + " on pipeline '"
                            + action.doc().getName() + "'.";
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, note);
        return new AiUserActionResult(true, done, null);
    }

    /// Writes the result with the action changed, if the message has not changed since it was read.
    ///
    /// @return The message's new text, or null if it had changed.
    private String update(final AiChatMessage message, final ToolResultRecord result, final UserActionRecord action) {
        final String text = JsonUtil.writeValueAsString(replace(result, action), false);
        return aiDao.compareAndSetMessageText(message.getId(), message.getMessage(), text)
                ? text
                : null;
    }

    private static ToolResultRecord replace(final ToolResultRecord result, final UserActionRecord action) {
        final List<UserActionRecord> actions = new ArrayList<>();
        for (final UserActionRecord existing : NullSafe.list(result.userActions())) {
            actions.add(existing.id().equals(action.id())
                    ? action
                    : existing);
        }
        return new ToolResultRecord(result.callId(), result.tool(), result.error(), result.text(), result.path(),
                result.chars(), result.durationMs(), actions);
    }

    private static UserActionRecord find(final ToolResultRecord result, final String actionId) {
        return NullSafe.list(result.userActions()).stream()
                .filter(action -> action.id().equals(actionId))
                .findFirst()
                .orElse(null);
    }

    private static ToolResultRecord read(final String json) {
        try {
            return JsonUtil.readValue(json, ToolResultRecord.class);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read a tool result", e);
            return null;
        }
    }
}
