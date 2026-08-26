/*
 * Copyright 2025 Crown Copyright
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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.FindTracesWithHistogramCriteria;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.TimeRange;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DurationUtil;
import stroom.util.client.NumberUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class TracesListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    // A trace is flagged as having "trailing leaked activity" when spans keep arriving well after
    // the root span itself ended — e.g. a pooled/background thread that captured the trace's OTel
    // context emits spans long afterward, inflating the trace's Duration. Flagged when the
    // post-root gap exceeds both an absolute floor and a multiple of the root's own duration (the
    // floor avoids flagging short async tails; the multiple scales with genuinely long traces).
    private static final long LEAK_ABSOLUTE_FLOOR_NANOS = 30L * 1_000_000_000L; // 30s
    private static final long LEAK_MULTIPLIER = 5L;

    private static final int HISTOGRAM_BUCKETS = 80;

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final PagerView pagerView;
    private final TraceHistogramWidget histogramWidget;
    private final MyDataGrid<TraceRoot> dataGrid;
    private final MultiSelectionModelImpl<TraceRoot> selectionModel;
    private RestDataProvider<TraceRoot, ResultPage<TraceRoot>> dataProvider;

    private DocRef dataSourceRef;
    private String filter;
    private Pathway pathway;
    private TimeRange timeRange;

    @Inject
    public TracesListPresenter(final EventBus eventBus,
                               final QuickFilterPageView view,
                               final PagerView pagerView,
                               final RestFactory restFactory,
                               final DateTimeFormatter dateTimeFormatter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.pagerView = pagerView;
        this.histogramWidget = new TraceHistogramWidget(dateTimeFormatter, HISTOGRAM_BUCKETS);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("Traces");
        pagerView.setDataWidget(dataGrid);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
        dataGrid.setRowStyles(errorRowStyles());

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Traces Quick Filter",
                        FindTraceCriteria.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        // Data slot = histogram above the grid, so it sits between the quick-filter bar and the list.
        final FlowPanel dataPanel = new FlowPanel();
        dataPanel.addStyleName("dock-container-vertical");
        dataPanel.addStyleName("max");
        histogramWidget.addStyleName("dock-min");
        dataPanel.add(histogramWidget);
        final Widget pagerWidget = pagerView.asWidget();
        pagerWidget.addStyleName("dock-max");
        pagerWidget.addStyleName("overflow-hidden");
        dataPanel.add(pagerWidget);
        view.setDataView(new SimpleView(dataPanel));
        view.setUiHandlers(this);
    }

    private static final class SimpleView extends ViewImpl {

        private final Widget widget;

        SimpleView(final Widget widget) {
            this.widget = widget;
        }

        @Override
        public Widget asWidget() {
            return widget;
        }
    }

    public void setHistogramZoomHandler(final BiConsumer<Long, Long> zoomHandler) {
        histogramWidget.setZoomHandler(zoomHandler);
    }

    @Override
    public void onFilterChange(final String text) {
        setFilter(text);
        refresh();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {

        }));
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    public MultiSelectionModelImpl<TraceRoot> getSelectionModel() {
        return selectionModel;
    }

    // Red-tints the whole row for a trace with any errored span (see pathways.css .trace-row--error).
    private RowStyles<TraceRoot> errorRowStyles() {
        return (row, rowIndex) -> row != null && row.isError()
                ? "trace-row--error"
                : "";
    }

    private void addColumns() {
        addNameColumn();
        addIdColumn();
        addTraceStartColumn();
        addDurationColumn();
        addTotalActivityColumn();
        addServicesColumn();
        addDepthColumn();
        addTotalSpansColumn();
    }

    private void addNameColumn() {
        final Column<TraceRoot, SafeHtml> column = DataGridUtil
                .htmlColumnBuilder(this::buildOperationCell)
                .withSorting("Operation")
                .build();
        dataGrid.addResizableColumn(column, "Operation", 300);
    }

    /**
     * Renders the operation name, prefixed with a single status icon (and hover tooltip), in
     * precedence order:
     * <ul>
     *   <li>an <b>error</b> icon when any span in the trace reported an error status
     *       ({@link TraceRoot#isError()});</li>
     *   <li>else an <b>error</b> icon + "No root span found" placeholder when the trace has no root
     *       span (orphan-only — its root aged out or never arrived); see {@link TraceRoot#isOrphan()};</li>
     *   <li>else a <b>warning</b> icon when the trace hit the store's per-trace span limit and so is
     *       missing spans ({@link TraceRoot#isTruncated()});</li>
     *   <li>else a <b>warning</b> icon when the trace has trailing leaked activity
     *       ({@link #hasTrailingLeak(TraceRoot)}).</li>
     * </ul>
     */
    private SafeHtml buildOperationCell(final TraceRoot trace) {
        final boolean orphan = trace != null && trace.isOrphan();
        final String rawName = trace == null
                ? ""
                : trace.getName();
        final String name = orphan && (rawName == null || rawName.isEmpty())
                ? "No root span found"
                : rawName;
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.append(SafeHtmlUtil.getSafeHtmlFromTrustedString(
                "<div style=\"display:flex;align-items:center;gap:4px;\">"));
        if (trace != null && trace.isError()) {
            sb.append(SvgImageUtil.toSafeHtml(
                    "Trace contains one or more errored spans", SvgImage.ERROR, "svgIcon"));
        } else if (orphan) {
            sb.append(SvgImageUtil.toSafeHtml(
                    "No root span found for this trace ID (its root has aged out "
                    + "or never arrived)", SvgImage.ERROR, "svgIcon"));
        } else if (trace != null && trace.isTruncated()) {
            sb.append(SvgImageUtil.toSafeHtml(
                    "Truncated — this trace reached the store's per-trace span limit, so some of "
                    + "its spans were not stored", SvgImage.WARNING, "svgIcon"));
        } else if (hasTrailingLeak(trace)) {
            sb.append(SvgImageUtil.toSafeHtml("Trailing spans — activity continued after the root span ended",
                    SvgImage.WARNING, "svgIcon"));
        }
        sb.append(SafeHtmlUtil.getSafeHtml(name));
        sb.append(SafeHtmlUtil.getSafeHtmlFromTrustedString("</div>"));
        return sb.toSafeHtml();
    }

    /**
     * A trace has trailing leaked activity when its last span ends significantly after the root
     * span's own end — the gap must clear both an absolute floor and a multiple of the root's own
     * duration. {@code endTime} is the max end across all spans; {@code rootEndTime} is the root
     * span's own (fixed) end.
     */
    private boolean hasTrailingLeak(final TraceRoot trace) {
        if (trace == null) {
            return false;
        }
        final NanoTime start = trace.getStartTime();
        final NanoTime rootEnd = trace.getRootEndTime();
        final NanoTime end = trace.getEndTime();
        if (start == null || rootEnd == null || end == null) {
            return false;
        }
        // A ZERO/unknown root end would read as "before" the start — treat as not flagged.
        if (rootEnd.isLessThan(start)) {
            return false;
        }
        final long gapNanos = end.diff(rootEnd).getNanos();
        final long rootDurationNanos = rootEnd.diff(start).getNanos();
        return gapNanos > LEAK_ABSOLUTE_FLOOR_NANOS
               && gapNanos > LEAK_MULTIPLIER * rootDurationNanos;
    }

    private void addIdColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> NullSafe.get(trace, TraceRoot::getTraceId);
        addTextColumn("Trace Id", 300, valueExtractor);
    }


    private void addTraceStartColumn() {
        final Function<TraceRoot, NanoTime> valueExtractor = TraceRoot::getStartTime;
        addTimeColumn("Trace Start", 200, valueExtractor);
    }

    private void addDurationColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> {
            // The root span's OWN duration (rootEndTime - startTime), not endTime (the max end
            // across all spans, which trailing leaked activity inflates). Blank when there is no
            // root duration to show — e.g. an orphan-only trace, whose rootEndTime is unset (ZERO)
            // and so reads as before the start time.
            final NanoTime start = trace.getStartTime();
            final NanoTime rootEnd = trace.getRootEndTime();
            if (start == null || rootEnd == null || rootEnd.isLessThan(start)) {
                return "";
            }
            return DurationUtil.formatDuration(rootEnd.diff(start));
        };
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting("Root Duration")
                .build();
        dataGrid.addResizableColumn(column,
                "Root Duration",
                100);
    }

    // Total activity span: start to the last span's end (endTime - startTime), vs addDurationColumn()
    // which shows the root's own duration; the gap is trailing activity after the root finished.
    // Sorted server-side via the trace-roots-total-duration index (label must match TOTAL_DURATION).
    private void addTotalActivityColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> {
            // Blank for an orphan (no root span) — matches the blank Root Duration for these rows.
            if (trace == null || trace.isOrphan()) {
                return "";
            }
            final NanoTime start = trace.getStartTime();
            final NanoTime end = trace.getEndTime();
            if (start == null || end == null || end.isLessThan(start)) {
                return "";
            }
            return DurationUtil.formatDuration(end.diff(start));
        };
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting("Trace Duration")
                .build();
        dataGrid.addResizableColumn(column,
                "Trace Duration",
                100);
    }

    private void addServicesColumn() {
        final Function<TraceRoot, String> valueExtractor = trace ->
                NumberUtil.formatInt(NullSafe.get(trace, TraceRoot::getServices));
        addTextColumn("Services", 100, valueExtractor);
    }

    private void addDepthColumn() {
        final Function<TraceRoot, String> valueExtractor = trace ->
                NumberUtil.formatInt(NullSafe.get(trace, TraceRoot::getDepth));
        addTextColumn("Depth", 100, valueExtractor);
    }

    private void addTotalSpansColumn() {
        final Function<TraceRoot, String> valueExtractor = trace ->
                NumberUtil.formatInt(NullSafe.get(trace, TraceRoot::getTotalSpans));
        addTextColumn("Total Spans", 100, valueExtractor);
    }

    private void addTextColumn(final String name,
                               final int width,
                               final Function<TraceRoot, String> function) {
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(function)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
//        dataGrid.sort(column);
    }

    private void addTimeColumn(final String name,
                               final int width,
                               final Function<TraceRoot, NanoTime> function) {
        final Function<TraceRoot, String> valueExtractor = pathway -> {
            final NanoTime nanoTime = function.apply(pathway);
            return nanoTime == null
                    ? ""
                    : dateTimeFormatter.format(nanoTime.toEpochMillis());
        };
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
//        dataGrid.sort(column);
    }


    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<TraceRoot, ResultPage<TraceRoot>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<TraceRoot>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (dataSourceRef == null) {
                        histogramWidget.setData(null);
                        dataConsumer.accept(ResultPage.empty());
                    } else {
                        // The histogram rides back on the page so the bars and the rows are counted
                        // from one read of the store rather than from two requests that can see
                        // different data.
                        final FindTracesWithHistogramCriteria criteria =
                                new FindTracesWithHistogramCriteria(new FindTraceCriteria(
                                        CriteriaUtil.createPageRequest(range),
                                        CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
                                        dataSourceRef,
                                        filter,
                                        pathway,
                                        SimpleDuration.ZERO,
                                        timeRange),
                                        HISTOGRAM_BUCKETS);

                        restFactory
                                .create(TRACES_RESOURCE)
                                .method(res -> res.findTracesWithHistogram(criteria))
                                .onSuccess(page -> {
                                    histogramWidget.setData(page.getHistogram());
                                    dataConsumer.accept(page);
                                })
                                .onFailure(error -> {
                                    histogramWidget.setData(null);
                                    errorHandler.onError(error);
                                })
                                .taskMonitorFactory(pagerView)
                                .exec();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }

    public void setPathway(final Pathway pathway) {
        this.pathway = pathway;
    }

    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
    }
}
