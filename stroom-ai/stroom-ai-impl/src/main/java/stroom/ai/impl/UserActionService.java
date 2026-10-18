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
import stroom.ai.impl.UserActionRecord.CodeChange;
import stroom.ai.impl.UserActionRecord.DashboardChange;
import stroom.ai.impl.UserActionRecord.Status;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.docref.DocRef;
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

    /// What a proposed code change's card says once it has been applied.
    static final String APPLIED = "Applied, not saved";
    /// What a proposed dashboard change's card says once it has been applied.
    static final String APPLIED_DASHBOARD = "Applied, not saved. Save the dashboard to keep it, or close it without "
                                            + "saving to go back.";
    static final String APPLY_COMMAND = "Apply proposed code change";
    static final String UNDO_COMMAND = "Undo applied code change";
    static final String APPLY_DASHBOARD_COMMAND = "Apply proposed dashboard change";
    private static final String NOT_IN_CHAT = "This action is not in the chat.";

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
        final AiChatMessage message = findMessage(chatId, messageId);
        final ToolResultRecord result = message == null
                ? null
                : read(message.getMessage());
        final UserActionRecord action = result == null
                ? null
                : find(result, actionId);
        if (action == null) {
            return new AiUserActionResult(false, NOT_IN_CHAT, null);
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
            case UserActions.APPLY_CODE_CHANGE -> applyCodeChange(chatId, message, result, action);
            case UserActions.APPLY_DASHBOARD_CHANGE -> applyDashboardChange(chatId, message, result, action);
            // Only shown, for the cluster admin to run. Stroom never runs these.
            default -> new AiUserActionResult(false, "Stroom does not run this action.", null);
        };
    }

    /// Records that the user undid a proposed code change they had applied to a stepping element's editor with a
    /// button in the chat, if they may still edit the element's document. The browser puts the editor's previous
    /// text back once this says it may; nothing is saved. The button can then be used to apply the change again.
    ///
    /// @param chatId    The chat, which the user must own.
    /// @param messageId The tool result message the action is in.
    /// @param actionId  The action's id.
    /// @return What happened.
    public AiUserActionResult undoUserAction(final int chatId, final int messageId, final String actionId) {
        aiService.verifyOwnership(chatId);
        final AiChatMessage message = findMessage(chatId, messageId);
        final ToolResultRecord result = message == null
                ? null
                : read(message.getMessage());
        final UserActionRecord action = result == null
                ? null
                : find(result, actionId);
        if (action == null) {
            return new AiUserActionResult(false, NOT_IN_CHAT, null);
        }
        if (!UserActions.APPLY_CODE_CHANGE.equals(action.action()) || action.status() != Status.DONE) {
            return new AiUserActionResult(false, "There is no applied change to undo.", null);
        }
        final String target = describeTarget(action);
        if (!lookup.canEdit(action.doc())) {
            final String problem = "You don't have permission to edit " + describeDoc(action) + ", so the change "
                                   + "can't be undone here. Use Ctrl+Z in the editor.";
            auditLog.processed(chatId, action.doc(), UNDO_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        final String outcome = "Undid the applied change, not saved";
        if (update(message, result, action.withStatus(Status.PENDING, outcome)) == null) {
            return new AiUserActionResult(false, "This has already changed. Try again.", null);
        }
        auditLog.processed(chatId, action.doc(), UNDO_COMMAND, target,
                "Undid an applied change: " + lineCounts(action), true);
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I undid the "
                                                                     + "proposed change I had applied to "
                                                                     + target + ". Nothing was saved.");
        return new AiUserActionResult(true, outcome, null);
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

    /// Lets the browser apply a proposed code change to a stepping element's editor, unsaved, if the user may edit
    /// the element's document (as editing it by hand needs). The button is claimed first, so it applies once; it
    /// is put back if the change is undone. Nothing is saved: a later Save is logged by the document's own Save.
    private AiUserActionResult applyCodeChange(final int chatId,
                                               final AiChatMessage message,
                                               final ToolResultRecord result,
                                               final UserActionRecord action) {
        final String target = describeTarget(action);
        if (action.doc() == null || action.codeChange() == null || lookup.find(action.doc()).isEmpty()) {
            final String problem = "The document no longer exists, or you cannot see it.";
            auditLog.processed(chatId, action.doc(), APPLY_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        if (!lookup.canEdit(action.doc())) {
            final String problem = "You don't have permission to edit " + describeDoc(action)
                                   + ", so the change can't be applied.";
            auditLog.processed(chatId, action.doc(), APPLY_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        if (update(message, result, action.withStatus(Status.DONE, APPLIED)) == null) {
            return new AiUserActionResult(false, "This has already been done.", null);
        }
        auditLog.processed(chatId, action.doc(), APPLY_COMMAND, target,
                "Applied a proposed change, not saved: " + lineCounts(action), true);
        // So the model knows on its next turn (or now, if it is working).
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I applied the "
                                                                     + "proposed change to " + target + " ("
                                                                     + lineCounts(action) + "). It is not saved.");
        return new AiUserActionResult(true, APPLIED, null);
    }

    /// Lets the browser apply a proposed change to a dashboard table, unsaved, if the user may edit the dashboard
    /// (as changing it by hand needs). The browser has already checked the dashboard hasn't changed since the
    /// proposal and, if it had unsaved changes, saved them with the dashboard's own Save (logged by that Save).
    /// The button is claimed first, so it applies once. Nothing is saved here or after.
    private AiUserActionResult applyDashboardChange(final int chatId,
                                                    final AiChatMessage message,
                                                    final ToolResultRecord result,
                                                    final UserActionRecord action) {
        final DashboardChange change = action.dashboardChange();
        final String target = change == null
                ? describeDoc(action)
                : "table '" + change.component() + "' on " + describeDoc(action);
        final String operations = change == null
                ? ""
                : String.join("; ", NullSafe.list(change.changes()));
        if (action.doc() == null || change == null || lookup.find(action.doc()).isEmpty()) {
            final String problem = "The dashboard no longer exists, or you cannot see it.";
            auditLog.processed(chatId, action.doc(), APPLY_DASHBOARD_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        if (!lookup.canEdit(action.doc())) {
            final String problem = "You don't have permission to edit " + describeDoc(action)
                                   + ", so the change can't be applied.";
            auditLog.processed(chatId, action.doc(), APPLY_DASHBOARD_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        if (update(message, result, action.withStatus(Status.DONE, APPLIED_DASHBOARD)) == null) {
            return new AiUserActionResult(false, "This has already been done.", null);
        }
        auditLog.processed(chatId, action.doc(), APPLY_DASHBOARD_COMMAND, target + ": " + operations,
                "Applied a proposed change, not saved: " + operations, true);
        // So the model knows on its next turn (or now, if it is working).
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I applied the "
                                                                     + "proposed change to " + target + " ("
                                                                     + operations + "). It is not saved.");
        return new AiUserActionResult(true, APPLIED_DASHBOARD, null);
    }

    private static String describeDoc(final UserActionRecord action) {
        final DocRef doc = action.doc();
        return doc == null
                ? "the document"
                : doc.getType() + " '" + NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid()) + "'";
    }

    private static String describeTarget(final UserActionRecord action) {
        final CodeChange change = action.codeChange();
        if (change == null) {
            return describeDoc(action);
        }
        return describeDoc(action) + " in element '" + change.element() + "' of pipeline '"
               + NullSafe.nonBlankStringElse(NullSafe.get(change.pipeline(), DocRef::getName), "?")
               + "', while stepping";
    }

    private static String lineCounts(final UserActionRecord action) {
        final CodeChange change = action.codeChange();
        return change == null
                ? "no lines"
                : "+" + change.added() + " −" + change.removed() + " lines";
    }

    private AiChatMessage findMessage(final int chatId, final int messageId) {
        return aiDao.getMessagesSince(chatId, messageId - 1).stream()
                .filter(m -> m.getId() == messageId && m.getMessageType() == AiMessageType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
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
