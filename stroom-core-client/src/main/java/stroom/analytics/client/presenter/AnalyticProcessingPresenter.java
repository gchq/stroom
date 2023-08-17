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
import stroom.analytics.shared.AnalyticConfig;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterResource;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.analytics.shared.FindAnalyticProcessorFilterCriteria;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ScheduledQueryAnalyticConfig;
import stroom.analytics.shared.StreamingAnalyticConfig;
import stroom.analytics.shared.TableBuilderAnalyticConfig;
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
        implements AnalyticProcessingUiHandlers, HasChangeDataHandlers<AnalyticRuleType> {

    private static final AnalyticProcessorFilterResource ANALYTIC_PROCESSOR_FILTER_RESOURCE =
            GWT.create(AnalyticProcessorFilterResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private AnalyticProcessorFilter loadedFilter;

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

    private void read(final AnalyticProcessorFilter filter) {
        if (filter == null) {
            AnalyticProcessorFilter newFilter = AnalyticProcessorFilter
                    .builder()
                    .analyticUuid(analyticRuleUuid)
                    .minMetaCreateTimeMs(System.currentTimeMillis())
                    .build();
            read(
                    newFilter.isEnabled(),
                    newFilter.getMinMetaCreateTimeMs(),
                    newFilter.getMaxMetaCreateTimeMs(),
                    newFilter.getNode());

        } else {
            loadedFilter = filter;
            read(
                    filter.isEnabled(),
                    filter.getMinMetaCreateTimeMs(),
                    filter.getMaxMetaCreateTimeMs(),
                    filter.getNode());
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
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<AnalyticRuleType> handler) {
        return addHandlerToSource(ChangeDataEvent.getType(), handler);
    }

    private void refreshTracker() {
        if (loadedFilter != null && loadedFilter.getUuid() != null) {
            final Rest<AnalyticProcessorFilterTracker> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final SafeHtml safeHtml = getInfo(result);
                        getView().setInfo(safeHtml);
                    })
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .getTracker(loadedFilter.getUuid());
        }
    }

    public SafeHtml getInfo(final AnalyticProcessorFilterTracker tracker) {
        final TableBuilder tb = new TableBuilder();

        if (tracker != null) {
            addRowDateString(tb, "Last Poll Time", tracker.getLastPollMs());
            tb.row(SafeHtmlUtil.from("Last Poll Task Count"), SafeHtmlUtil.from(tracker.getLastPollTaskCount()));
            tb.row(SafeHtmlUtil.from("Last Meta Id"), SafeHtmlUtil.from(tracker.getLastMetaId()));
            tb.row(SafeHtmlUtil.from("Last Event Id"), SafeHtmlUtil.from(tracker.getLastEventId()));
            addRowDateString(tb, "Last Event Time", tracker.getLastEventTime());
            tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(tracker.getMetaCount()));
            tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(tracker.getEventCount()));
            tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(tracker.getMessage()));
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
                      final Long minMetaCreateTimeMs,
                      final Long maxMetaCreateTimeMs,
                      final String node) {
        getView().setEnabled(enabled);
        getView().setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        getView().setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
        getView().setNode(node);
    }

    private AnalyticProcessorFilter write(final AnalyticProcessorFilter filter) {
        return filter.copy()
                .enabled(getView().isEnabled())
                .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                .node(getView().getNode())
                .build();
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        analyticRuleUuid = analyticRuleDoc.getUuid();
        refresh(analyticRuleUuid);

        getView().setProcessingType(analyticRuleDoc.getAnalyticRuleType() == null
                ? AnalyticRuleType.SCHEDULED_QUERY
                : analyticRuleDoc.getAnalyticRuleType());

        if (analyticRuleDoc.getAnalyticConfig() instanceof TableBuilderAnalyticConfig) {
            final TableBuilderAnalyticConfig tableBuilderAnalyticConfig =
                    (TableBuilderAnalyticConfig) analyticRuleDoc.getAnalyticConfig();
            getView().setDataRetention(tableBuilderAnalyticConfig.getDataRetention());
            getView().setTimeToWaitForData(tableBuilderAnalyticConfig.getTimeToWaitForData());
        } else if (analyticRuleDoc.getAnalyticConfig() instanceof ScheduledQueryAnalyticConfig) {
            final ScheduledQueryAnalyticConfig scheduledQueryAnalyticConfig =
                    (ScheduledQueryAnalyticConfig) analyticRuleDoc.getAnalyticConfig();

            getView().setQueryFrequency(scheduledQueryAnalyticConfig.getQueryFrequency());
            getView().setTimeToWaitForData(scheduledQueryAnalyticConfig.getTimeToWaitForData());
        }
    }

    private void refresh(final String analyticDocUuid) {
        final FindAnalyticProcessorFilterCriteria criteria = new FindAnalyticProcessorFilterCriteria();
        criteria.setAnalyticDocUuid(analyticDocUuid);
        final Rest<ResultPage<AnalyticProcessorFilter>> rest = restFactory.create();
        rest
                .onSuccess(result -> read(result.getFirst()))
                .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                .find(criteria);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        if (loadedFilter != null) {
            loadedFilter = write(loadedFilter);
            final Rest<AnalyticProcessorFilter> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .update(loadedFilter.getUuid(), loadedFilter);
        } else {
            final AnalyticProcessorFilter newFilter = write(AnalyticProcessorFilter
                    .builder()
                    .analyticUuid(analyticRuleUuid)
                    .build());
            final Rest<AnalyticProcessorFilter> rest = restFactory.create();
            rest
                    .onSuccess(result -> refresh(analyticRuleUuid))
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .create(newFilter);
        }

        AnalyticConfig analyticConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                analyticConfig = new StreamingAnalyticConfig();
                break;
            case TABLE_BUILDER:
                analyticConfig =
                        new TableBuilderAnalyticConfig(getView().getTimeToWaitForData(), getView().getDataRetention());
                break;
            case SCHEDULED_QUERY:
                analyticConfig =
                        new ScheduledQueryAnalyticConfig(getView().getTimeToWaitForData(),
                                getView().getQueryFrequency());
                break;
        }

        return analyticRuleDoc.copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .analyticRuleType(getView().getProcessingType())
                .analyticConfig(analyticConfig)
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

        AnalyticRuleType getProcessingType();

        void setProcessingType(AnalyticRuleType analyticRuleType);

        SimpleDuration getQueryFrequency();

        void setQueryFrequency(SimpleDuration queryFrequency);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        SimpleDuration getDataRetention();

        void setDataRetention(SimpleDuration dataRetention);

        void setInfo(SafeHtml info);
    }
}
