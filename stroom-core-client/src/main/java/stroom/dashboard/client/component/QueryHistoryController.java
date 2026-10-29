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

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.StoredQueryResource;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionTreeController;
import stroom.util.shared.PageRequest;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A dashboard query's history ([QueryHistory]), in a dialog its owner keeps and shows again: the
/// user chooses an earlier query to use.
public class QueryHistoryController extends Controller {

    private static final StoredQueryResource STORED_QUERY_RESOURCE = GWT.create(StoredQueryResource.class);

    private final QueryHistory view;
    private final RestFactory restFactory;
    private String componentId;
    private String currentDashboardUuid;
    private Consumer<ExpressionOperator> onChoose;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param expressionTree    Shows the chosen query's expression.
    /// @param restFactory       Finds the queries.
    /// @param dateTimeFormatter Formats the queries' times.
    @Inject
    public QueryHistoryController(final EventBus eventBus,
                                  final QueryHistory view,
                                  final ExpressionTreeController expressionTree,
                                  final RestFactory restFactory,
                                  final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.restFactory = restFactory;
        own(expressionTree).attach(view.getExpressionTree());

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);
        view.addColumn(dateTimeFormatter);

        view.setOnSelect(query -> expressionTree.read(query == null || query.getQuery() == null
                ? null
                : query.getQuery().getExpression()));
        view.setOnChoose(() -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Fetches the query's history and shows it.
    ///
    /// @param componentId   The query's component.
    /// @param dashboardUuid The query's dashboard.
    /// @param onChoose      Given the chosen query's expression when OK is pressed.
    public void show(final String componentId,
                     final String dashboardUuid,
                     final Consumer<ExpressionOperator> onChoose) {
        this.componentId = componentId;
        this.currentDashboardUuid = dashboardUuid;
        this.onChoose = onChoose;
        refresh();
    }

    private void refresh() {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(currentDashboardUuid);
        criteria.setComponentId(componentId);
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, true, false);
        criteria.setFavourite(false);
        criteria.setPageRequest(PageRequest.createDefault());

        restFactory
                .create(STORED_QUERY_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(result -> {
                    view.setQueries(dedupe(result.getValues()));
                    showDialog();
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    // Leaves out a query with the same expression as the one before it
    private static List<StoredQuery> dedupe(final List<StoredQuery> queries) {
        ExpressionOperator lastExpression = null;
        final List<StoredQuery> dedupedList = new ArrayList<>(queries.size());
        for (final StoredQuery queryEntity : queries) {
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
        return dedupedList;
    }

    private void showDialog() {
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(500, 400))
                .caption("Query History")
                .onShow(e -> view.selectFirst())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final StoredQuery query = view.getSelected();
                        if (query != null && query.getQuery() != null
                            && query.getQuery().getExpression() != null) {
                            onChoose.accept(query.getQuery().getExpression());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

}
