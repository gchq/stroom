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
import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.shared.AnalyticProcess;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticProcessTracker;
import stroom.analytics.shared.AnalyticProcessTrackerData;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.FindAnalyticProcessCriteria;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessTrackerData;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.analytics.shared.StreamingAnalyticProcessTrackerData;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticProcessTrackerData;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.node.client.NodeManager;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class AnalyticProcessingPresenter
        extends DocumentEditPresenter<AnalyticProcessingView, AnalyticRuleDoc>
        implements AnalyticProcessingUiHandlers, HasChangeDataHandlers<AnalyticProcessType> {

    private static final AnalyticProcessResource ANALYTIC_PROCESSOR_FILTER_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private AnalyticProcess loadedFilter;

    private String analyticRuleUuid;

    @Inject
    public AnalyticProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final RestFactory restFactory,
                                       final DateTimeFormatter dateTimeFormatter,
                                       final NodeManager nodeManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        view.setUiHandlers(this);

        nodeManager.listAllNodes(
                list -> {
                    if (list != null && list.size() > 0) {
                        view.setNodes(list);
                    }
                },
                throwable -> AlertEvent
                        .fireError(this,
                                "Error",
                                throwable.getMessage(),
                                null));
    }

    private void read(final AnalyticProcess process) {
        if (process == null) {
            AnalyticProcess newFilter = AnalyticProcess
                    .builder()
                    .analyticUuid(analyticRuleUuid)
                    .build();
            read(
                    newFilter.isEnabled(),
                    newFilter.getNode());

        } else {
            loadedFilter = process;
            read(
                    process.isEnabled(),
                    process.getNode());
            refreshTracker();
        }
    }

    @Override
    public void onRefreshProcessingStatus() {
        refreshTracker();
    }

    @Override
    public void onProcessingTypeChange() {
        ChangeDataEvent.fire(this, getView().getProcessingType());
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<AnalyticProcessType> handler) {
        return addHandlerToSource(ChangeDataEvent.getType(), handler);
    }

    private void refreshTracker() {
        if (loadedFilter != null && loadedFilter.getUuid() != null) {
            final Rest<AnalyticProcessTracker> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final SafeHtml safeHtml = getInfo(result.getAnalyticProcessTrackerData());
                        getView().setInfo(safeHtml);
                    })
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .getTracker(loadedFilter.getUuid());
        }
    }

    public SafeHtml getInfo(final AnalyticProcessTrackerData trackerData) {
        final TableBuilder tb = new TableBuilder();

        if (trackerData instanceof TableBuilderAnalyticProcessTrackerData) {
            final TableBuilderAnalyticProcessTrackerData td =
                    (TableBuilderAnalyticProcessTrackerData) trackerData;

            addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
            tb.row(SafeHtmlUtil.from("Last Stream Count"), SafeHtmlUtil.from(td.getLastStreamCount()));
            tb.row(SafeHtmlUtil.from("Last Stream Id"), SafeHtmlUtil.from(td.getLastStreamId()));
            tb.row(SafeHtmlUtil.from("Last Event Id"), SafeHtmlUtil.from(td.getLastEventId()));
            addRowDateString(tb, "Last Event Time", td.getLastEventTime());
            tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(td.getTotalStreamCount()));
            tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(td.getTotalEventCount()));
            tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));

        } else if (trackerData instanceof ScheduledQueryAnalyticProcessTrackerData) {
            final ScheduledQueryAnalyticProcessTrackerData td =
                    (ScheduledQueryAnalyticProcessTrackerData) trackerData;

            addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
            addRowDateString(tb, "Last Window Start Time", td.getLastWindowStartTimeMs());
            addRowDateString(tb, "Last Window End Time", td.getLastWindowEndTimeMs());
            tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));

        } else if (trackerData instanceof StreamingAnalyticProcessTrackerData) {
            final StreamingAnalyticProcessTrackerData td =
                    (StreamingAnalyticProcessTrackerData) trackerData;

            addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
            tb.row(SafeHtmlUtil.from("Last Stream Count"), SafeHtmlUtil.from(td.getLastStreamCount()));
            tb.row(SafeHtmlUtil.from("Last Steam Id"), SafeHtmlUtil.from(td.getLastStreamId()));
            tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(td.getTotalStreamCount()));
            tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(td.getTotalEventCount()));
            tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            tb.row(label, dateTimeFormatter.formatWithDuration(ms));
        }
    }

    private void read(final boolean enabled,
                      final String node) {
        getView().setEnabled(enabled);
        getView().setNode(node);
    }

    private AnalyticProcess write(final AnalyticProcess filter) {
        return filter.copy()
                .enabled(getView().isEnabled())
                .node(getView().getNode())
                .build();
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        analyticRuleUuid = analyticRuleDoc.getUuid();
        refresh(analyticRuleUuid);

        getView().setProcessingType(analyticRuleDoc.getAnalyticProcessType() == null
                ? AnalyticProcessType.SCHEDULED_QUERY
                : analyticRuleDoc.getAnalyticProcessType());

        if (analyticRuleDoc.getAnalyticProcessConfig() instanceof TableBuilderAnalyticProcessConfig) {
            final TableBuilderAnalyticProcessConfig ac =
                    (TableBuilderAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig();
            getView().setDataRetention(ac.getDataRetention());
            getView().setTimeToWaitForData(ac.getTimeToWaitForData());
            getView().setMaxMetaCreateTimeMs(ac.getMinMetaCreateTimeMs());
            getView().setMaxMetaCreateTimeMs(ac.getMaxMetaCreateTimeMs());

        } else if (analyticRuleDoc.getAnalyticProcessConfig() instanceof ScheduledQueryAnalyticProcessConfig) {
            final ScheduledQueryAnalyticProcessConfig ac =
                    (ScheduledQueryAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig();

            getView().setQueryFrequency(ac.getQueryFrequency());
            getView().setTimeToWaitForData(ac.getTimeToWaitForData());
            getView().setMaxMetaCreateTimeMs(ac.getMinEventTimeMs());
            getView().setMaxMetaCreateTimeMs(ac.getMaxEventTimeMs());

        } else if (analyticRuleDoc.getAnalyticProcessConfig() instanceof StreamingAnalyticProcessConfig) {
            final StreamingAnalyticProcessConfig ac =
                    (StreamingAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig();

            getView().setMaxMetaCreateTimeMs(ac.getMinMetaCreateTimeMs());
            getView().setMaxMetaCreateTimeMs(ac.getMaxMetaCreateTimeMs());
        }
    }

    private void refresh(final String analyticDocUuid) {
        final FindAnalyticProcessCriteria criteria = new FindAnalyticProcessCriteria();
        criteria.setAnalyticDocUuid(analyticDocUuid);
        final Rest<ResultPage<AnalyticProcess>> rest = restFactory.create();
        rest
                .onSuccess(result -> read(result.getFirst()))
                .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                .find(criteria);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        if (loadedFilter != null) {
            loadedFilter = write(loadedFilter);
            final Rest<AnalyticProcess> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .update(loadedFilter.getUuid(), loadedFilter);
        } else {
            final AnalyticProcess newProcess = write(AnalyticProcess
                    .builder()
                    .analyticUuid(analyticRuleUuid)
                    .build());
            final Rest<AnalyticProcess> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .create(newProcess);
        }

        AnalyticProcessConfig analyticProcessConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                analyticProcessConfig = new StreamingAnalyticProcessConfig(
                        getView().getMinMetaCreateTimeMs(),
                        getView().getMaxMetaCreateTimeMs());
                break;
            case TABLE_BUILDER:
                analyticProcessConfig =
                        new TableBuilderAnalyticProcessConfig(
                                getView().getMinMetaCreateTimeMs(),
                                getView().getMaxMetaCreateTimeMs(),
                                getView().getTimeToWaitForData(),
                                getView().getDataRetention());
                break;
            case SCHEDULED_QUERY:
                analyticProcessConfig =
                        new ScheduledQueryAnalyticProcessConfig(
                                getView().getMinMetaCreateTimeMs(),
                                getView().getMaxMetaCreateTimeMs(),
                                getView().getTimeToWaitForData(),
                                getView().getQueryFrequency());
                break;
        }

        return analyticRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .analyticProcessType(getView().getProcessingType())
                .analyticProcessConfig(analyticProcessConfig)
                .build();
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    public interface AnalyticProcessingView extends View, HasUiHandlers<AnalyticProcessingUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        void setNodes(final List<String> nodes);

        String getNode();

        void setNode(final String node);

        AnalyticProcessType getProcessingType();

        void setProcessingType(AnalyticProcessType analyticProcessType);

        SimpleDuration getQueryFrequency();

        void setQueryFrequency(SimpleDuration queryFrequency);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        SimpleDuration getDataRetention();

        void setDataRetention(SimpleDuration dataRetention);

        void setInfo(SafeHtml info);
    }
}
