/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.query;

import com.google.gwt.user.cellview.client.CellList;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Sort.Direction;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.ExpressionTreePresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class QueryHistoryPresenter extends MyPresenterWidget<QueryHistoryPresenter.QueryHistoryView> {
    private final ClientDispatchAsync dispatcher;
    private final ExpressionTreePresenter expressionPresenter;
    private final MySingleSelectionModel<QueryEntity> selectionModel;
    private QueryPresenter queryPresenter;
    private long currentDashboardId;

    @Inject
    public QueryHistoryPresenter(final EventBus eventBus, final QueryHistoryView view,
                                 final ExpressionTreePresenter expressionPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.expressionPresenter = expressionPresenter;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setExpressionView(expressionPresenter.getView());

        selectionModel = new MySingleSelectionModel<>();
        view.getCellList().setSelectionModel(selectionModel);
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event -> {
            final QueryEntity query = selectionModel.getSelectedObject();

            if (query == null || query.getQuery() == null) {
                expressionPresenter.read(null);
            } else {
                expressionPresenter.read(query.getQuery().getExpression());
            }
        }));
        registerHandler(selectionModel.addDoubleSelectHandler(event -> close(true)));
    }

    public void show(final QueryPresenter queryPresenter, final long dashboardId) {
        this.queryPresenter = queryPresenter;
        this.currentDashboardId = dashboardId;

        refresh(true);
    }

    private void refresh(final boolean showAfterRefresh) {
        final FindQueryCriteria criteria = new FindQueryCriteria();
        criteria.setDashboardId(currentDashboardId);
        criteria.setQueryId(queryPresenter.getId());
        criteria.setSort(FindQueryCriteria.FIELD_TIME, Direction.DESCENDING, false);
        criteria.setFavourite(false);
        criteria.setPageRequest(new PageRequest(0L, 100));

        final EntityServiceFindAction<FindQueryCriteria, QueryEntity> action = new EntityServiceFindAction<>(criteria);
        dispatcher.exec(action).onSuccess(result -> {
            selectionModel.clear();

            ExpressionOperator lastExpression = null;
            final List<QueryEntity> dedupedList = new ArrayList<>(result.getSize());
            for (final QueryEntity queryEntity : result) {
                if (queryEntity != null && queryEntity.getQuery() != null && queryEntity.getQuery().getExpression() != null) {
                    final ExpressionOperator expression = queryEntity.getQuery().getExpression();
                    if (lastExpression == null || !lastExpression.equals(expression)) {
                        dedupedList.add(queryEntity);
                    }

                    lastExpression = expression;
                }
            }

            getView().getCellList().setRowData(dedupedList);
            getView().getCellList().setRowCount(dedupedList.size(), true);

            if (showAfterRefresh) {
                final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        close(ok);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                    }
                };

                final PopupSize popupSize = new PopupSize(500, 400, true);
                ShowPopupEvent.fire(queryPresenter, QueryHistoryPresenter.this, PopupType.OK_CANCEL_DIALOG,
                        popupSize, "Query History", popupUiHandlers);
            }
        });
    }

    private void close(final boolean ok) {
        if (ok) {
            final QueryEntity query = selectionModel.getSelectedObject();
            if (query != null && query.getQuery() != null && query.getQuery().getExpression() != null) {
                queryPresenter.setExpression(query.getQuery().getExpression());
            }
        }

        HidePopupEvent.fire(queryPresenter, QueryHistoryPresenter.this);
    }

    public interface QueryHistoryView extends View {
        CellList<QueryEntity> getCellList();

        void setExpressionView(View view);
    }
}
