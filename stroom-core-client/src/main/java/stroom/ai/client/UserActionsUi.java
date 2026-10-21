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
import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    /// A dashboard the agent proposed, built from StroomQL, which the button opens Stroom's New Dashboard dialog to
    /// create.
    static final String CREATE_DASHBOARD = "create_dashboard";
    /// A change to a stepping element's code the agent proposed, which the button opens a review of, from which
    /// the user can apply it to the element's editor (unsaved).
    static final String APPLY_CODE_CHANGE = "apply_code_change";
    /// A revised query the agent proposed for the user's open Query, which the button puts in its editor, unsaved.
    static final String APPLY_QUERY_CHANGE = "apply_query_change";
    /// A change to a dashboard table the agent proposed, which the button applies to the open dashboard (unsaved).
    static final String APPLY_DASHBOARD_CHANGE = "apply_dashboard_change";
    /// A change to a document's Documentation tab the agent proposed, which the button opens a review of, from which
    /// the user can apply it to the tab's editor (unsaved).
    static final String APPLY_DOCUMENTATION_CHANGE = "apply_documentation_change";
    /// What an applied dashboard change's card says, as the server writes it.
    static final String APPLIED_DASHBOARD = "Applied, not saved. Save the dashboard to keep it, or use Undo apply.";
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
    /// cluster admin), or the query of a proposed query or dashboard.
    static boolean showsCode(final String action) {
        return ELASTICSEARCH_REQUEST.equals(action) || CREATE_QUERY.equals(action) || CREATE_DASHBOARD.equals(action);
    }

    /// @param action An action that shows code (see [#showsCode(String)]).
    /// @return The language its code is in, as its code block's header names it.
    static String codeLanguage(final String action) {
        return ELASTICSEARCH_REQUEST.equals(action)
                ? "elasticsearch"
                : "stroomql";
    }

    /// @param action An action.
    /// @param status Its status, as stored.
    /// @return True if it is a code or documentation change that has been applied, so its card offers Undo apply.
    static boolean isAppliedCodeChange(final String action, final String status) {
        return (APPLY_CODE_CHANGE.equals(action) || APPLY_QUERY_CHANGE.equals(action)
                || APPLY_DOCUMENTATION_CHANGE.equals(action)) && "DONE".equals(status);
    }

    /// @param action An action.
    /// @param status Its status, as stored.
    /// @return True if it is a dashboard change that has been applied, so its card offers Undo apply.
    static boolean isAppliedDashboardChange(final String action, final String status) {
        return APPLY_DASHBOARD_CHANGE.equals(action) && "DONE".equals(status);
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
        } else if (CREATE_DASHBOARD.equals(action)) {
            return "Create dashboard…";
        } else if (APPLY_CODE_CHANGE.equals(action) || APPLY_DOCUMENTATION_CHANGE.equals(action)) {
            return "Review and apply…";
        } else if (APPLY_QUERY_CHANGE.equals(action)) {
            return "Apply to query";
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
    /// @param query For set_expression, the query's expression as the server wrote it ([#expression(Object)]), or
    ///              null.
    /// @return The operation, for the dashboard to apply.
    static TableChange tableChange(final Function<String, String> field, final Object query) {
        return tableChange(field, query, null);
    }

    /// @param field Gives each of a proposed operation's fields as text (numbers as digits), or null if it has none.
    /// @param query For set_expression, the query's expression as the server wrote it ([#expression(Object)]), or
    ///              null.
    /// @param doc   For set_setting of a document, the document as the server wrote it (`type`, `uuid` and
    ///              `name`), or null.
    /// @return The operation, for the dashboard to apply.
    static TableChange tableChange(final Function<String, String> field, final Object query, final Object doc) {
        return TableChange.builder(field.apply("op"))
                .column(field.apply("column"))
                .level(number(field.apply("level")))
                .name(field.apply("name"))
                .expression(field.apply("expression"))
                .format(field.apply("format"))
                .position(number(field.apply("position")))
                .direction(field.apply("direction"))
                .priority(number(field.apply("priority")))
                .filter(field.apply("filter"))
                .query(query == null
                        ? null
                        : expression(query))
                .table(field.apply("table"))
                .copy("true".equals(field.apply("copy")))
                .target(field.apply("target"))
                .targetId(field.apply("target_id"))
                .setting(field.apply("setting"))
                .value(field.apply("value"))
                .ref(field.apply("ref"))
                .doc(doc instanceof final Map<?, ?> map
                     && map.get("type") instanceof final String type
                     && map.get("uuid") instanceof final String uuid
                        ? new DocRef(type, uuid, map.get("name") instanceof final String name
                        ? name
                        : null)
                        : null)
                .style(field.apply("style"))
                .formatType(field.apply("format_type"))
                .bold("true".equals(field.apply("bold")))
                .italic("true".equals(field.apply("italic")))
                .hide("true".equals(field.apply("hide")))
                .build();
    }

    /// Reads a query's expression as the server writes it for a proposed dashboard change: an operator as `op`,
    /// `enabled` and `children`, and a term as `field`, `condition` (the condition's name), `value`, `enabled` and
    /// `doc`, given here as maps and lists.
    ///
    /// @param value The expression.
    /// @return The expression, or null if it can't be read.
    static ExpressionOperator expression(final Object value) {
        return expressionItem(value) instanceof final ExpressionOperator operator
                ? operator
                : null;
    }

    private static ExpressionItem expressionItem(final Object value) {
        if (!(value instanceof final Map<?, ?> map)) {
            return null;
        }
        final Boolean enabled = Boolean.FALSE.equals(map.get("enabled"))
                ? Boolean.FALSE
                : null;
        if (map.get("op") instanceof final String op) {
            final List<ExpressionItem> children = new ArrayList<>();
            if (map.get("children") instanceof final List<?> list) {
                for (final Object child : list) {
                    final ExpressionItem item = expressionItem(child);
                    if (item == null) {
                        return null;
                    }
                    children.add(item);
                }
            }
            final Op parsed = enumValue(Op.values(), op);
            return parsed == null
                    ? null
                    : ExpressionOperator.builder().op(parsed).enabled(enabled).children(children).build();
        }
        if (map.get("field") instanceof final String field && map.get("condition") instanceof final String name) {
            final Condition condition = enumValue(Condition.values(), name);
            if (condition == null) {
                return null;
            }
            final ExpressionTerm.Builder term = ExpressionTerm.builder()
                    .field(field)
                    .condition(condition)
                    .enabled(enabled);
            if (map.get("value") instanceof final String text) {
                term.value(text);
            }
            if (map.get("doc") instanceof final Map<?, ?> doc
                && doc.get("type") instanceof final String type && doc.get("uuid") instanceof final String uuid) {
                term.docRef(new DocRef(type, uuid, doc.get("name") instanceof final String docName
                        ? docName
                        : null));
            }
            return term.build();
        }
        return null;
    }

    private static <E extends Enum<E>> E enumValue(final E[] values, final String name) {
        for (final E value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
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
