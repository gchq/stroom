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

package stroom.pathways.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.HasToolbar;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.query.client.view.TimeRangeSelector;
import stroom.query.client.view.TimeRangeSelectorController;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Controller;
import stroom.widget.customdatebox.client.ClientDateUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// A Traces store's Traces tab ([TracesListTab]): its traces in a time range (on the document's
/// toolbar row), and the selected trace's spans, paged from the server.
public class TracesListTabController extends Controller implements HasToolbar {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    // Traces with at least this many spans are shown via the bounded, paged detail path rather than
    // loaded whole; below it the whole trace is fetched and rendered as before.
    private static final int LARGE_TRACE_THRESHOLD = 10_000;

    private final TracesListController tracesList;
    private final TraceOverviewWidget traceOverviewWidget;
    private final RestFactory restFactory;
    // The time-range selector lives on the doc tab's save-toolbar row (contributed via HasToolbar),
    // right-aligned by the .traces-toolbar wrapper, rather than in a dedicated band above the grid.
    private final TimeRangeSelector timeRangeSelector = new TimeRangeSelector();
    private final FlowPanel toolbar = new FlowPanel();
    private DocRef dataSourceRef;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        The content.
    /// @param tracesList                  The traces.
    /// @param restFactory                 Fetches the selected trace's spans.
    /// @param timeRangeSelectorController Lets the user choose the time range.
    @Inject
    public TracesListTabController(final EventBus eventBus,
                                   final TracesListTab view,
                                   final TracesListController tracesList,
                                   final RestFactory restFactory,
                                   final TimeRangeSelectorController timeRangeSelectorController) {
        super(eventBus);
        initWidget(view);
        own(tracesList);
        own(timeRangeSelectorController).attach(timeRangeSelector);
        this.tracesList = tracesList;
        this.restFactory = restFactory;
        traceOverviewWidget = view.getTraceOverview();

        toolbar.addStyleName("traces-toolbar");
        toolbar.add(timeRangeSelector);

        view.setTracesList(tracesList);

        own(timeRangeSelector.addValueChangeHandler(e -> {
            tracesList.setTimeRange(e.getValue());
            tracesList.refresh();
        }));
        tracesList.setHistogramZoomHandler((fromMs, toMs) ->
                timeRangeSelector.setValue(new TimeRange(
                        null, ClientDateUtil.toISOString(fromMs), ClientDateUtil.toISOString(toMs)), true));
        own(tracesList.getSelectionModel().addSelectionHandler(e -> {
            final TraceRoot traceRoot = tracesList.getSelectionModel().getSelected();
            if (traceRoot == null) {
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

    @Override
    public List<Widget> getToolbars() {
        return List.of(toolbar);
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
                .owner(this)
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
                            .owner(this)
                            .taskMonitorFactory(traceOverviewWidget)
                            .exec();
                };
        traceOverviewWidget.setLargeTrace(traceRoot, fetcher);
    }

    /// Shows a Traces store's traces, today's at first.
    ///
    /// @param docRef   The store.
    /// @param document The store.
    public void read(final DocRef docRef, final TracesDoc document) {
        if (docRef != null) {
            setDataSourceRef(docRef);
            // Default the time range selector to Today on first open.
            timeRangeSelector.setValue(TimeRanges.TODAY);
            tracesList.setTimeRange(TimeRanges.TODAY);
            refresh();
        }
    }

    /// @param dataSourceRef The store whose traces are shown.
    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
        tracesList.setDataSourceRef(dataSourceRef);
    }

    /// @param filter The filter of the traces shown.
    public void setFilter(final String filter) {
        tracesList.setFilter(filter);
    }

    /// @param pathway The pathway whose traces are shown, or `null` for all.
    public void setPathway(final Pathway pathway) {
        tracesList.setPathway(pathway);
    }

    /// Fetches the traces again.
    public void refresh() {
        tracesList.refresh();
    }
}
