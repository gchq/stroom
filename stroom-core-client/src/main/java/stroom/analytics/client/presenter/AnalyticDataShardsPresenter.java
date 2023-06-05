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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.client.presenter.QueryUiHandlers;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRanges;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;

public class AnalyticDataShardsPresenter
        extends MyPresenterWidget<AnalyticDataShardsView>
        implements HasDocumentRead<AnalyticRuleDoc>,
        AnalyticDataShardsUiHandlers,
        QueryUiHandlers {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private final AnalyticDataShardListPresenter analyticDataShardListPresenter;
    private final QueryResultTablePresenter tablePresenter;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;

    private List<String> currentWarnings;
    private TimeRange currentTimeRange = TimeRanges.ALL_TIME;
    private String analyticRuleUuid;

    @Inject
    public AnalyticDataShardsPresenter(final EventBus eventBus,
                                       final AnalyticDataShardsView view,
                                       final AnalyticDataShardListPresenter analyticDataShardListPresenter,
                                       final QueryResultTablePresenter tablePresenter,
                                       final RestFactory restFactory,
                                       final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus, view);
        this.analyticDataShardListPresenter = analyticDataShardListPresenter;
        this.tablePresenter = tablePresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        view.setListView(analyticDataShardListPresenter.getView());
        view.setTable(tablePresenter.getView());
        view.setUiHandlers(this);
        view.getQueryButtons().setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(analyticDataShardListPresenter.getSelectionModel().addSelectionHandler(e -> update()));
        registerHandler(tablePresenter.addRangeChangeHandler(e -> update()));
    }

    private void update() {
        getView().getQueryButtons().setEnabled(true);
        getView().getQueryButtons().setMode(false);
        final AnalyticDataShard selected = analyticDataShardListPresenter.getSelectionModel().getSelected();
        if (selected == null) {
            tablePresenter.reset();
        } else {
            final GetAnalyticShardDataRequest request =
                    new GetAnalyticShardDataRequest(
                            tablePresenter.getRequestedRange(),
                            currentTimeRange,
                            analyticRuleUuid,
                            selected.getPath(),
                            dateTimeSettingsFactory.getDateTimeSettings());

            getView().getQueryButtons().setMode(true);
            final Rest<Result> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        tablePresenter.setData(result);
                        getView().getQueryButtons().setMode(false);
                    })
                    .onFailure(t -> {
                        setErrors(Collections.singletonList(t.getMessage()));
                        getView().getQueryButtons().setMode(false);
                    })
                    .call(ANALYTIC_DATA_SHARD_RESOURCE)
                    .getData(selected.getNode(), request);
        }
    }

    @Override
    public void read(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        this.analyticRuleUuid = docRef.getUuid();
        analyticDataShardListPresenter.read(docRef);
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
            update();
        }
    }

    @Override
    public void start() {
        update();
    }

    public interface AnalyticDataShardsView extends View, HasUiHandlers<AnalyticDataShardsUiHandlers> {

        void setListView(View view);

        void setWarningsVisible(boolean show);

        QueryButtons getQueryButtons();

        TimeRange getTimeRange();

        void setTimeRange(TimeRange timeRange);

        void setTable(View view);
    }
}
