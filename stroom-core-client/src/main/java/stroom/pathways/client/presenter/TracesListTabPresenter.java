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
import stroom.entity.client.presenter.HasToolbar;
import stroom.pathways.client.presenter.TraceNavigation.TraceToShow;
import stroom.pathways.client.presenter.TracesListTabPresenter.TracesView;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.query.client.view.TimeRangeSelector;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.customdatebox.client.ClientDateUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class TracesListTabPresenter extends DocPresenter<TracesView, TracesDoc> implements HasToolbar {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    // Traces with at least this many spans are shown via the bounded, paged detail path rather than
    // loaded whole; below it the whole trace is fetched and rendered as before.
    private static final int LARGE_TRACE_THRESHOLD = 10_000;

    private final TracesListPresenter listPresenter;
    private final TraceOverviewWidget traceOverviewWidget;
    private final RestFactory restFactory;
    private final TraceNavigation traceNavigation;
    // The time-range selector lives on the doc tab's save-toolbar row (contributed via HasToolbar),
    // right-aligned by the .traces-toolbar wrapper, rather than in a dedicated band above the grid.
    private final TimeRangeSelector timeRangeSelector = new TimeRangeSelector();
    private final FlowPanel toolbar = new FlowPanel();
    private DocRef dataSourceRef;

    @Inject
    public TracesListTabPresenter(final EventBus eventBus,
                                  final TracesView view,
                                  final TracesListPresenter listPresenter,
                                  final DefaultResources resources,
                                  final RestFactory restFactory,
                                  final TraceNavigation traceNavigation) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.restFactory = restFactory;
        this.traceNavigation = traceNavigation;
        traceOverviewWidget = new TraceOverviewWidget(this, resources);

        toolbar.addStyleName("traces-toolbar");
        toolbar.add(timeRangeSelector);

        view.setTopWidget(listPresenter.getView());
        view.setBottomWidget(traceOverviewWidget);
    }

    @Override
    public List<Widget> getToolbars() {
        return List.of(toolbar);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(timeRangeSelector.addValueChangeHandler(e -> {
            listPresenter.setTimeRange(e.getValue());
            listPresenter.refresh();
        }));
        listPresenter.setHistogramZoomHandler((fromMs, toMs) ->
                timeRangeSelector.setValue(new TimeRange(
                        null, ClientDateUtil.toISOString(fromMs), ClientDateUtil.toISOString(toMs)), true));
        registerHandler(listPresenter.getSelectionModel().addSelectionHandler(e -> {
            final TraceRoot traceRoot = listPresenter.getSelectionModel().getSelected();
            if (traceRoot == null) {
                // Nothing selected, so there is nothing to show below the list.
                traceOverviewWidget.setTrace(null);
                return;
            }
            // Every span list is paged via the bounded getSpans path, regardless of size. The one
            // exception is a SMALL rootless trace: the paged path can't yet traverse a rootless fragment
            // (separately parked), so we load it whole — safe at that size, and it fits in one page anyway.
            if (isSmallRootless(traceRoot)) {
                loadFullTrace(traceRoot);
            } else {
                loadLargeTrace(traceRoot);
            }
        }));
    }

    // A small, rootless trace: served whole because the paged (getSpans) tree walk needs a root and can't
    // traverse a rootless fragment yet. Large rootless traces still take the paged path (loading them whole
    // would OOM) — they show empty until the parked rootless traversal is implemented.
    private boolean isSmallRootless(final TraceRoot traceRoot) {
        return traceRoot.isOrphan() && traceRoot.getTotalSpans() < LARGE_TRACE_THRESHOLD;
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
        final TraceOverviewWidget.SpanWindowFetcher fetcher =
                (offset, cursor, limit, groupSelection, onLoaded) -> {
                    final GetSpansRequest spansRequest = new GetSpansRequest(
                            dataSourceRef, traceId, offset, limit, startTimeMs, cursor, groupSelection);
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
            // This store may have been opened to show one trace so the time range and filter are
            // set before the first fetch — otherwise today's traces are fetched and drawn and then replaced.
            final TraceToShow trace = traceNavigation.getAndClearTraceToShow(docRef);
            if (trace != null) {
                showTrace(trace.traceId(), trace.fromMs(), trace.toMs());
            } else {
                // Default the time range selector to Today on first open.
                timeRangeSelector.setValue(TimeRanges.TODAY);
                listPresenter.setTimeRange(TimeRanges.TODAY);
                refresh();
            }
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

    /**
     * Shows one trace: the window narrowed to when it ran and the quick filter set to its id.
     *
     * <p>The selector is set as well as the list, or the window on show would not be the window being
     * searched and the next thing the reader did with it would widen rather than narrow.
     */
    public void showTrace(final String traceId, final long fromMs, final long toMs) {
        // Given no name, so the selector shows the times themselves. A name is what it displays when
        // it has one, and a window around one trace has nothing to call itself.
        //
        // Set on the selector rather than straight onto the list, and told to say so: the handler
        // above is what carries a window from the selector to the list, so going round it would leave
        // the window on show and the window being searched as two different things.
        final TimeRange window = new TimeRange(
                null, ClientDateUtil.toISOString(fromMs), ClientDateUtil.toISOString(toMs));
        // Set without telling anyone, and the list told directly. Announcing it would have the handler
        // above fetch a page for the window alone, which is then thrown away a moment later when the
        // filter arrives — a page drawn only to be replaced, which is what a reader sees as a flicker.
        timeRangeSelector.setValue(window);
        listPresenter.setTimeRange(window);
        listPresenter.showFilter(traceId);
        listPresenter.refresh();
    }

    public void setPathway(final Pathway pathway, final List<String> ignoredSpanNames) {
        listPresenter.setPathway(pathway, ignoredSpanNames);
    }

    public void refresh() {
        listPresenter.refresh();
    }

    public interface TracesView extends View {

        void setTopWidget(View view);

        void setBottomWidget(Widget view);
    }
}
