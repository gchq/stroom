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

package stroom.dashboard.client.query;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.ExpressionTreePresenter;
import stroom.util.shared.PageRequest;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellList;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class QueryHistoryPresenter
        extends MyPresenterWidget<QueryHistoryPresenter.QueryHistoryView>
        implements HidePopupRequestEvent.Handler {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final RestFactory restFactory;
    private final ExpressionTreePresenter expressionPresenter;
    private final MySingleSelectionModel<StoredQuery> selectionModel;
    private QueryPresenter queryPresenter;
    private String currentDashboardUuid;

    @Inject
    public QueryHistoryPresenter(final EventBus eventBus,
                                 final QueryHistoryView view,
                                 final ExpressionTreePresenter expressionPresenter,
                                 final RestFactory restFactory) {
        super(eventBus, view);

        this.restFactory = restFactory;
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
            final StoredQuery query = selectionModel.getSelectedObject();

            if (query == null || query.getQuery() == null) {
                expressionPresenter.read(null);
            } else {
                expressionPresenter.read(query.getQuery().getExpression());
            }
        }));
        registerHandler(selectionModel.addDoubleSelectHandler(event ->
                HidePopupRequestEvent.builder(this).fire()));
    }

    public void show(final QueryPresenter queryPresenter, final String dashboardUuid) {
        this.queryPresenter = queryPresenter;
        this.currentDashboardUuid = dashboardUuid;

        refresh(true);
    }

    private void refresh(final boolean showAfterRefresh) {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(currentDashboardUuid);
        criteria.setComponentId(queryPresenter.getId());
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, true, false);
        criteria.setFavourite(false);
        criteria.setPageRequest(PageRequest.createDefault());

        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(result -> {
                    selectionModel.clear();

                    ExpressionOperator lastExpression = null;
                    final List<StoredQuery> dedupedList = new ArrayList<>(result.size());
                    for (final StoredQuery queryEntity : result.getValues()) {
                        if (queryEntity != null
                                && queryEntity.getQuery() != null
                                && queryEntity.getQuery().getExpression() != null) {
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
                        final PopupSize popupSize = PopupSize.resizable(500, 400);

                        ShowPopupEvent.builder(this)
                                .popupType(PopupType.OK_CANCEL_DIALOG)
                                .popupSize(popupSize)
                                .caption("Query History")
                                .onShow(e -> {
                                    if (getView().getCellList().getVisibleItems().size() > 0) {
                                        final StoredQuery firstItem = getView().getCellList().getVisibleItem(0);
                                        selectionModel.setSelected(firstItem, true);
                                        getView().getCellList().setKeyboardSelectedRow(0, true);
                                    }
                                })
                                .onHideRequest(this)
                                .fire();
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final StoredQuery query = selectionModel.getSelectedObject();
            if (query != null && query.getQuery() != null && query.getQuery().getExpression() != null) {
                queryPresenter.setExpression(query.getQuery().getExpression());
            }
        }
        e.hide();
    }

    public interface QueryHistoryView extends View {

        CellList<StoredQuery> getCellList();

        void setExpressionView(View view);
    }
}
