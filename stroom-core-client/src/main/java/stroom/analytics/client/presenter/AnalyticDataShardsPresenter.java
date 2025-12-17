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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;

public class AnalyticDataShardsPresenter
        extends DocumentEditPresenter<AnalyticDataShardsView, AnalyticRuleDoc>
        implements HasToolbar {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private final QueryToolbarPresenter queryToolbarPresenter;
    private final AnalyticDataShardListPresenter analyticDataShardListPresenter;
    private final QueryResultTablePresenter tablePresenter;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private String analyticRuleUuid;

    @Inject
    public AnalyticDataShardsPresenter(final EventBus eventBus,
                                       final AnalyticDataShardsView view,
                                       final QueryToolbarPresenter queryToolbarPresenter,
                                       final AnalyticDataShardListPresenter analyticDataShardListPresenter,
                                       final QueryResultTablePresenter tablePresenter,
                                       final RestFactory restFactory,
                                       final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus, view);
        this.queryToolbarPresenter = queryToolbarPresenter;
        this.analyticDataShardListPresenter = analyticDataShardListPresenter;
        this.tablePresenter = tablePresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        view.setListView(analyticDataShardListPresenter.getView());
        view.setTable(tablePresenter.getView());
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbarPresenter.getWidget());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryToolbarPresenter.addStartQueryHandler(e -> update()));
        registerHandler(queryToolbarPresenter.addTimeRangeChangeHandler(e -> update()));
        registerHandler(analyticDataShardListPresenter.getSelectionModel().addSelectionHandler(e -> update()));
        registerHandler(tablePresenter.addRefreshRequestHandler(e -> update()));
    }

    private void update() {
        queryToolbarPresenter.setEnabled(true);
        queryToolbarPresenter.onSearching(false);
        final AnalyticDataShard selected = analyticDataShardListPresenter.getSelectionModel().getSelected();
        if (selected == null) {
            tablePresenter.reset();
        } else {
            final GetAnalyticShardDataRequest request =
                    new GetAnalyticShardDataRequest(
                            tablePresenter.getRequestedRange(),
                            queryToolbarPresenter.getTimeRange(),
                            analyticRuleUuid,
                            selected.getPath(),
                            dateTimeSettingsFactory.getDateTimeSettings());

            queryToolbarPresenter.onSearching(true);
            restFactory
                    .create(ANALYTIC_DATA_SHARD_RESOURCE)
                    .method(res -> res.getData(selected.getNode(), request))
                    .onSuccess(result -> {
                        tablePresenter.setData(result);
                        queryToolbarPresenter.onSearching(false);
                    })
                    .onFailure(t -> {
                        queryToolbarPresenter.onError(
                                Collections.singletonList(new ErrorMessage(Severity.ERROR, t.getMessage())));
                        queryToolbarPresenter.onSearching(false);
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        this.analyticRuleUuid = docRef.getUuid();
        analyticDataShardListPresenter.read(docRef);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        return document;
    }

    public interface AnalyticDataShardsView extends View {

        void setListView(View view);

        void setTable(View view);
    }
}
