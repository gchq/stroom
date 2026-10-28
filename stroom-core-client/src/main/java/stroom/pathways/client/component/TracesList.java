/*
 * Copyright 2026 Crown Copyright
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

import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DurationUtil;
import stroom.util.client.NumberUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Traces: a quick filter above a histogram of them over time and a page of them, a row each (its
/// operation, with an icon for an error, a missing root, truncation or trailing activity, its id,
/// start, root and trace durations, services, depth and spans), red where it has an errored span;
/// several can be selected.
public class TracesList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // A trace is flagged as having "trailing leaked activity" when spans keep arriving well after
    // the root span itself ended — e.g. a pooled/background thread that captured the trace's OTel
    // context emits spans long afterward, inflating the trace's Duration. Flagged when the
    // post-root gap exceeds both an absolute floor and a multiple of the root's own duration (the
    // floor avoids flagging short async tails; the multiple scales with genuinely long traces).
    private static final long LEAK_ABSOLUTE_FLOOR_NANOS = 30L * 1_000_000_000L; // 30s
    private static final long LEAK_MULTIPLIER = 5L;

    @UiField
    QuickFilterPage page;
    @UiField
    TraceHistogramChart histogram;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<TraceRoot> dataGrid;

    private final MultiSelectionModelImpl<TraceRoot> selectionModel;
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public TracesList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(TraceRoot::getTraceId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.setRowStyles(errorRowStyles());
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<TraceRoot> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<TraceRoot> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param data The histogram of the traces found over time, or `null` for none.
    public void setHistogramData(final TraceHistogram data) {
        histogram.setData(data);
    }

    /// @param dateTimeFormatter Formats the histogram's times.
    public void setHistogramDateTimeFormatter(final DateTimeFormatter dateTimeFormatter) {
        histogram.setDateTimeFormatter(dateTimeFormatter);
    }

    /// @param onZoom Given the start and end, in milliseconds since the epoch, of a part of the
    ///               histogram the user zooms into.
    public void setOnHistogramZoom(final BiConsumer<Long, Long> onZoom) {
        histogram.setOnZoom(onZoom);
    }

    /// @param filterError Why the server rejected the filter, or `null` to clear it.
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        page.registerPopupTextProvider(popupTextSupplier);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used, or
    ///                       `null` for none.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    // Red-tints the whole row for a trace with any errored span (see pathways.css .trace-row--error).
    private RowStyles<TraceRoot> errorRowStyles() {
        return (row, rowIndex) -> row != null && row.isError()
                ? "trace-row--error"
                : "";
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        addNameColumn();
        addIdColumn();
        addTraceStartColumn(dateTimeFormat);
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

    // Renders the operation name, prefixed with a single status icon (and hover tooltip), in
    // precedence order:
    // - an error icon when any span in the trace reported an error status (TraceRoot.isError());
    // - else an error icon + "No root span found" placeholder when the trace has no root span
    //   (orphan-only: its root aged out or never arrived); see TraceRoot.isOrphan();
    // - else a warning icon when the trace hit the store's per-trace span limit and so is missing
    //   spans (TraceRoot.isTruncated());
    // - else a warning icon when the trace has trailing leaked activity (hasTrailingLeak).
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
                "<div class=\"traces-list__operation\">"));
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

    // A trace has trailing leaked activity when its last span ends significantly after the root
    // span's own end: the gap must clear both an absolute floor and a multiple of the root's own
    // duration. endTime is the max end across all spans; rootEndTime is the root span's own (fixed)
    // end.
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

    private void addTraceStartColumn(final Function<Long, String> dateTimeFormat) {
        final Function<TraceRoot, NanoTime> valueExtractor = TraceRoot::getStartTime;
        addTimeColumn("Trace Start", 200, valueExtractor, dateTimeFormat);
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
            return DurationUtil.formatDuration(rootEnd.diff(start).getNanos());
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
            return DurationUtil.formatDuration(end.diff(start).getNanos());
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
                               final Function<TraceRoot, NanoTime> function,
                               final Function<Long, String> dateTimeFormat) {
        final Function<TraceRoot, String> valueExtractor = pathway -> {
            final NanoTime nanoTime = function.apply(pathway);
            return nanoTime == null
                    ? ""
                    : dateTimeFormat.apply(nanoTime.toEpochMillis());
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TracesList> {

    }
}
