/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.event.WindowCloseEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRanges;
import stroom.view.client.presenter.IndexLoader;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.function.Function;
import javax.inject.Provider;

public class QueryEditPresenter
        extends MyPresenterWidget<QueryEditView>
        implements QueryEditUiHandlers, QueryUiHandlers, HasDirtyHandlers {

    private List<String> currentWarnings;
    EditorPresenter codePresenter;
    private final QueryResultTablePresenter tablePresenter;
    private boolean dirty;
    private boolean reading;
    private boolean readOnly = true;
    final QueryModel queryModel;
    TimeRange currentTimeRange = TimeRanges.ALL_TIME;

    @Inject
    public QueryEditPresenter(final EventBus eventBus,
                              final QueryEditView view,
                              final Provider<EditorPresenter> editorPresenterProvider,
                              final QueryResultTablePresenter tablePresenter,
                              final RestFactory restFactory,
                              final IndexLoader indexLoader,
                              final DateTimeSettingsFactory dateTimeSettingsFactory,
                              final ResultStoreModel resultStoreModel) {
        super(eventBus, view);
        this.tablePresenter = tablePresenter;

        queryModel = new QueryModel(
                restFactory,
                indexLoader,
                dateTimeSettingsFactory,
                resultStoreModel,
                tablePresenter);
        queryModel.addErrorListener(this::setErrors);
        getView().setWarningsVisible(false);
        //        queryModel.addComponent("table", tablePresenter);

        codePresenter = editorPresenterProvider.get();
        codePresenter.setMode(AceEditorMode.STROOM_QUERY);

        view.setQueryEditor(codePresenter.getView());
        view.setTable(tablePresenter.getView());
        view.getQueryButtons().setUiHandlers(this);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(tablePresenter.addExpanderHandler(event -> queryModel.refresh()));
        registerHandler(tablePresenter.addRangeChangeHandler(event -> queryModel.refresh()));

        registerHandler(getEventBus().addHandler(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        }));
    }

    private void setDirty(final boolean dirty) {
        if (!reading && this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
        }
    }

    public boolean isDirty() {
        return !readOnly && dirty;
    }

    public void onClose() {
        queryModel.reset(DestroyReason.TAB_CLOSE);
    }

    public void setErrors(final List<String> errors) {
        currentWarnings = errors;
        getView().setWarningsVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    @Override
    public void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            final String msg = currentWarnings.size() == 1
                    ? ("The following warning was created while running this search:")
                    : ("The following " + currentWarnings.size()
                            + " warnings have been created while running this search:");
            final String errors = String.join("\n", currentWarnings);
            AlertEvent.fireWarn(this, msg, errors, null);
        }
    }

    @Override
    public void onTimeRange(final TimeRange timeRange) {
        if (!currentTimeRange.equals(timeRange)) {
            currentTimeRange = timeRange;
//        setTimeRange(timeRange);
            start();
        }
    }

//    private void setTimeRange(final TimeRange timeRange) {
////        getEntity().dashboardContext.setTimeRange(timeRange);
//        getView().setTimeRange(timeRange);
//    }

    @Override
    public void start() {
        run(true, true);
    }

//    @Override
//    public void stop() {
//        searchModel.stop();
//    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        run(incremental, storeHistory, Function.identity());
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
//        final DocRef dataSourceRef = getQuerySettings().getDataSource();
//
//        if (dataSourceRef == null) {
//            warnNoDataSource();
//        } else {
//            currentWarnings = null;
//            expressionPresenter.clearSelection();
//
//            warningsButton.setVisible(false);
//
//            // Write expression.
//            final ExpressionOperator root = expressionPresenter.write();
//            final ExpressionOperator decorated = expressionDecorator.apply(root);

        // Start search.
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
        queryModel.startNewSearch(
                codePresenter.getText(),
                null, //getDashboardContext().getCombinedParams(),
                currentTimeRange,
                incremental,
                storeHistory,
                null);
//        }
    }

    public void setQuery(final DocRef docRef, final String query, final boolean readOnly) {
        this.readOnly = readOnly;

        queryModel.init(docRef.getUuid(), "query");
        if (query != null) {
            reading = true;
            codePresenter.setText(query);
            reading = false;
        }
        getView().getQueryButtons().setEnabled(true);
        getView().getQueryButtons().setMode(false);
        getView().setTimeRange(currentTimeRange);

        codePresenter.setReadOnly(readOnly);
        codePresenter.getFormatAction().setAvailable(!readOnly);

        dirty = false;
    }

    public String getQuery() {
        return codePresenter.getText();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface QueryEditView extends View, HasUiHandlers<QueryEditUiHandlers> {

        void setWarningsVisible(boolean show);

        QueryButtons getQueryButtons();

        TimeRange getTimeRange();

        void setTimeRange(TimeRange timeRange);

        void setQueryEditor(View view);

        void setTable(View view);
    }
}
