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

import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.client.ExpressionTreePresenter;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.PageRequest;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellList;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class QueryFavouritesPresenter
        extends MyPresenterWidget<QueryFavouritesPresenter.QueryFavouritesView> {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final RestFactory restFactory;
    private final ExpressionTreePresenter expressionPresenter;
    private final MySingleSelectionModel<StoredQuery> selectionModel;
    private final NamePresenter namePresenter;
    private final ButtonView createButton;
    private final ButtonView editButton;
    private final ButtonView deleteButton;
    private QueryPresenter queryPresenter;
    private ExpressionOperator currentExpression;
    private String currentDashboardUuid;
    private DocRef currentDataSource;

    @Inject
    public QueryFavouritesPresenter(final EventBus eventBus,
                                    final QueryFavouritesView view,
                                    final ExpressionTreePresenter expressionPresenter,
                                    final NamePresenter namePresenter,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.expressionPresenter = expressionPresenter;
        this.namePresenter = namePresenter;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setExpressionView(expressionPresenter.getView());

        selectionModel = new MySingleSelectionModel<>();
        view.getCellList().setSelectionModel(selectionModel);

        createButton = view.addButton(SvgPresets.NEW_ITEM);
        createButton.setTitle("Create Favourite From Current Query");
        editButton = view.addButton(SvgPresets.EDIT);
        editButton.setTitle("Change Favourite Name");
        deleteButton = view.addButton(SvgPresets.DELETE);
        deleteButton.setTitle("Delete Favourite");
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event -> {
            final StoredQuery query = selectionModel.getSelectedObject();

            if (query == null || query.getQuery() == null) {
                expressionPresenter.read(null);
                editButton.setEnabled(false);
                deleteButton.setEnabled(false);
            } else {
                expressionPresenter.read(query.getQuery().getExpression());
                editButton.setEnabled(true);
                deleteButton.setEnabled(true);
            }
        }));
        registerHandler(selectionModel.addDoubleSelectHandler(event -> hide()));
        registerHandler(createButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final Query query = Query.builder()
                        .dataSource(currentDataSource)
                        .expression(currentExpression)
                        .build();
                final StoredQuery queryEntity = new StoredQuery();
                queryEntity.setQuery(query);
                queryEntity.setDashboardUuid(currentDashboardUuid);
                queryEntity.setComponentId(queryPresenter.getId());
                queryEntity.setName("");
                queryEntity.setFavourite(true);

                namePresenter.show(queryEntity, result -> refresh(false));
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final StoredQuery query = selectionModel.getSelectedObject();
                if (query != null) {
                    namePresenter.show(query, result -> refresh(false));
                }
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final StoredQuery query = selectionModel.getSelectedObject();
                if (query != null) {
                    ConfirmEvent.fire(QueryFavouritesPresenter.this,
                            "Are you sure you want to delete this favourite?", ok -> {
                                if (ok) {
                                    delete(query);
                                }
                            });
                }
            }
        }));
    }

    public void show(final QueryPresenter queryPresenter, final String dashboardUuid,
                     final DocRef currentDataSource, final ExpressionOperator currentExpression) {
        this.currentDashboardUuid = dashboardUuid;
        this.currentDataSource = currentDataSource;
        this.currentExpression = currentExpression;
        this.queryPresenter = queryPresenter;

        refresh(true);
    }

    private void refresh(final boolean showAfterRefresh) {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();

        criteria.setDashboardUuid(currentDashboardUuid);
        criteria.setComponentId(queryPresenter.getId());
        criteria.setSort(FindStoredQueryCriteria.FIELD_NAME, false, true);
        criteria.setFavourite(true);
        criteria.setPageRequest(PageRequest.createDefault());

        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(result -> {
                    selectionModel.clear();
                    getView().getCellList().setRowData(result.getValues());
                    getView().getCellList().setRowCount(result.size(), true);

                    if (showAfterRefresh) {
                        final PopupSize popupSize = PopupSize.resizable(500, 400);
                        ShowPopupEvent.builder(this)
                                .popupType(PopupType.OK_CANCEL_DIALOG)
                                .popupSize(popupSize)
                                .caption("Query Favourites")
                                .onShow(e -> createButton.focus())
                                .onHideRequest(e -> {
                                    if (e.isOk()) {
                                        final StoredQuery query = selectionModel.getSelectedObject();
                                        if (query != null && query.getQuery() != null &&
                                            query.getQuery().getExpression() != null) {
                                            queryPresenter.setExpression(query.getQuery().getExpression());
                                        }
                                    }
                                    e.hide();
                                })
                                .fire();
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    private void delete(final StoredQuery query) {
        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.delete(query))
                .onSuccess(result -> refresh(false))
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    public interface QueryFavouritesView extends View {

        CellList<StoredQuery> getCellList();

        void setExpressionView(View view);

        ButtonView addButton(Preset preset);
    }
}
