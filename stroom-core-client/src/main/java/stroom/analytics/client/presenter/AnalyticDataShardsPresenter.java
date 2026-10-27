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

package stroom.analytics.client.presenter;

import stroom.analytics.client.component.AnalyticDataShardListController;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.client.component.QueryToolbarController;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;

public class AnalyticDataShardsPresenter
        extends DocPresenter<AnalyticDataShardsView, AnalyticRuleDoc>
        implements HasToolbar {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private final QueryToolbarController queryToolbar;
    private final AnalyticDataShardListController analyticDataShardList;
    private final QueryResultTablePresenter tablePresenter;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private String analyticRuleUuid;
    // The shard table's own preferences (e.g. a column's width), for as long as the tab is open
    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();

    @Inject
    public AnalyticDataShardsPresenter(final EventBus eventBus,
                                       final AnalyticDataShardsView view,
                                       final QueryToolbarController queryToolbar,
                                       final AnalyticDataShardListController analyticDataShardList,
                                       final QueryResultTablePresenter tablePresenter,
                                       final RestFactory restFactory,
                                       final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus, view);
        own(queryToolbar);
        own(analyticDataShardList);
        own(tablePresenter);
        this.queryToolbar = queryToolbar;
        this.analyticDataShardList = analyticDataShardList;
        this.tablePresenter = tablePresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        view.setListView(analyticDataShardList);
        view.setTable(tablePresenter.getView());
        tablePresenter.setQueryTablePreferencesSupplier(() -> queryTablePreferences);
        tablePresenter.setQueryTablePreferencesConsumer(qtp -> queryTablePreferences = qtp);

        queryToolbar.setOnStart(this::update);
        queryToolbar.setOnTimeRangeChange(this::update);
        own(analyticDataShardList.getSelectionModel().addSelectionHandler(e -> update()));
        tablePresenter.setOnRefreshRequest(this::update);
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
            tablePresenter.reset();
        } else {
            final GetAnalyticShardDataRequest request =
                    new GetAnalyticShardDataRequest(
                            tablePresenter.getRequestedRange(),
                            queryToolbar.getTimeRange(),
                            analyticRuleUuid,
                            selected.getPath(),
                            dateTimeSettingsFactory.getDateTimeSettings());

            queryToolbar.onSearching(true);
            restFactory
                    .create(ANALYTIC_DATA_SHARD_RESOURCE)
                    .method(res -> res.getData(selected.getNode(), request))
                    .onSuccess(result -> {
                        tablePresenter.setData(result);
                        queryToolbar.onSearching(false);
                    })
                    .onFailure(t -> {
                        queryToolbar.onError(
                                Collections.singletonList(new ErrorMessage(Severity.ERROR, t.getMessage())));
                        queryToolbar.onSearching(false);
                    })
                    // Nothing is run if this is disposed (e.g. its tab closed) before the reply
                    .owner(this)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document) {
        this.analyticRuleUuid = docRef.getUuid();
        analyticDataShardList.read(docRef);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        return document;
    }

    public interface AnalyticDataShardsView extends View {

        void setListView(IsWidget view);

        void setTable(View view);
    }
}
