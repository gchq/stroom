/*
 * Copyright 2017 Crown Copyright
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
import stroom.dashboard.client.table.ColumnFunctionEditorPresenter.ColumnFunctionEditorView;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.query.api.v2.Field;
import stroom.query.client.presenter.QueryHelpPresenter;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpDataSupplier;
import stroom.query.shared.QueryHelpItemsRequest;
import stroom.query.shared.QueryHelpItemsRequest.HelpItemType;
import stroom.query.shared.QueryHelpItemsResult;
import stroom.query.shared.QueryResource;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.view.client.presenter.DataSourceFieldsMap;
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
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This is the presenter for editing the function expression in a dashboard column
 */
public class ColumnFunctionEditorPresenter
        extends MyPresenterWidget<ColumnFunctionEditorView>
        implements ShowPopupEvent.Handler,
        HidePopupRequestEvent.Handler,
        HidePopupEvent.Handler {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private static final Set<HelpItemType> SUPPORTED_HELP_TYPES = EnumSet.of(
            HelpItemType.FIELD,
            HelpItemType.FUNCTION);

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;
    private final EditorPresenter editorPresenter;
    private final QueryHelpPresenter queryHelpPresenter;
    private AceCompletionProvider functionsCompletionProvider;
    private TablePresenter tablePresenter;
    private Field field;
    private BiConsumer<Field, Field> fieldChangeConsumer;

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
                     final Field field,
                     final BiConsumer<Field, Field> fieldChangeConsumer) {
        this.tablePresenter = tablePresenter;
        this.field = field;
        this.fieldChangeConsumer = fieldChangeConsumer;

        if (field.getExpression() != null) {
            editorPresenter.setText(field.getExpression());
        } else {
            editorPresenter.setText("");
        }
        queryHelpPresenter.setQueryHelpDataSupplier(createQueryHelpDataSupplier());
        queryHelpPresenter.linkToEditor(this.editorPresenter);

        final PopupSize popupSize = PopupSize.resizable(800, 700);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Set Expression For '" + field.getName() + "'")
                .onShow(this)
                .onHideRequest(this)
                .onHide(this)
                .fire();
    }

    @Override
    public void onShow(final ShowPopupEvent e) {
        editorPresenter.focus();

        // If this is done without the scheduler then we get weird behaviour when you click
        // in the text area if line wrap is set to on.  If it is initially set to off and the user
        // manually sets it to on all is fine. Confused.
        Scheduler.get().scheduleDeferred(this::setupEditor);
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final String expression = editorPresenter.getText();
            if (EqualsUtil.isEquals(expression, field.getExpression())) {
                e.hide();
            } else {
                if (expression == null) {
                    fieldChangeConsumer.accept(field, field.copy().expression(null).build());
                    e.hide();
                } else {
                    // Check the validity of the expression.
                    final Rest<ValidateExpressionResult> rest = restFactory.create();
                    rest
                            .onSuccess(result -> {
                                if (result.isOk()) {
                                    fieldChangeConsumer.accept(field, field
                                            .copy()
                                            .expression(expression)
                                            .build());
                                    e.hide();
                                } else {
                                    AlertEvent.fireError(tablePresenter, result.getString(), null);
                                }
                            })
                            .call(DASHBOARD_RESOURCE)
                            .validateExpression(expression);
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
                    }
                });
            }
        }
    }

    @Override
    public void onHide(final HidePopupEvent e) {
        editorPresenter.deRegisterCompletionProviders();
    }

    private QueryHelpDataSupplier createQueryHelpDataSupplier() {
        return new QueryHelpDataSupplier() {

            @Override
            public String decorateFieldName(final String fieldName) {
                return GwtNullSafe.get(fieldName, str ->
                        "${" + str + "}");
            }

            @Override
            public void registerChangeHandler(final Consumer<DataSourceFieldsMap> onChange) {
                // Do nothing as there is no means to change the data source on this screen
            }

            @Override
            public boolean isSupported(final HelpItemType helpItemType) {
                return helpItemType != null && SUPPORTED_HELP_TYPES.contains(helpItemType);
            }

            @Override
            public void fetchQueryHelpItems(final String filterInput,
                                            final Consumer<QueryHelpItemsResult> resultConsumer) {
                final QueryHelpItemsRequest queryHelpItemsRequest = QueryHelpItemsRequest.fromDataSource(
                        GwtNullSafe.get(tablePresenter.getTableSettings(), TableComponentSettings::getDataSourceRef),
                        filterInput,SUPPORTED_HELP_TYPES);

                final Rest<QueryHelpItemsResult> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            GwtNullSafe.consume(result, resultConsumer);
                        })
                        .onFailure(throwable -> AlertEvent.fireError(
                                ColumnFunctionEditorPresenter.this,
                                throwable.getMessage(),
                                null))
                        .call(QUERY_RESOURCE)
                        .fetchQueryHelpItems(queryHelpItemsRequest);
            }
        };
    }


    // --------------------------------------------------------------------------------


    public interface ColumnFunctionEditorView extends View {

        void setEditor(final EditorView editor);

        void setQueryHelp(View view);
    }
}
