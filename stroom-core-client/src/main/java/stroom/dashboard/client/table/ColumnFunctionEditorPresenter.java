/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.table.ColumnFunctionEditorPresenter.ColumnFunctionEditorView;
import stroom.dashboard.shared.DashboardResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.query.api.Column;
import stroom.query.client.presenter.QueryHelpPresenter;
import stroom.query.shared.CompletionsRequest.TextType;
import stroom.query.shared.QueryHelpType;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * This is the presenter for editing the function expression in a dashboard column
 */
public class ColumnFunctionEditorPresenter
        extends MyPresenterWidget<ColumnFunctionEditorView>
        implements ShowPopupEvent.Handler,
        HidePopupRequestEvent.Handler,
        HidePopupEvent.Handler {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final RestFactory restFactory;
    private final EditorPresenter editorPresenter;
    private final QueryHelpPresenter queryHelpPresenter;
    private TablePresenter tablePresenter;
    private Column column;
    private BiConsumer<Column, Column> columnChangeConsumer;

    @Inject
    public ColumnFunctionEditorPresenter(final EventBus eventBus,
                                         final ColumnFunctionEditorView view,
                                         final RestFactory restFactory,
                                         final EditorPresenter editorPresenter,
                                         final QueryHelpPresenter queryHelpPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.editorPresenter = editorPresenter;
        this.queryHelpPresenter = queryHelpPresenter;
        view.setEditor(editorPresenter.getView());
        view.setQueryHelp(queryHelpPresenter.getView());
    }

    private void setupEditor() {
        editorPresenter.setMode(AceEditorMode.STROOM_EXPRESSION);
        editorPresenter.setReadOnly(false);

        // Need to explicitly set some of these as the defaults don't
        // seem to work, maybe due to timing
        editorPresenter.getLineNumbersOption().setOff();
        editorPresenter.getLineWrapOption().setOn();
        editorPresenter.getHighlightActiveLineOption().setOff();
        editorPresenter.getBasicAutoCompletionOption().setOn();
        editorPresenter.getSnippetsOption().setOn();
    }

    public void show(final TablePresenter tablePresenter,
                     final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        this.tablePresenter = tablePresenter;
        this.column = column;
        this.columnChangeConsumer = columnChangeConsumer;

        if (column.getExpression() != null) {
            editorPresenter.setText(column.getExpression());
        } else {
            editorPresenter.setText("");
        }
        final SearchModel searchModel = tablePresenter.getCurrentSearchModel();
        if (searchModel != null) {
            final IndexLoader indexLoader = searchModel.getIndexLoader();
            if (indexLoader != null) {
                queryHelpPresenter.setDataSourceRef(indexLoader.getLoadedDataSourceRef());
            }
        }

        final PopupSize popupSize = PopupSize.resizable(800, 700);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Set Expression For '" + column.getName() + "'")
                .onShow(this)
                .onHideRequest(this)
                .onHide(this)
                .fire();
    }

    @Override
    public void onShow(final ShowPopupEvent e) {
        queryHelpPresenter.setTaskMonitorFactory(this);
        queryHelpPresenter.setIncludedTypes(EnumSet.of(
                QueryHelpType.FIELD,
                QueryHelpType.FUNCTION));
        queryHelpPresenter.setTextType(TextType.EXPRESSION);
        queryHelpPresenter.refresh();
        editorPresenter.focus();

        // If this is done without the scheduler then we get weird behaviour when you click
        // in the text area if line wrap is set to on.  If it is initially set to off and the user
        // manually sets it to on all is fine. Confused.
        Scheduler.get().scheduleDeferred(() -> {
            setupEditor();
            // Needs to be called after the editor is setup
            queryHelpPresenter.linkToEditor(this.editorPresenter);
        });
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final String expression = editorPresenter.getText();
            if (Objects.equals(expression, column.getExpression())) {
                e.hide();
            } else {
                if (expression == null) {
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
                                    AlertEvent.fireError(tablePresenter, result.getString(), e::reset);
                                }
                            })
                            .onFailure(RestErrorHandler.forPopup(this, e))
                            .taskMonitorFactory(this)
                            .exec();
                }
            }
        } else {
            // Cancel/Close
            if (editorPresenter.isClean()) {
                // User not change anything so allow the close
                e.hide();
            } else {
                final String msg = "Expression has unsaved changes.\n"
                                   + "Are you sure you want to close this window?";
                ConfirmEvent.fire(ColumnFunctionEditorPresenter.this, msg, confirm -> {
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

    @Override
    public void onHide(final HidePopupEvent e) {
        editorPresenter.deRegisterCompletionProviders();
    }

    public interface ColumnFunctionEditorView extends View {

        void setEditor(final EditorView editor);

        void setQueryHelp(View view);
    }
}
