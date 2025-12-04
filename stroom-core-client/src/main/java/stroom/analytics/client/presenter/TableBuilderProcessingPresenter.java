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

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticTrackerData;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class TableBuilderProcessingPresenter
        extends MyPresenterWidget<TableBuilderProcessingPresenter.TableBuilderProcessingView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE =
            GWT.create(AnalyticProcessResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;

    private DocRef ruleDocRef;

    @Inject
    public TableBuilderProcessingPresenter(final EventBus eventBus,
                                           final TableBuilderProcessingView view,
                                           final DateTimeFormatter dateTimeFormatter,
                                           final RestFactory restFactory,
                                           final NodeManager nodeManager) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
    }

    public void read(final DocRef ruleDocRef,
                     final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
        nodeManager.listAllNodes(
                list -> {
                    if (list != null && list.size() > 0) {
                        getView().setNodes(list);
                    }
                },
                throwable -> AlertEvent
                        .fireError(this,
                                "Error",
                                throwable.getMessage(),
                                null),
                this);

        this.ruleDocRef = ruleDocRef;
        getView().setEnabled(tableBuilderAnalyticProcessConfig.isEnabled());
        getView().setNode(tableBuilderAnalyticProcessConfig.getNode());
        getView().setMinMetaCreateTimeMs(tableBuilderAnalyticProcessConfig.getMinMetaCreateTimeMs());
        getView().setMaxMetaCreateTimeMs(tableBuilderAnalyticProcessConfig.getMaxMetaCreateTimeMs());
        getView().setTimeToWaitForData(tableBuilderAnalyticProcessConfig.getTimeToWaitForData());
        getView().setDataRetention(tableBuilderAnalyticProcessConfig.getDataRetention());

        refreshTracker();
    }

    public TableBuilderAnalyticProcessConfig write() {
        return TableBuilderAnalyticProcessConfig
                .builder()
                .enabled(getView().isEnabled())
                .node(getView().getNode())
                .minMetaCreateTimeMs(getView().getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(getView().getMaxMetaCreateTimeMs())
                .timeToWaitForData(getView().getTimeToWaitForData())
                .dataRetention(getView().getDataRetention())
                .build();
    }

    @Override
    public void onRefreshProcessingStatus() {
        refreshTracker();
    }

    private void refreshTracker() {
        if (ruleDocRef != null && ruleDocRef.getUuid() != null) {
            restFactory
                    .create(ANALYTIC_PROCESS_RESOURCE)
                    .method(res -> res.getTracker(ruleDocRef.getUuid()))
                    .onSuccess(result -> {
                        final SafeHtml safeHtml = getInfo(result);
                        getView().setInfo(safeHtml);
                    })
                    .taskMonitorFactory(this)
                    .exec();
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
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface TableBuilderProcessingView extends View, HasUiHandlers<ProcessingStatusUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        void setNodes(List<String> nodes);

        String getNode();

        void setNode(String node);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        SimpleDuration getDataRetention();

        void setDataRetention(SimpleDuration dataRetention);

        void setInfo(SafeHtml info);
    }
}
