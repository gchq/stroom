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

package stroom.dashboard.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dashboard.client.component.QueryFavourites.FavouriteAction;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.client.component.ExpressionTreeController;
import stroom.util.shared.PageRequest;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A dashboard query's favourites ([QueryFavourites]), in a dialog its owner keeps and shows again:
/// the user can make the current query a favourite, rename or delete one, and choose one to use.
public class QueryFavouritesController extends Controller {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final QueryFavourites view;
    private final RestFactory restFactory;
    private final ExpressionTreeController expressionTree;
    private final Provider<FavouriteNameController> favouriteNameProvider;
    private String componentId;
    private String currentDashboardUuid;
    private DocRef currentDataSource;
    private ExpressionOperator currentExpression;
    private Consumer<ExpressionOperator> onChoose;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The content.
    /// @param expressionTree        Shows the chosen favourite's expression.
    /// @param favouriteNameProvider Makes the dialog that names a favourite.
    /// @param restFactory           Finds and deletes the favourites.
    /// @param dateTimeFormatter     Formats the favourites' times.
    @Inject
    public QueryFavouritesController(final EventBus eventBus,
                                     final QueryFavourites view,
                                     final ExpressionTreeController expressionTree,
                                     final Provider<FavouriteNameController> favouriteNameProvider,
                                     final RestFactory restFactory,
                                     final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.restFactory = restFactory;
        this.expressionTree = own(expressionTree);
        this.favouriteNameProvider = favouriteNameProvider;

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);
        view.setExpressionTree(expressionTree);
        view.addColumn(dateTimeFormatter);

        view.setOnAction(this::onAction);
        view.setOnSelect(query -> {
            if (query == null || query.getQuery() == null) {
                expressionTree.read(null);
                view.setSelectionActionsEnabled(false);
            } else {
                expressionTree.read(query.getQuery().getExpression());
                view.setSelectionActionsEnabled(true);
            }
        });
        view.setOnChoose(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Fetches the query's favourites and shows them.
    ///
    /// @param componentId       The query's component.
    /// @param dashboardUuid     The query's dashboard.
    /// @param currentDataSource The query's data source, for a new favourite.
    /// @param currentExpression The query's expression, for a new favourite.
    /// @param onChoose          Given the chosen favourite's expression when OK is pressed.
    public void show(final String componentId,
                     final String dashboardUuid,
                     final DocRef currentDataSource,
                     final ExpressionOperator currentExpression,
                     final Consumer<ExpressionOperator> onChoose) {
        this.componentId = componentId;
        this.currentDashboardUuid = dashboardUuid;
        this.currentDataSource = currentDataSource;
        this.currentExpression = currentExpression;
        this.onChoose = onChoose;
        refresh(true);
    }

    private void onAction(final FavouriteAction action) {
        switch (action) {
            case CREATE -> onCreate();
            case EDIT -> onEdit();
            case DELETE -> onDelete();
        }
    }

    private void onCreate() {
        final Query query = Query.builder()
                .dataSource(currentDataSource)
                .expression(currentExpression)
                .build();
        final StoredQuery queryEntity = StoredQuery
                .builder()
                .query(query)
                .dashboardUuid(currentDashboardUuid)
                .componentId(componentId)
                .name("")
                .favourite(true)
                .build();
        favouriteNameProvider.get().show(this, queryEntity, result -> refresh(false));
    }

    private void onEdit() {
        final StoredQuery query = view.getSelected();
        if (query != null) {
            favouriteNameProvider.get().show(this, query, result -> refresh(false));
        }
    }

    private void onDelete() {
        final StoredQuery query = view.getSelected();
        if (query != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete this favourite?", ok -> {
                if (ok) {
                    delete(query);
                }
            });
        }
    }

    private void refresh(final boolean showAfterRefresh) {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(currentDashboardUuid);
        criteria.setComponentId(componentId);
        criteria.setSort(FindStoredQueryCriteria.FIELD_NAME, false, true);
        criteria.setFavourite(true);
        criteria.setPageRequest(PageRequest.createDefault());

        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(result -> {
                    view.setQueries(result.getValues());
                    if (showAfterRefresh) {
                        showDialog();
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void showDialog() {
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(500, 400))
                .caption("Query Favourites")
                .onShow(e -> view.focusCreate())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final StoredQuery query = view.getSelected();
                        if (query != null && query.getQuery() != null &&
                            query.getQuery().getExpression() != null) {
                            onChoose.accept(query.getQuery().getExpression());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    private void delete(final StoredQuery query) {
        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.delete(query))
                .onSuccess(result -> refresh(false))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

}
