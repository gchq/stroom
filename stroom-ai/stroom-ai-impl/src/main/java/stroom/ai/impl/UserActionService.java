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
import stroom.util.shared.diff.DiffBlock;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
    static final String APPLIED_DASHBOARD = "Applied, not saved. Save the dashboard to keep it, or use Undo apply.";
    static final String APPLY_COMMAND = "Apply proposed code change";
    static final String UNDO_COMMAND = "Undo applied code change";
    static final String APPLY_DASHBOARD_COMMAND = "Apply proposed dashboard change";
    static final String UNDO_DASHBOARD_COMMAND = "Undo applied dashboard change";
    private static final String NOT_IN_CHAT = "This action is not in the chat.";

    /// The most lines of each rejected part of a code change described to the model, on each side.
    static final int MAX_DESCRIBED_LINES = 10;

    private final AiService aiService;
    private final AiDao aiDao;
    private final UserActions userActions;
    private final UserActionLookup lookup;
    private final ProcessorFilterService processorFilterService;
    private final AiAuditLog auditLog;
    private final DashboardBuilder dashboardBuilder;

    @Inject
    UserActionService(final AiService aiService,
                      final AiDao aiDao,
                      final UserActions userActions,
                      final UserActionLookup lookup,
                      final ProcessorFilterService processorFilterService,
                      final AiAuditLog auditLog,
                      final DashboardBuilder dashboardBuilder) {
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.userActions = userActions;
        this.lookup = lookup;
        this.processorFilterService = processorFilterService;
        this.auditLog = auditLog;
        this.dashboardBuilder = dashboardBuilder;
    }

    /// Does a user action, if it can still be done.
    ///
    /// @param chatId    The chat, which the user must own.
    /// @param messageId The tool result message the action is in.
    /// @param actionId  The action's id.
    /// @return What happened.
    public AiUserActionResult doUserAction(final int chatId, final int messageId, final String actionId) {
        return doUserAction(chatId, messageId, actionId, null);
    }

    /// Does a user action, if it can still be done, leaving out the parts of a proposed code change the user
    /// rejected in its review.
    ///
    /// @param chatId          The chat, which the user must own.
    /// @param messageId       The tool result message the action is in.
    /// @param actionId        The action's id.
    /// @param rejectedChanges For a proposed code change, the parts the user rejected, by their index among the
    ///                        change's changed blocks, comma separated; null or blank for none.
    /// @return What happened.
    public AiUserActionResult doUserAction(final int chatId,
                                           final int messageId,
                                           final String actionId,
                                           final String rejectedChanges) {
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
        final Set<Integer> rejected;
        try {
            rejected = parseRejected(rejectedChanges);
        } catch (final NumberFormatException e) {
            return new AiUserActionResult(false, "The rejected parts of the change weren't understood.", null);
        }
        if (!rejected.isEmpty() && !isCodeChange(action)) {
            return new AiUserActionResult(false, "Only a proposed code change can be applied in part.", null);
        }

        return switch (action.action()) {
            case UserActions.OPEN_DOCUMENT -> openDocument(chatId, action);
            case UserActions.ENABLE_PROCESSOR_FILTER, UserActions.DISABLE_PROCESSOR_FILTER ->
                changeFilter(chatId, message, result, action);
            case UserActions.CREATE_QUERY -> createQuery(chatId, action);
            case UserActions.CREATE_DASHBOARD -> createDashboard(chatId, action);
            case UserActions.APPLY_CODE_CHANGE, UserActions.APPLY_QUERY_CHANGE ->
                applyCodeChange(chatId, message, result, action, rejected);
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
        if (UserActions.APPLY_DASHBOARD_CHANGE.equals(action.action()) && action.status() == Status.DONE) {
            return undoDashboardChange(chatId, message, result, action);
        }
        if (!isCodeChange(action) || action.status() != Status.DONE) {
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

    /// Lets the browser open Stroom's New Dashboard dialog with the suggested name, giving it the dashboard's
    /// settings, built again from the query now, if the user may still run the query. Nothing is created here: the
    /// user creates the dashboard in the dialog, which checks their permission to create it in the folder they
    /// choose and logs it as Stroom logs any new document. As it changes nothing, the button can be used again
    /// (e.g. after the dialog was cancelled).
    private AiUserActionResult createDashboard(final int chatId, final UserActionRecord action) {
        if (action.request() == null || action.newDashboard() == null
            || lookup.usableDataSource(action.request()).isEmpty()) {
            final String problem = "The query's data source no longer exists, or you can't use it.";
            auditLog.userAction(chatId, action.action(), action.label(), problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        final DashboardBuilder.Built built;
        try {
            built = dashboardBuilder.build(action.name(), action.request(), action.newDashboard().queryType(),
                    action.newDashboard().preview());
        } catch (final IllegalArgumentException e) {
            final String problem = "The dashboard can no longer be built from the query: " + e.getMessage();
            auditLog.userAction(chatId, action.action(), action.label(), problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        auditLog.userAction(chatId, action.action(), action.label(), "Opened the New Dashboard dialog", true);
        return AiUserActionResult.createDashboard(action.name(), built.config());
    }

    /// Lets the browser apply a proposed code change to a stepping element's editor, unsaved, if the user may edit
    /// the element's document (as editing it by hand needs). The button is claimed first, so it applies once; it
    /// is put back if the change is undone. Nothing is saved: a later Save is logged by the document's own Save.
    private AiUserActionResult applyCodeChange(final int chatId,
                                               final AiChatMessage message,
                                               final ToolResultRecord result,
                                               final UserActionRecord action,
                                               final Set<Integer> rejected) {
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
        final LineDiffResult proposed = LineDiff.diff(action.codeChange().baseCode(), action.request());
        final int parts = proposed.getChangedBlocks().size();
        if (rejected.stream().anyMatch(index -> index < 0 || index >= parts)) {
            return new AiUserActionResult(false, "The rejected parts aren't parts of this change.", null);
        }
        if (!rejected.isEmpty() && rejected.size() >= parts) {
            return new AiUserActionResult(false, "Every part of the change was rejected, so there is nothing to "
                                                 + "apply.", null);
        }
        if (rejected.isEmpty()) {
            if (update(message, result, action.withStatus(Status.DONE, APPLIED)) == null) {
                return new AiUserActionResult(false, "This has already been done.", null);
            }
            auditLog.processed(chatId, action.doc(), APPLY_COMMAND, target,
                    "Applied a proposed change, not saved: " + lineCounts(action), true);
            // So the model knows on its next turn (or now, if it is working).
            aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I applied the "
                                                                         + "proposed change to " + target + " ("
                                                                         + lineCounts(action) + "). It is not "
                                                                         + "saved.");
            return new AiUserActionResult(true, APPLIED, null);
        }

        // Worked out here, as the browser does, so what is recorded is what the user applied.
        final String baseCode = action.codeChange().baseCode();
        final LineDiffResult applied = LineDiff.diff(baseCode,
                proposed.withChangesExcept(rejected, LineDiff.lineSeparatorOf(baseCode)));
        final String counts = "+" + applied.getAddedLineCount() + " −" + applied.getRemovedLineCount()
                              + " lines, " + (parts - rejected.size()) + " of " + parts + " parts";
        final String outcome = APPLIED + " (" + (parts - rejected.size()) + " of " + parts + " parts)";
        if (update(message, result, action.withStatus(Status.DONE, outcome)) == null) {
            return new AiUserActionResult(false, "This has already been done.", null);
        }
        auditLog.processed(chatId, action.doc(), APPLY_COMMAND, target,
                "Applied part of a proposed change, not saved: " + counts, true);
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I applied part of "
                                                                     + "the proposed change to " + target + " ("
                                                                     + counts + "). It is not saved. I rejected "
                                                                     + "these parts, so they weren't applied:\n"
                                                                     + describeParts(proposed, rejected));
        return new AiUserActionResult(true, outcome, null);
    }

    /// @param rejectedChanges The rejected parts, comma separated, or null.
    /// @return Them, by index.
    /// @throws NumberFormatException if one isn't a number.
    static Set<Integer> parseRejected(final String rejectedChanges) {
        final Set<Integer> rejected = new TreeSet<>();
        if (NullSafe.isBlankString(rejectedChanges)) {
            return rejected;
        }
        for (final String part : rejectedChanges.split(",")) {
            if (!part.isBlank()) {
                rejected.add(Integer.parseInt(part.strip()));
            }
        }
        return rejected;
    }

    /// @return Each rejected part of a change, as the lines it would have removed and added, for the model.
    static String describeParts(final LineDiffResult diff, final Set<Integer> rejected) {
        final StringBuilder sb = new StringBuilder();
        final List<DiffBlock> blocks = diff.getChangedBlocks();
        for (final int index : rejected) {
            final DiffBlock block = blocks.get(index);
            sb.append("- Part ").append(index + 1).append(", at line ").append(block.getOldFrom() + 1);
            if (isFinalLineBreak(diff, block)) {
                sb.append(": the ").append(block.getOldCount() > 0
                        ? "removal"
                        : "addition").append(" of the final line break\n");
                continue;
            }
            sb.append(":\n");
            appendLines(sb, "-", diff.getOldLines().subList(block.getOldFrom(), block.getOldTo()));
            appendLines(sb, "+", diff.getNewLines().subList(block.getNewFrom(), block.getNewTo()));
        }
        return sb.toString().stripTrailing();
    }

    // A part that only removes or adds the empty last line: the text's final line break.
    private static boolean isFinalLineBreak(final LineDiffResult diff, final DiffBlock block) {
        if (block.getOldCount() == 1 && block.getNewCount() == 0) {
            return block.getOldTo() == diff.getOldLines().size() && diff.getOldLines().getLast().isEmpty();
        }
        if (block.getOldCount() == 0 && block.getNewCount() == 1) {
            return block.getNewTo() == diff.getNewLines().size() && diff.getNewLines().getLast().isEmpty();
        }
        return false;
    }

    private static void appendLines(final StringBuilder sb, final String prefix, final List<String> lines) {
        lines.stream()
                .limit(MAX_DESCRIBED_LINES)
                .forEach(line -> sb.append("  ").append(prefix).append(' ').append(line).append('\n'));
        if (lines.size() > MAX_DESCRIBED_LINES) {
            sb.append("  ").append(prefix).append(" … and ").append(lines.size() - MAX_DESCRIBED_LINES)
                    .append(" more line(s)\n");
        }
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

    /// Records that the user undid a proposed dashboard change they had applied, if they may still edit the
    /// dashboard. The browser puts the table back as it was once this says it may; nothing is saved. The button
    /// can then be used to apply the change again.
    private AiUserActionResult undoDashboardChange(final int chatId,
                                                   final AiChatMessage message,
                                                   final ToolResultRecord result,
                                                   final UserActionRecord action) {
        final DashboardChange change = action.dashboardChange();
        final String target = change == null
                ? describeDoc(action)
                : "table '" + change.component() + "' on " + describeDoc(action);
        if (action.doc() == null || !lookup.canEdit(action.doc())) {
            final String problem = "You don't have permission to edit " + describeDoc(action) + ", so the change "
                                   + "can't be undone here. Close the dashboard without saving to go back.";
            auditLog.processed(chatId, action.doc(), UNDO_DASHBOARD_COMMAND, target, problem, false);
            return new AiUserActionResult(false, problem, null);
        }
        final String outcome = "Undid the applied change, not saved";
        if (update(message, result, action.withStatus(Status.PENDING, outcome)) == null) {
            return new AiUserActionResult(false, "This has already changed. Try again.", null);
        }
        auditLog.processed(chatId, action.doc(), UNDO_DASHBOARD_COMMAND, target,
                "Undid an applied change: " + String.join("; ", NullSafe.list(NullSafe.get(change,
                        DashboardChange::changes))), true);
        aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, "(With a button in the chat) I undid the "
                                                                     + "proposed change I had applied to "
                                                                     + target + ". Nothing was saved.");
        return new AiUserActionResult(true, outcome, null);
    }

    private static String describeDoc(final UserActionRecord action) {
        final DocRef doc = action.doc();
        return doc == null
                ? "the document"
                : doc.getType() + " '" + NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid()) + "'";
    }

    /// @return True for a change to code (a stepping element's, or an open Query's) that the user applies to an
    /// editor, unsaved, and can undo.
    private static boolean isCodeChange(final UserActionRecord action) {
        return UserActions.APPLY_CODE_CHANGE.equals(action.action())
               || UserActions.APPLY_QUERY_CHANGE.equals(action.action());
    }

    private static String describeTarget(final UserActionRecord action) {
        final CodeChange change = action.codeChange();
        if (change == null || change.pipeline() == null) {
            // A Query's, in its own tab.
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
