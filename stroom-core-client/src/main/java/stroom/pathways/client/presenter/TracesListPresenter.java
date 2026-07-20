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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.TimeRange;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DurationUtil;
import stroom.util.client.NumberUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class TracesListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    // A trace is flagged as having "trailing leaked activity" when spans keep arriving well after
    // the root span itself ended — e.g. a pooled/background thread that captured the trace's OTel
    // context emits spans long afterward, inflating the trace's Duration. Flagged when the
    // post-root gap exceeds both an absolute floor and a multiple of the root's own duration (the
    // floor avoids flagging short async tails; the multiple scales with genuinely long traces).
    private static final long LEAK_ABSOLUTE_FLOOR_NANOS = 30L * 1_000_000_000L; // 30s
    private static final long LEAK_MULTIPLIER = 5L;

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final MyDataGrid<TraceRoot> dataGrid;
    private final MultiSelectionModelImpl<TraceRoot> selectionModel;
    private RestDataProvider<TraceRoot, ResultPage<TraceRoot>> dataProvider;

    private DocRef dataSourceRef;
    private String filter;
    private Pathway pathway;
    private TimeRange timeRange;

    @Inject
    public TracesListPresenter(final EventBus eventBus,
                               final PagerView view,
                               final RestFactory restFactory,
                               final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
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

    private void addColumns() {
        addNameColumn();
        addIdColumn();
        addTraceStartColumn();
        addDurationColumn();
        addTotalActivityColumn();
        addServicesColumn();
        addDepthColumn();
        addTotalSpansColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        final Column<TraceRoot, SafeHtml> column = DataGridUtil
                .htmlColumnBuilder(this::buildOperationCell)
                .withSorting("Operation")
                .build();
        dataGrid.addResizableColumn(column, "Operation", 300);
    }

    /**
     * Renders the operation name, prefixed with a warning icon (and hover tooltip):
     * <ul>
     *   <li>an <b>error</b> icon + "No root span found" placeholder when the trace has no root span
     *       (orphan-only — its root aged out or never arrived); see {@link TraceRoot#isOrphan()};</li>
     *   <li>otherwise a <b>warning</b> icon when the trace has trailing leaked activity
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
        if (orphan) {
            sb.append(SvgImageUtil.toSafeHtml(
                    "No root span found for this trace ID (its root has aged out "
                    + "or never arrived)", SvgImage.ERROR, "svgIcon"));
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
                        dataConsumer.accept(ResultPage.empty());
                    } else {
                        final FindTraceCriteria criteria = new FindTraceCriteria(
                                CriteriaUtil.createPageRequest(range),
                                CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
                                dataSourceRef,
                                filter,
                                pathway,
                                SimpleDuration.ZERO,
                                timeRange);

                        restFactory
                                .create(TRACES_RESOURCE)
                                .method(res -> res.findTraces(criteria))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .taskMonitorFactory(getView())
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
