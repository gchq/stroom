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

package stroom.ai.client;

import stroom.dashboard.client.event.ApplyDashboardChangeEvent;
import stroom.dashboard.client.table.TableChange;

import java.util.function.Function;

/// The actions an MCP tool's result can offer the user as buttons, as the server sends them (see the server's
/// `UserActions`), and what their buttons say.
final class UserActionsUi {

    static final String ENABLE_PROCESSOR_FILTER = "enable_processor_filter";
    static final String DISABLE_PROCESSOR_FILTER = "disable_processor_filter";
    static final String OPEN_DOCUMENT = "open_document";
    static final String ELASTICSEARCH_REQUEST = "elasticsearch_request";
    /// A StroomQL query the agent proposed, which the button opens Stroom's New Query dialog to create.
    static final String CREATE_QUERY = "create_query";
    /// A change to a stepping element's code the agent proposed, which the button opens a review of, from which
    /// the user can apply it to the element's editor (unsaved).
    static final String APPLY_CODE_CHANGE = "apply_code_change";
    /// A change to a dashboard table the agent proposed, which the button applies to the open dashboard (unsaved).
    static final String APPLY_DASHBOARD_CHANGE = "apply_dashboard_change";
    /// What an applied dashboard change's card says, as the server writes it.
    static final String APPLIED_DASHBOARD = "Applied, not saved. Save the dashboard to keep it, or close it without "
                                            + "saving to go back.";
    /// What an applied code change's card says, as the server writes it.
    static final String APPLIED = "Applied, not saved";
    /// The text of the button that undoes an applied code change.
    static final String UNDO_APPLY = "Undo apply";
    /// What an applied code change's card says once its editor has been edited, so Undo apply would lose edits.
    static final String UNDO_WITHDRAWN = "The code has been edited since the change was applied, so Undo apply "
                                         + "would lose those edits. Use Ctrl+Z in the editor to undo it.";

    private UserActionsUi() {
        // Static methods only.
    }

    /// @param status    The action's status, as stored.
    /// @param expiresMs When its button stops working.
    /// @param nowMs     The time now.
    /// @return True if its button can still be used.
    static boolean isPending(final String status, final long expiresMs, final long nowMs) {
        return "PENDING".equals(status) && expiresMs > nowMs;
    }

    /// @param status  The action's status, as stored.
    /// @param outcome What happened when it was used, or null.
    /// @return What to show in place of a button that can't be used.
    static String outcomeText(final String status, final String outcome) {
        if (outcome != null && !outcome.isBlank()) {
            return outcome;
        }
        return "PENDING".equals(status)
                ? "This button has expired."
                : String.valueOf(status);
    }

    /// @param action An action.
    /// @return True if the action shows a request or query as code: an Elasticsearch request (only shown, for the
    /// cluster admin) or a proposed query.
    static boolean showsCode(final String action) {
        return ELASTICSEARCH_REQUEST.equals(action) || CREATE_QUERY.equals(action);
    }

    /// @param action An action.
    /// @param status Its status, as stored.
    /// @return True if it is a code change that has been applied, so its card offers Undo apply.
    static boolean isAppliedCodeChange(final String action, final String status) {
        return APPLY_CODE_CHANGE.equals(action) && "DONE".equals(status);
    }

    /// @param action An action.
    /// @return What its button says.
    static String buttonText(final String action) {
        if (ENABLE_PROCESSOR_FILTER.equals(action)) {
            return "Enable";
        } else if (DISABLE_PROCESSOR_FILTER.equals(action)) {
            return "Disable";
        } else if (OPEN_DOCUMENT.equals(action)) {
            return "Open";
        } else if (CREATE_QUERY.equals(action)) {
            return "Create query…";
        } else if (APPLY_CODE_CHANGE.equals(action)) {
            return "Review and apply…";
        } else if (APPLY_DASHBOARD_CHANGE.equals(action)) {
            return "Apply to dashboard";
        }
        return "Do it";
    }

    /// @param editableWhenProposed True if the user could edit the dashboard when the change was proposed.
    /// @param open                 True if the dashboard is open now.
    /// @param editableNow          True if it is open and the user can edit it.
    /// @return Why the Apply to dashboard button is disabled, as its tooltip, or null if it isn't.
    static String applyDashboardDisabledReason(final boolean editableWhenProposed,
                                               final boolean open,
                                               final boolean editableNow) {
        if (!editableWhenProposed || (open && !editableNow)) {
            return ApplyDashboardChangeEvent.NOT_EDITABLE;
        }
        return null;
    }

    /// @param field Gives each of a proposed operation's fields as text (numbers as digits), or null if it has none.
    /// @return The operation, for the dashboard to apply.
    static TableChange tableChange(final Function<String, String> field) {
        return new TableChange(field.apply("op"),
                field.apply("column"),
                number(field.apply("level")),
                field.apply("name"),
                field.apply("expression"),
                field.apply("format"),
                number(field.apply("position")),
                field.apply("direction"),
                number(field.apply("priority")),
                field.apply("filter"));
    }

    private static Integer number(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(text);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
