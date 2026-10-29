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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.HasToolbar;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.component.QueryToolbarController;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;

/// An analytic rule's Shards tab ([AnalyticDataShards]): the rule's data shards, and the rows of the
/// one selected for the query toolbar's time range, fetched again when it is run.
public class AnalyticDataShardsController
        extends Controller
        implements HasToolbar {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private final QueryToolbarController queryToolbar;
    private final AnalyticDataShardListController analyticDataShardList;
    private final QueryResultTableController tableController;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private String analyticRuleUuid;
    // The shard table's own preferences (e.g. a column's width), for as long as the tab is open
    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                The application's event bus.
    /// @param view                    The content.
    /// @param queryToolbar            Runs the query, over a time range.
    /// @param analyticDataShardList   The rule's data shards.
    /// @param tableController         The rows of the shard selected.
    /// @param restFactory             Fetches the rows.
    /// @param dateTimeSettingsFactory Gives the request the user's time settings.
    @Inject
    public AnalyticDataShardsController(final EventBus eventBus,
                                        final AnalyticDataShards view,
                                        final QueryToolbarController queryToolbar,
                                        final AnalyticDataShardListController analyticDataShardList,
                                        final QueryResultTableController tableController,
                                        final RestFactory restFactory,
                                        final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus);
        initWidget(view);
        own(queryToolbar);
        this.queryToolbar = queryToolbar;
        this.analyticDataShardList = own(analyticDataShardList).attach(view.getShardList());
        this.tableController = own(tableController).attach(view.getTable());
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        tableController.setQueryTablePreferencesSupplier(() -> queryTablePreferences);
        tableController.setQueryTablePreferencesConsumer(qtp -> queryTablePreferences = qtp);

        queryToolbar.setOnStart(this::update);
        queryToolbar.setOnTimeRangeChange(this::update);
        own(analyticDataShardList.getSelectionModel().addSelectionHandler(e -> update()));
        tableController.setOnRefreshRequest(this::update);
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbar.asWidget());
    }

    private void update() {
        queryToolbar.setEnabled(true);
        queryToolbar.onSearching(false);
        final AnalyticDataShard selected = analyticDataShardList.getSelectionModel().getSelected();
        if (selected == null) {
            tableController.reset();
        } else {
            final GetAnalyticShardDataRequest request =
                    new GetAnalyticShardDataRequest(
                            tableController.getRequestedRange(),
                            queryToolbar.getTimeRange(),
                            analyticRuleUuid,
                            selected.getPath(),
                            dateTimeSettingsFactory.getDateTimeSettings());

            queryToolbar.onSearching(true);
            restFactory
                    .create(ANALYTIC_DATA_SHARD_RESOURCE)
                    .method(res -> res.getData(selected.getNode(), request))
                    .onSuccess(result -> {
                        tableController.setData(result);
                        queryToolbar.onSearching(false);
                    })
                    .onFailure(t -> {
                        queryToolbar.onError(
                                Collections.singletonList(new ErrorMessage(Severity.ERROR, t.getMessage())));
                        queryToolbar.onSearching(false);
                    })
                    // Nothing is run if this is disposed (e.g. its tab closed) before the reply
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }


    /// Shows a rule's data shards.
    ///
    /// @param docRef   The rule.
    /// @param document The rule.
    public void read(final DocRef docRef, final AnalyticRuleDoc document) {
        this.analyticRuleUuid = docRef.getUuid();
        analyticDataShardList.read(docRef);
    }
}
