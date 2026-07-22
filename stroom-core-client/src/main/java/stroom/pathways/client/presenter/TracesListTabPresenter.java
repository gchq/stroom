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

package stroom.pathways.client.presenter;

import stroom.data.grid.client.DefaultResources;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.pathways.client.presenter.TracesListTabPresenter.TracesView;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.util.shared.time.SimpleDuration;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class TracesListTabPresenter extends DocPresenter<TracesView, TracesDoc> {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    // Traces with at least this many spans are shown via the bounded, paged detail path rather than
    // loaded whole; below it the whole trace is fetched and rendered as before.
    private static final int LARGE_TRACE_THRESHOLD = 10_000;

    private final TracesListPresenter listPresenter;
    private final TraceOverviewWidget traceOverviewWidget;
    private final RestFactory restFactory;
    private DocRef dataSourceRef;

    @Inject
    public TracesListTabPresenter(final EventBus eventBus,
                                  final TracesView view,
                                  final TracesListPresenter listPresenter,
                                  final DefaultResources resources,
                                  final RestFactory restFactory) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.restFactory = restFactory;
        traceOverviewWidget = new TraceOverviewWidget(resources);

        view.setTopWidget(listPresenter.getView());
        view.setBottomWidget(traceOverviewWidget);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().addTimeRangeValueChangeHandler(e -> {
            listPresenter.setTimeRange(e.getValue());
            listPresenter.refresh();
        }));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(e -> {
            final TraceRoot traceRoot = listPresenter.getSelectionModel().getSelected();
            if (traceRoot == null) {
                return;
            }
            if (isLargeTrace(traceRoot)) {
                loadLargeTrace(traceRoot);
            } else {
                loadFullTrace(traceRoot);
            }
        }));
    }

    // Any trace with enough spans is served via the bounded, virtualized path, regardless of orphan
    // status — a huge rootless/orphan or archived trace must NOT take the whole-trace loadFullTrace
    // path, which materialises every span and OOMs. Extents (start/end) are required for the timeline
    // axis; a huge trace lacking them falls back to loadFullTrace.
    private boolean isLargeTrace(final TraceRoot traceRoot) {
        return traceRoot.getTotalSpans() >= LARGE_TRACE_THRESHOLD
               && traceRoot.getStartTime() != null
               && traceRoot.getEndTime() != null;
    }

    private void loadFullTrace(final TraceRoot traceRoot) {
        // Pass the root start time so the server can locate the archive bucket
        // (labelled by start time) if this trace has been purged from the live shard.
        final Long startTimeMs = traceRoot.getStartTime() != null
                ? traceRoot.getStartTime().toEpochMillis()
                : null;
        final GetTraceRequest request = new GetTraceRequest(
                dataSourceRef,
                traceRoot.getTraceId(),
                SimpleDuration.ZERO,
                startTimeMs);
        restFactory
                .create(TRACES_RESOURCE)
                .method(res -> res.findTrace(request))
                .onSuccess(traceOverviewWidget::setTrace)
                // Belt-and-braces: orphan-only traces are now served by the success path, so a
                // failure here is genuinely unexpected — clear the detail rather than leaving a
                // stale trace displayed.
                .onFailure(error -> traceOverviewWidget.setTrace(null))
                // Spin the span-detail widget's own refresh button, not the traces list's.
                .taskMonitorFactory(traceOverviewWidget)
                .exec();
    }

    // Hands the widget a fetcher that pages tree-order spans on demand as the user navigates the pager.
    // The timeline axis is derived per page from the returned spans, so no whole-trace overview is
    // fetched.
    private void loadLargeTrace(final TraceRoot traceRoot) {
        final String traceId = traceRoot.getTraceId();
        // Root start time locates the archive bucket for a split trace (root/bulk archived, trailing
        // spans live) so the server can page the merged live+archive tree.
        final Long startTimeMs = traceRoot.getStartTime() != null
                ? traceRoot.getStartTime().toEpochMillis()
                : null;
        final TraceOverviewWidget.SpanWindowFetcher fetcher = (offset, cursor, limit, onLoaded) -> {
            final GetSpansRequest spansRequest = new GetSpansRequest(
                    dataSourceRef, traceId, offset, limit, startTimeMs, cursor);
            restFactory
                    .create(TRACES_RESOURCE)
                    .method(res -> res.getSpans(spansRequest))
                    .onSuccess(onLoaded)
                    .onFailure(error -> onLoaded.accept(null))
                    // Spin the span-list pager's own refresh button, not the traces list's.
                    .taskMonitorFactory(traceOverviewWidget)
                    .exec();
        };
        traceOverviewWidget.setLargeTrace(traceRoot, fetcher);
    }

    @Override
    protected void onRead(final DocRef docRef, final TracesDoc document, final boolean readOnly) {
        if (docRef != null) {
            setDataSourceRef(docRef);
            // Default the time range selector to Today on first open.
            getView().setTimeRange(TimeRanges.TODAY);
            listPresenter.setTimeRange(TimeRanges.TODAY);
            refresh();
        }
    }

    @Override
    protected TracesDoc onWrite(final TracesDoc document) {
        return document;
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
        listPresenter.setDataSourceRef(dataSourceRef);
    }

    public void setFilter(final String filter) {
        listPresenter.setFilter(filter);
    }

    public void setPathway(final Pathway pathway) {
        listPresenter.setPathway(pathway);
    }

    public void refresh() {
        listPresenter.refresh();
    }

    public interface TracesView extends View {

        void setLabel(String label);

        void setTopWidget(View view);

        void setBottomWidget(Widget view);

        void setTimeRange(TimeRange timeRange);

        TimeRange getTimeRange();

        HandlerRegistration addTimeRangeValueChangeHandler(ValueChangeHandler<TimeRange> handler);
    }
}
