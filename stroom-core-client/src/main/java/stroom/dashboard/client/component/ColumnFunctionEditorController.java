/*
 * Copyright 2023 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.shared.DashboardResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.component.CodeEditorOptions;
import stroom.query.api.Column;
import stroom.query.client.component.QueryHelpController;
import stroom.query.shared.CompletionsRequest.TextType;
import stroom.query.shared.QueryHelpType;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;

/// Edits the function expression of a dashboard table's column ([ColumnFunctionEditor]), in a
/// dialog made for one use, validating it on OK.
public class ColumnFunctionEditorController extends Controller {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final RestFactory restFactory;
    private final CodeEditorController editor;
    private final QueryHelpController queryHelp;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param restFactory        Validates the expression.
    /// @param codeEditorProvider Makes the controller of the expression's editor.
    /// @param queryHelp          Lists the fields and functions, beside the editor.
    @Inject
    public ColumnFunctionEditorController(final EventBus eventBus,
                                          final ColumnFunctionEditor view,
                                          final RestFactory restFactory,
                                          final Provider<CodeEditorController> codeEditorProvider,
                                          final QueryHelpController queryHelp) {
        super(eventBus);
        this.restFactory = restFactory;
        this.queryHelp = own(queryHelp);
        initWidget(view);
        editor = make(codeEditorProvider).attach(view.getEditor());
        view.setQueryHelp(queryHelp);
    }

    /// Shows a column's expression in a dialog, owned by `tablePresenter` until it is closed. OK
    /// validates a changed expression before giving it.
    ///
    /// @param tablePresenter       The table, whose data source's fields the help lists.
    /// @param column               The column.
    /// @param columnChangeConsumer Given the column and the column with its new expression, once
    ///                             OK is pressed with a valid changed expression.
    public void show(final TableController tablePresenter,
                     final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        editor.setText(column.getExpression() != null
                ? column.getExpression()
                : "");
        final SearchModel searchModel = tablePresenter.getCurrentSearchModel();
        if (searchModel != null) {
            final IndexLoader indexLoader = searchModel.getIndexLoader();
            if (indexLoader != null) {
                queryHelp.setDataSourceRef(indexLoader.getLoadedDataSourceRef());
            }
        }

        ControllerDialog.showOnce(tablePresenter, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 700))
                .caption("Set Expression For '" + column.getName() + "'")
                .onShow(e -> onShow())
                .onHideRequest(e -> onHideRequest(e, column, columnChangeConsumer))
                .onHide(e -> editor.deRegisterCompletionProviders())
                .fire();
    }

    private void onShow() {
        queryHelp.setIncludedTypes(EnumSet.of(
                QueryHelpType.FIELD,
                QueryHelpType.FUNCTION));
        queryHelp.setTextType(TextType.EXPRESSION);
        queryHelp.refresh();
        editor.focus();

        // If this is done without the scheduler then we get weird behaviour when you click
        // in the text area if line wrap is set to on.  If it is initially set to off and the user
        // manually sets it to on all is fine. Confused.
        Scheduler.get().scheduleDeferred(() -> {
            if (!isDisposed()) {
                setupEditor();
                // Needs to be called after the editor is setup
                queryHelp.linkToEditor(editor);
            }
        });
    }

    private void setupEditor() {
        editor.setMode(AceEditorMode.STROOM_EXPRESSION);
        editor.setReadOnly(false);

        // Need to explicitly set some of these as the defaults don't
        // seem to work, maybe due to timing
        final CodeEditorOptions options = editor.getOptions();
        options.getLineNumbersOption().setOff();
        options.getLineWrapOption().setOn();
        options.getHighlightActiveLineOption().setOff();
        options.getBasicAutoCompletionOption().setOn();
        options.getSnippetsOption().setOn();
    }

    private void onHideRequest(final HidePopupRequestEvent e,
                               final Column column,
                               final BiConsumer<Column, Column> columnChangeConsumer) {
        if (e.isOk()) {
            final String expression = editor.getText();
            if (Objects.equals(expression, column.getExpression())) {
                e.hide();
            } else if (expression == null) {
                columnChangeConsumer.accept(column, column.copy().expression(null).build());
                e.hide();
            } else {
                // Check the validity of the expression.
                restFactory
                        .create(DASHBOARD_RESOURCE)
                        .method(res -> res.validateExpression(expression))
                        .onSuccess(result -> {
                            if (result.isOk()) {
                                columnChangeConsumer.accept(column, column
                                        .copy()
                                        .expression(expression)
                                        .build());
                                e.hide();
                            } else {
                                AlertEvent.fireError(this, result.getString(), e::reset);
                            }
                        })
                        .onFailure(RestErrorHandler.forPopup(this, e))
                        .owner(this)
                        .exec();
            }
        } else if (editor.isClean()) {
            // Cancel/Close: the user didn't change anything, so allow the close
            e.hide();
        } else {
            final String msg = "Expression has unsaved changes.\n"
                               + "Are you sure you want to close this window?";
            ConfirmEvent.fire(this, msg, confirm -> {
                if (confirm) {
                    e.hide();
                } else {
                    // Don't hide
                    e.reset();
                }
            });
        }
    }
}
