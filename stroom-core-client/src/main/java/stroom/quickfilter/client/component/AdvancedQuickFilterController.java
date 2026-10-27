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

package stroom.quickfilter.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QuickFilterFields;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.FormatQuickFilterRequest;
import stroom.query.shared.ParseQuickFilterRequest;
import stroom.query.shared.ParseQuickFilterResult;
import stroom.quickfilter.shared.QuickFilterContext;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The quick filter's "Advanced Query" ([AdvancedQuickFilter]), made for one use: the same filter
/// as an expression tree, for building something the box's one-line syntax makes awkward, written
/// back as text on OK.
///
/// The text stays king - see docs/query-filter-surface-syntax-spec.md §9-§11. The tree is a view
/// of it: parsed from it on open by the server, printed back by the server on OK, and never
/// touching it otherwise. The rules this enforces:
///
/// - Open, look, Cancel: the text is byte-identical afterwards, comments and spacing intact.
/// - Open, OK without changing anything: also untouched - the canonical spelling of the original
///   is compared with the canonical spelling of what was written, not the raw text.
/// - Open, edit, OK: the text is replaced with the canonical spelling of the tree, without a
///   confirmation. The edit was deliberate and Cancel is the way out.
/// - Text the server cannot parse: the dialog still opens, empty, and says why; OK from there
///   does replace the text, because the user has built something new.
/// - The tree editor only offers conditions the syntax can write back, so OK cannot dead-end on
///   "cannot be expressed".
public class AdvancedQuickFilterController extends Controller {

    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);
    private static final ExpressionOperator EMPTY = ExpressionOperator.builder().build();

    private final AdvancedQuickFilter view;
    private final ExpressionEditorController expressionEditor;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The content.
    /// @param expressionEditor Edits the filter as a tree.
    /// @param restFactory      Parses and prints the filter's text.
    @Inject
    public AdvancedQuickFilterController(final EventBus eventBus,
                                         final AdvancedQuickFilter view,
                                         final ExpressionEditorController expressionEditor,
                                         final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor);
        this.restFactory = restFactory;
        view.setExpressionEditor(expressionEditor);
    }

    /// Parses the filter's text and shows it as a tree. The opener owns the dialog until it is
    /// hidden; if it can't be shown, it says why.
    ///
    /// @param opener             What shows it.
    /// @param context            Whose fields the text is parsed against and the tree printed with.
    /// @param currentText        What is in the box now.
    /// @param onOk               Given the new text when the user confirms a change; not called on
    ///                           Cancel or on an OK that changed nothing.
    /// @param taskMonitorFactory What to show busy during the server calls.
    public void show(final HasControllerNode opener,
                     final QuickFilterContext context,
                     final String currentText,
                     final Consumer<String> onOk,
                     final TaskMonitorFactory taskMonitorFactory) {
        if (!context.hasFields()) {
            AlertEvent.fireError(this, "Advanced Query is not available for this filter.", null);
            dispose();
            return;
        }
        final String text = NullSafe.string(currentText);
        restFactory
                .create(EXPRESSION_RESOURCE)
                .method(res -> res.parseQuickFilter(new ParseQuickFilterRequest(
                        text, context.getDefaultFields(), context.getQualifiedFields())))
                .onSuccess(parsed -> open(opener, context, parsed, onOk, taskMonitorFactory))
                .onFailure(error -> notShown(error.getMessage()))
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void open(final HasControllerNode opener,
                      final QuickFilterContext context,
                      final ParseQuickFilterResult parsed,
                      final Consumer<String> onOk,
                      final TaskMonitorFactory taskMonitorFactory) {
        final SimpleFieldSelectionListModel fields = new SimpleFieldSelectionListModel();
        fields.addItems(context.getQualifiedFields());
        // No data source: these surfaces are not datasources, and TermEditor only uses it for
        // the doc-ref and dictionary pickers, which the condition filter removes anyway.
        expressionEditor.init(null, fields);
        expressionEditor.setConditionFilter(QuickFilterFields::isQuickFilterCondition);

        final ExpressionOperator original = parsed.isOk()
                ? Objects.requireNonNullElse(parsed.getExpression(), EMPTY)
                : EMPTY;
        expressionEditor.read(original);

        if (parsed.isOk()) {
            view.setMessage(null);
        } else {
            // Spec §10.3: say so explicitly rather than silently degrade. Not a dead end - the
            // user can build afresh, and Cancel still leaves the text exactly as it was.
            view.setMessage("The current filter could not be converted: "
                            + NullSafe.get(parsed.getError(), TokenError::getText)
                            + " Starting from an empty query. OK will replace the filter text.");
        }

        // The canonical spelling of what was opened, so that OK-without-edit is a no-op even
        // though the editor rewrites the tree's incidental details (enabled flags, ordering).
        // Fetched before the dialog shows, so a quick OK cannot race it.
        if (parsed.isOk() && original.hasChildren()) {
            restFactory
                    .create(EXPRESSION_RESOURCE)
                    .method(res -> res.formatQuickFilter(new FormatQuickFilterRequest(
                            original, context.getDefaultFields(), context.getQualifiedFields())))
                    .onSuccess(formatted ->
                            showDialog(opener, context, formatted.getText(), onOk, taskMonitorFactory))
                    // An unchanged tree will then be re-formatted and written, which is only
                    // cosmetic; not worth refusing to open over.
                    .onFailure(error -> showDialog(opener, context, null, onOk, taskMonitorFactory))
                    .owner(this)
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        } else {
            showDialog(opener, context, parsed.isOk()
                    ? ""
                    : null, onOk, taskMonitorFactory);
        }
    }

    // canonicalOriginal is the canonical spelling of the tree the dialog opened with, or null
    // when there is none to compare against (parse failed, or the format call did), in which case
    // OK always writes
    private void showDialog(final HasControllerNode opener,
                            final QuickFilterContext context,
                            final String canonicalOriginal,
                            final Consumer<String> onOk,
                            final TaskMonitorFactory taskMonitorFactory) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption("Advanced Query")
                .onShow(e -> expressionEditor.focus())
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                        return;
                    }
                    final ExpressionOperator written = expressionEditor.write();
                    restFactory
                            .create(EXPRESSION_RESOURCE)
                            .method(res -> res.formatQuickFilter(new FormatQuickFilterRequest(
                                    written, context.getDefaultFields(), context.getQualifiedFields())))
                            .onSuccess(formatted -> {
                                if (!formatted.isOk()) {
                                    // Should not happen given the condition filter, but the
                                    // server is the authority; keep the dialog open to fix it.
                                    AlertEvent.fireError(this, formatted.getError(), e::reset);
                                } else if (canonicalOriginal != null
                                           && Objects.equals(formatted.getText(), canonicalOriginal)) {
                                    // Spec §10.1: nothing was edited, so the text is not touched.
                                    e.hide();
                                } else {
                                    // Spec §10.2: the user edited and pressed OK, so no confirmation.
                                    onOk.accept(formatted.getText());
                                    e.hide();
                                }
                            })
                            .onFailure(error -> AlertEvent.fireError(this, error.getMessage(), e::reset))
                            .owner(this)
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                })
                .fire();
    }

    // The text couldn't be parsed at all (e.g. the server is unreachable), so there is no dialog
    private void notShown(final String message) {
        AlertEvent.fireError(this, message, null);
        dispose();
    }
}
