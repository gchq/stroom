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
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.ScheduledQueryAnalyticTrackerData;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.analytics.shared.StreamingAnalyticTrackerData;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticTrackerData;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.node.client.NodeManager;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermissionNames;
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
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.Objects;

public class AnalyticProcessingPresenter
        extends DocumentEditPresenter<AnalyticProcessingView, AnalyticRuleDoc>
        implements AnalyticProcessingUiHandlers, HasChangeDataHandlers<AnalyticProcessType> {

    private static final AnalyticProcessResource ANALYTIC_PROCESSOR_FILTER_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final EditorPresenter editorPresenter;
    private final EntityDropDownPresenter errorFeedPresenter;
    private DocRef currentErrorFeed;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public AnalyticProcessingPresenter(final EventBus eventBus,
                                       final AnalyticProcessingView view,
                                       final EntityDropDownPresenter errorFeedPresenter,
                                       final RestFactory restFactory,
                                       final EditorPresenter editorPresenter,
                                       final DateTimeFormatter dateTimeFormatter,
                                       final NodeManager nodeManager) {
        super(eventBus, view);
        this.errorFeedPresenter = errorFeedPresenter;
        this.restFactory = restFactory;
        this.editorPresenter = editorPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        view.setUiHandlers(this);


        errorFeedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        view.setErrorFeedView(errorFeedPresenter.getView());

        view.setQueryEditorView(editorPresenter.getView());

        this.editorPresenter.setMode(AceEditorMode.STROOM_QUERY);

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

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(editorPresenter.addValueChangeHandler(event -> {
            setDirty(true);
        }));
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> {
            if (!Objects.equals(errorFeedPresenter.getSelectedEntityReference(), currentErrorFeed)) {
                currentErrorFeed = errorFeedPresenter.getSelectedEntityReference();
                onDirty();
            }
        }));
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
        if (getEntity() != null && getEntity().getUuid() != null) {
            final Rest<AnalyticTracker> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final SafeHtml safeHtml = getInfo(result);
                        getView().setInfo(safeHtml);
                    })
                    .call(ANALYTIC_PROCESSOR_FILTER_RESOURCE)
                    .getTracker(getEntity().getUuid());
        }
    }

    public SafeHtml getInfo(final AnalyticTracker tracker) {
        final TableBuilder tb = new TableBuilder();

        if (tracker != null) {
            final AnalyticTrackerData trackerData = tracker.getAnalyticTrackerData();
            if (trackerData instanceof TableBuilderAnalyticTrackerData) {
                final TableBuilderAnalyticTrackerData td =
                        (TableBuilderAnalyticTrackerData) trackerData;

                addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
                tb.row(SafeHtmlUtil.from("Last Stream Count"), SafeHtmlUtil.from(td.getLastStreamCount()));
                tb.row(SafeHtmlUtil.from("Last Stream Id"), SafeHtmlUtil.from(td.getLastStreamId()));
                tb.row(SafeHtmlUtil.from("Last Event Id"), SafeHtmlUtil.from(td.getLastEventId()));
                addRowDateString(tb, "Last Event Time", td.getLastEventTime());
                tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(td.getTotalStreamCount()));
                tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(td.getTotalEventCount()));
                if (td.getMessage() != null && td.getMessage().length() > 0) {
                    tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
                }

            } else if (trackerData instanceof ScheduledQueryAnalyticTrackerData) {
                final ScheduledQueryAnalyticTrackerData td =
                        (ScheduledQueryAnalyticTrackerData) trackerData;

                addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
                addRowDateString(tb, "Last Window Start Time", td.getLastWindowStartTimeMs());
                addRowDateString(tb, "Last Window End Time", td.getLastWindowEndTimeMs());
                if (td.getMessage() != null && td.getMessage().length() > 0) {
                    tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
                }

            } else if (trackerData instanceof StreamingAnalyticTrackerData) {
                final StreamingAnalyticTrackerData td =
                        (StreamingAnalyticTrackerData) trackerData;

                addRowDateString(tb, "Last Execution Time", td.getLastExecutionTimeMs());
                tb.row(SafeHtmlUtil.from("Last Stream Count"), SafeHtmlUtil.from(td.getLastStreamCount()));
                tb.row(SafeHtmlUtil.from("Last Steam Id"), SafeHtmlUtil.from(td.getLastStreamId()));
                tb.row(SafeHtmlUtil.from("Total Streams Processed"), SafeHtmlUtil.from(td.getTotalStreamCount()));
                tb.row(SafeHtmlUtil.from("Total Events Processed"), SafeHtmlUtil.from(td.getTotalEventCount()));
                if (td.getMessage() != null && td.getMessage().length() > 0) {
                    tb.row(SafeHtmlUtil.from("Message"), SafeHtmlUtil.from(td.getMessage()));
                }
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            if (ms == 0) {
                tb.row(label, "---");
            } else {
                tb.row(label, dateTimeFormatter.formatWithDuration(ms));
            }
        }
    }


    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc analyticRuleDoc, final boolean readOnly) {
        final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
        if (analyticProcessConfig != null) {
            editorPresenter.setText(analyticRuleDoc.getQuery());
            getView().setEnabled(analyticProcessConfig.isEnabled());
            getView().setNode(analyticProcessConfig.getNode());

            this.currentErrorFeed = analyticProcessConfig.getErrorFeed();
            errorFeedPresenter.setSelectedEntityReference(currentErrorFeed);

            getView().setProcessingType(analyticRuleDoc.getAnalyticProcessType() == null
                    ? AnalyticProcessType.SCHEDULED_QUERY
                    : analyticRuleDoc.getAnalyticProcessType());

            if (analyticProcessConfig instanceof TableBuilderAnalyticProcessConfig) {
                final TableBuilderAnalyticProcessConfig ac =
                        (TableBuilderAnalyticProcessConfig) analyticProcessConfig;
                getView().setDataRetention(ac.getDataRetention());
                getView().setTimeToWaitForData(ac.getTimeToWaitForData());
                getView().setMinMetaCreateTimeMs(ac.getMinMetaCreateTimeMs());
                getView().setMaxMetaCreateTimeMs(ac.getMaxMetaCreateTimeMs());

            } else if (analyticProcessConfig instanceof ScheduledQueryAnalyticProcessConfig) {
                final ScheduledQueryAnalyticProcessConfig ac =
                        (ScheduledQueryAnalyticProcessConfig) analyticProcessConfig;

                getView().setQueryFrequency(ac.getQueryFrequency());
                getView().setTimeToWaitForData(ac.getTimeToWaitForData());
                getView().setMinEventTimeMs(ac.getMinEventTimeMs());
                getView().setMaxEventTimeMs(ac.getMaxEventTimeMs());

            } else if (analyticProcessConfig instanceof StreamingAnalyticProcessConfig) {
                final StreamingAnalyticProcessConfig ac =
                        (StreamingAnalyticProcessConfig) analyticProcessConfig;

                getView().setMinMetaCreateTimeMs(ac.getMinMetaCreateTimeMs());
                getView().setMaxMetaCreateTimeMs(ac.getMaxMetaCreateTimeMs());
            }
        }

        refreshTracker();
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc analyticRuleDoc) {
        AnalyticProcessConfig analyticProcessConfig = null;
        switch (getView().getProcessingType()) {
            case STREAMING:
                analyticProcessConfig = StreamingAnalyticProcessConfig.builder()
                        .enabled(getView().isEnabled())
                        .node(getView().getNode())
                        .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                        .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                        .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                        .build();
                break;
            case TABLE_BUILDER:
                analyticProcessConfig = TableBuilderAnalyticProcessConfig.builder()
                        .enabled(getView().isEnabled())
                        .node(getView().getNode())
                        .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                        .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                        .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                        .timeToWaitForData(getView().getTimeToWaitForData())
                        .dataRetention(getView().getDataRetention())
                        .build();
                break;
            case SCHEDULED_QUERY:
                analyticProcessConfig = ScheduledQueryAnalyticProcessConfig.builder()
                        .enabled(getView().isEnabled())
                        .node(getView().getNode())
                        .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                        .minEventTimeMs(getView().getMinEventTimeMs())
                        .maxEventTimeMs(getView().getMaxEventTimeMs())
                        .timeToWaitForData(getView().getTimeToWaitForData())
                        .queryFrequency(getView().getQueryFrequency())
                        .build();
                break;
        }

        return analyticRuleDoc.copy()
                .query(editorPresenter.getText())
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

        void setQueryEditorView(View view);

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        Long getMinEventTimeMs();

        void setMinEventTimeMs(Long minEventTimeMs);

        Long getMaxEventTimeMs();

        void setMaxEventTimeMs(Long maxEventTimeMs);

        void setNodes(final List<String> nodes);

        String getNode();

        void setNode(final String node);

        void setErrorFeedView(View view);

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
