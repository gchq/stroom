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

package stroom.quickfilter.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QuickFilterFields;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.FormatQuickFilterRequest;
import stroom.query.shared.ParseQuickFilterRequest;
import stroom.query.shared.ParseQuickFilterResult;
import stroom.quickfilter.client.presenter.AdvancedQuickFilterPresenter.AdvancedQuickFilterView;
import stroom.quickfilter.shared.QuickFilterContext;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TokenError;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The quick filter's "Advanced Query": the same filter as an expression tree, for building
 * something the box's one-line syntax makes awkward, written back as text on OK.
 * <p>
 * The text stays king - see docs/query-filter-surface-syntax-spec.md §9-§11. The tree is a view
 * of it: parsed from it on open by the server, printed back by the server on OK, and never
 * touching it otherwise. The rules this enforces:
 * <ul>
 *     <li>Open, look, Cancel: the text is byte-identical afterwards, comments and spacing intact.</li>
 *     <li>Open, OK without changing anything: also untouched - the canonical spelling of the
 *     original is compared with the canonical spelling of what was written, not the raw text.</li>
 *     <li>Text the server cannot parse: the dialog still opens, empty, and says why; OK from
 *     there does replace the text, because the user has built something new.</li>
 *     <li>The tree editor only offers conditions the syntax can write back, so OK cannot
 *     dead-end on "cannot be expressed".</li>
 * </ul>
 */
public class AdvancedQuickFilterPresenter extends MyPresenterWidget<AdvancedQuickFilterView> {

    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);
    private static final ExpressionOperator EMPTY = ExpressionOperator.builder().build();

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;

    @Inject
    public AdvancedQuickFilterPresenter(final EventBus eventBus,
                                        final AdvancedQuickFilterView view,
                                        final EditExpressionPresenter editExpressionPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    /**
     * @param context            whose fields the text is parsed against and the tree printed with
     * @param currentText        what is in the box now
     * @param onOk               given the new text when the user confirms a change; not called
     *                           on Cancel or on an OK that changed nothing
     * @param taskMonitorFactory what to show busy during the two server calls
     */
    public void show(final QuickFilterContext context,
                     final String currentText,
                     final Consumer<String> onOk,
                     final TaskMonitorFactory taskMonitorFactory) {
        if (!context.hasFields()) {
            AlertEvent.fireError(this, "Advanced Query is not available for this filter.", null);
            return;
        }
        final String text = NullSafe.string(currentText);
        restFactory
                .create(EXPRESSION_RESOURCE)
                .method(res -> res.parseQuickFilter(new ParseQuickFilterRequest(
                        text, context.getDefaultFields(), context.getQualifiedFields())))
                .onSuccess(parsed -> open(context, text, parsed, onOk, taskMonitorFactory))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void open(final QuickFilterContext context,
                      final String currentText,
                      final ParseQuickFilterResult parsed,
                      final Consumer<String> onOk,
                      final TaskMonitorFactory taskMonitorFactory) {
        final SimpleFieldSelectionListModel fields = new SimpleFieldSelectionListModel();
        fields.addItems(context.getQualifiedFields());
        // No data source: these surfaces are not datasources, and TermEditor only uses it for
        // the doc-ref and dictionary pickers, which the condition filter removes anyway.
        editExpressionPresenter.init(restFactory, null, fields);
        editExpressionPresenter.setConditionFilter(QuickFilterFields::isQuickFilterCondition);

        final ExpressionOperator original = parsed.isOk()
                ? Objects.requireNonNullElse(parsed.getExpression(), EMPTY)
                : EMPTY;
        editExpressionPresenter.read(original);

        if (parsed.isOk()) {
            getView().setMessage(null);
        } else {
            // Spec §10.3: say so explicitly rather than silently degrade. Not a dead end - the
            // user can build afresh, and Cancel still leaves the text exactly as it was.
            getView().setMessage("The current filter could not be converted: "
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
                    .onSuccess(formatted -> showPopup(context, currentText, formatted.getText(), onOk,
                            taskMonitorFactory))
                    // An unchanged tree will then be re-formatted and written, which is only
                    // cosmetic; not worth refusing to open over.
                    .onFailure(error -> showPopup(context, currentText, null, onOk, taskMonitorFactory))
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        } else {
            showPopup(context, currentText, parsed.isOk()
                    ? ""
                    : null, onOk, taskMonitorFactory);
        }
    }

    /**
     * @param canonicalOriginal the canonical spelling of the tree the dialog opened with, or
     *                          null when there is none to compare against (parse failed, or the
     *                          format call did), in which case OK always writes
     */
    private void showPopup(final QuickFilterContext context,
                           final String currentText,
                           final String canonicalOriginal,
                           final Consumer<String> onOk,
                           final TaskMonitorFactory taskMonitorFactory) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption("Advanced Query")
                .modal(true)
                .onShow(e -> editExpressionPresenter.focus())
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                        return;
                    }
                    final ExpressionOperator written = editExpressionPresenter.write();
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
                                    confirmReplace(currentText, formatted.getText(), () -> {
                                        onOk.accept(formatted.getText());
                                        e.hide();
                                    }, e::reset);
                                }
                            })
                            .onFailure(error -> {
                                AlertEvent.fireError(this, error.getMessage(), e::reset);
                            })
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                })
                .fire();
    }

    /**
     * Spec §10.2: warn before reformatting, and say what will be lost. Only when there is
     * something to lose - existing text that the new text does not equal.
     */
    private void confirmReplace(final String currentText,
                                final String newText,
                                final Runnable onConfirm,
                                final Runnable onDecline) {
        if (NullSafe.isBlankString(currentText)) {
            onConfirm.run();
            return;
        }
        // The detail pane is the part rendered pre-formatted, so the two texts go there, where
        // their spacing and quoting survive.
        ConfirmEvent.fire(this,
                SafeHtmlUtils.fromString("Replace the filter text?"),
                SafeHtmlUtils.fromString("Current:  " + currentText + "\nNew:      " + newText),
                ok -> {
                    if (ok) {
                        onConfirm.run();
                    } else {
                        onDecline.run();
                    }
                });
    }


    // --------------------------------------------------------------------------------


    public interface AdvancedQuickFilterView extends View {

        void setExpressionView(View view);

        /**
         * A message shown above the tree, or hidden when passed null.
         */
        void setMessage(String message);
    }
}
