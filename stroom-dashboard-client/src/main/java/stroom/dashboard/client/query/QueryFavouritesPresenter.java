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
 *
 */

package stroom.dashboard.client.query;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.CellList;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Sort.Direction;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.client.ExpressionTreePresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

public class QueryFavouritesPresenter extends MyPresenterWidget<QueryFavouritesPresenter.QueryFavouritesView> {
    private final ClientDispatchAsync dispatcher;
    private final ExpressionTreePresenter expressionPresenter;
    private final MySingleSelectionModel<QueryEntity> selectionModel;
    private final NamePresenter namePresenter;
    private final ButtonView createButton;
    private final ButtonView editButton;
    private final ButtonView deleteButton;
    private QueryPresenter queryPresenter;
    private ExpressionOperator currentExpression;
    private String currentDashboardUuid;
    private DocRef currentDataSource;

    @Inject
    public QueryFavouritesPresenter(final EventBus eventBus, final QueryFavouritesView view,
                                    final ExpressionTreePresenter expressionPresenter, final NamePresenter namePresenter,
                                    final ClientSecurityContext securityContext,
                                    final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.expressionPresenter = expressionPresenter;
        this.namePresenter = namePresenter;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setExpressionView(expressionPresenter.getView());

        selectionModel = new MySingleSelectionModel<>();
        view.getCellList().setSelectionModel(selectionModel);

        createButton = view.addButton(SvgPresets.NEW_ITEM);
        createButton.setTitle("Create Favourite From Current Query");
        editButton = view.addButton(SvgPresets.OPEN);
        editButton.setTitle("Change Favourite Name");
        deleteButton = view.addButton(SvgPresets.DELETE);
        deleteButton.setTitle("Delete Favourite");
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event -> {
            final QueryEntity query = selectionModel.getSelectedObject();

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
        registerHandler(selectionModel.addDoubleSelectHandler(event -> close(true)));
        registerHandler(createButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            String entityName = namePresenter.getName();
                            if (entityName != null) {
                                entityName = entityName.trim();
                            }

                            if (entityName == null || entityName.length() == 0) {
                                AlertEvent.fireWarn(QueryFavouritesPresenter.this, "You must provide a name", null);

                            } else {
                                final Query query = new Query(currentDataSource, currentExpression);
                                final QueryEntity queryEntity = new QueryEntity();
                                queryEntity.setQuery(query);
                                queryEntity.setDashboardUuid(currentDashboardUuid);
                                queryEntity.setQueryId(queryPresenter.getId());
                                queryEntity.setName(entityName);
                                queryEntity.setFavourite(true);

                                save(queryEntity, autoClose, ok);
                            }
                        } else {
                            HidePopupEvent.fire(QueryFavouritesPresenter.this, namePresenter, autoClose, ok);
                        }
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                    }
                };

                namePresenter.setName("");
                namePresenter.setUihandlers(popupUiHandlers);
                final PopupSize popupSize = new PopupSize(350, 78, 300, 78, 1024, 78, true);
                ShowPopupEvent.fire(QueryFavouritesPresenter.this, namePresenter, PopupType.OK_CANCEL_DIALOG,
                        popupSize, "Create New Favourite", popupUiHandlers);
                namePresenter.getView().focus();
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final QueryEntity query = selectionModel.getSelectedObject();
                if (query != null) {
                    final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                        @Override
                        public void onHideRequest(final boolean autoClose, final boolean ok) {
                            if (ok) {
                                String entityName = namePresenter.getName();
                                if (entityName != null) {
                                    entityName = entityName.trim();
                                }

                                if (entityName == null || entityName.length() == 0) {
                                    AlertEvent.fireWarn(QueryFavouritesPresenter.this, "You must provide a name",
                                            null);

                                } else {
                                    query.setName(entityName);
                                    query.setFavourite(true);
                                    save(query, autoClose, ok);
                                }
                            } else {
                                HidePopupEvent.fire(QueryFavouritesPresenter.this, namePresenter, autoClose, ok);
                            }
                        }

                        @Override
                        public void onHide(final boolean autoClose, final boolean ok) {
                        }
                    };

                    namePresenter.setName(query.getName());
                    namePresenter.setUihandlers(popupUiHandlers);
                    ShowPopupEvent.fire(QueryFavouritesPresenter.this, namePresenter, PopupType.OK_CANCEL_DIALOG,
                            "Rename Favourite", popupUiHandlers);
                    // getView().focus();
                }
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final QueryEntity query = selectionModel.getSelectedObject();
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
        final FindQueryCriteria criteria = new FindQueryCriteria();

        criteria.setDashboardUuid(currentDashboardUuid);
        criteria.setQueryId(queryPresenter.getId());
        criteria.setSort(FindQueryCriteria.FIELD_NAME, Direction.ASCENDING, true);
        criteria.setFavourite(true);
        criteria.setPageRequest(new PageRequest(0L, 100));

        final EntityServiceFindAction<FindQueryCriteria, QueryEntity> action = new EntityServiceFindAction<>(criteria);
        dispatcher.exec(action).onSuccess(result -> {
            selectionModel.clear();
            getView().getCellList().setRowData(result);
            getView().getCellList().setRowCount(result.size(), true);

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
                ShowPopupEvent.fire(queryPresenter, QueryFavouritesPresenter.this, PopupType.OK_CANCEL_DIALOG,
                        popupSize, "Query Favourites", popupUiHandlers);
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

        HidePopupEvent.fire(queryPresenter, QueryFavouritesPresenter.this);
    }

    private void save(final QueryEntity query, final boolean autoClose, final boolean ok) {
        dispatcher.exec(new DocumentServiceWriteAction<>(query)).onSuccess(result -> {
            refresh(false);
            HidePopupEvent.fire(QueryFavouritesPresenter.this, namePresenter, autoClose, ok);
        });
    }

    private void delete(final QueryEntity query) {
        dispatcher.exec(new EntityServiceDeleteAction(query)).onSuccess(result -> refresh(false));
    }

    public interface QueryFavouritesView extends View {
        CellList<QueryEntity> getCellList();

        void setExpressionView(View view);

        ButtonView addButton(SvgPreset preset);
    }
}
