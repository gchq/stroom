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

package stroom.pathways.client.component;

import stroom.dashboard.client.table.GroupLevelButtons;
import stroom.data.client.CopyTextUtil;
import stroom.data.pager.client.Pager;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.pathways.shared.otel.trace.StatusCode;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.query.api.GroupSelection;
import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.StringUtil;
import stroom.widget.button.client.Toolbar;
import stroom.widget.datagrid.client.Glass;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;
import com.google.gwt.view.client.RowCountChangeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// A trace's overview: a timeline of its spans, with a window on it that can be dragged and resized,
/// above a waterfall of the spans in the window, as a tree that opens and closes, and a panel of a
/// chosen span's details. A trace too large to show whole is shown a page of spans at a time, with a
/// pager. Its frame is in `TraceOverviewChart.ui.xml`; the trace itself is drawn as HTML, again on
/// every change, styled by `trace-overview-chart.css` (with custom properties for the positions and sizes).
public class TraceOverviewChart extends Composite implements TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html panel;
    private Trace trace;
    /** Lookup of spanId -&gt; Span for resolving clicks on span rows. */
    private final Map<String, Span> spanById = new HashMap<>();
    /** The span whose attributes are shown in the Span Info panel, or {@code null}. */
    private Span selectedSpan;


    private Extents extents;
    private Glass glass;
    private boolean resizingStart;
    private boolean resizingEnd;
    private int offsetX;
    private int startX;
    private NanoDuration windowStart = NanoDuration.ZERO;
    private NanoDuration windowEnd = NanoDuration.ZERO;
    private boolean resizingPanel;
    private int spanInfoWidth = 340;

    // ---- Large-trace (paged) mode ----------------------------------------------------------------
    // A trace too large to load whole is shown from a TraceRoot (extents + total count) with the detail
    // waterfall PAGED: one page of tree-order rows (SpanWindowFetcher) is fetched and rendered at a time,
    // navigated with first/prev/next/last controls (like the traces list). The timeline reflects only the
    // current page's spans. Every request carries the target offset AND the resume cursor if we know one:
    // the server pages by cursor when present (cheap sequential next/prev) and by offset otherwise (live
    // checkpoints, or a merged checkpoint index for split traces), so first/prev/next/last/jump all work.
    private static final int SPANS_PER_PAGE = 1000;
    // The overview strip: each span is one row. When the whole result set fits at the fixed row stride
    // they're stacked at that stride (a single span is one thin bar); when it doesn't, the rows are
    // vertically compressed to fill the strip so none are clipped.
    private static final int SPAN_INFO_MIN_WIDTH = 240;
    private static final int WATERFALL_MIN_WIDTH = 240;
    // The strip's height, as __timeline-bar (trace-overview-chart.css) has it
    private static final int OVERVIEW_STRIP_PX = 40;
    private static final int OVERVIEW_ROW_PX = 3;   // fixed row stride when the set fits
    private static final int OVERVIEW_BAR_PX = 2;   // bar height at the fixed stride

    private boolean largeMode;
    private TraceRoot largeRoot;
    private final List<TraceSpanRow> pageRows = new ArrayList<>();
    private int pageIndex;
    private int totalSpans;
    private boolean hasMore;        // a further page exists after the current one (sequential correctness)
    // pageCursors.get(i) is the resume cursor that fetches page i, when known (index 0 == null == root).
    // Sparse: a jump populates only the landed page's successor, so absent entries fall back to offset.
    private final Map<Integer, String> pageCursors = new HashMap<>();
    private SpanWindowFetcher fetcher;
    private boolean fetching;

    // The traces-list pager widget, driven by a lightweight HasRows adapter (the detail waterfall isn't a
    // cell widget, so we bridge the pager's offset/range model to our page fetches). suppressRangeEvent
    // guards against the range changes we push back into the adapter re-triggering a fetch.
    @UiField
    Pager pager;
    private final TracePagerRows pagerRows = new TracePagerRows();
    @UiField
    FlowPanel pagerBar;
    @UiField
    Toolbar expandControls;
    private boolean suppressRangeEvent;
    // In-flight span-fetch count driving this widget's own pager refresh spinner (see createTaskMonitor).
    private int taskCount;

    // ---- Expand/collapse -------------------------------------------------------------------------
    // Which spans are expanded/collapsed, keyed by spanId (the "group key"), mirroring dashboard table
    // grouping. null == fully expanded (the whole tree); a non-null selection prunes. In large mode the
    // server does the pruning (the request carries this); in whole-trace mode we prune client-side while
    // rendering. maxDepth caps the expand-level buttons.
    private GroupSelection groupSelection;
    private int maxDepth;
    private final GroupLevelButtons groupLevelButtons;

    /**
     * Fetches a bounded, tree-order window of spans. For random/offset paging pass {@code cursor == null}
     * and the window {@code [offset, offset + limit)}; for sequential paging pass the opaque resume
     * {@code cursor} (offset ignored). {@code groupSelection} carries the expand/collapse state (null =
     * fully expanded).
     */
    public interface SpanWindowFetcher {

        void fetch(int offset,
                   String cursor,
                   int limit,
                   GroupSelection groupSelection,
                   Consumer<TraceSpanPage> onLoaded);
    }

    /// Makes an overview showing no trace.
    public TraceOverviewChart() {
        initWidget(BINDER.createAndBindUi(this));
        pager.setDisplay(pagerRows);
        // Expand/collapse-level buttons on the LEFT, the pager on the RIGHT (CSS space-between).
        groupLevelButtons = new GroupLevelButtons(expandControls, this::effectiveSelection, () -> maxDepth,
                selection -> {
                    groupSelection = selection;
                    onSelectionChanged();
                });

        // A pager button changes the adapter's range; page it (unless it's our own sync write-back).
        pagerRows.addRangeChangeHandler(event -> {
            if (!suppressRangeEvent) {
                onPagerRange(event.getNewRange());
            }
        });

        glass = new Glass("trace-overview-chart__glass");

        panel.addMouseDownHandler(e -> {

            final Element element = e.getNativeEvent().getEventTarget().cast();
            if (ElementUtil.findParent(element, "doc-ref-link", 5) != null) {
                CopyTextUtil.onClick(e.getNativeEvent());
                // Only the copy icon (or a right-click menu) consumes the event; a plain click on the value
                // text still falls through, so clicking an operation name opens the Span Info panel.
                if (ElementUtil.findParent(element, CopyTextUtil.COPY_CLASS_NAME, 5) != null
                        || !MouseUtil.isPrimary(e.getNativeEvent())) {
                    return;
                }
            }
            if ("startSlider".equals(element.getId())) {
                startX = e.getClientX();
                offsetX = startX - element.getAbsoluteLeft() - 4;
                capture();
                resizingStart = true;

            } else if ("endSlider".equals(element.getId())) {
                startX = e.getClientX();
                offsetX = startX - element.getAbsoluteLeft() - 4;
                capture();
                resizingEnd = true;

            } else if ("timeRangeOverlay".equals(element.getId())) {
                startX = e.getClientX();
                offsetX = startX - element.getAbsoluteLeft();
                capture();
                resizingStart = true;
                resizingEnd = true;

            } else if ("resetRangeBtn".equals(element.getId())) {
                windowStart = NanoDuration.ZERO;
                windowEnd = extents.totalDuration;
                refresh();

            } else if ("closeSpanInfo".equals(element.getId())) {
                selectedSpan = null;
                refresh();

            } else if ("spanInfoResize".equals(element.getId())) {
                startX = e.getClientX();
                capture();
                resizingPanel = true;

            } else {
                // An expander click toggles that span's open/closed state (checked before the generic
                // span-row click so it doesn't also open the Span Info panel).
                final Element expander = ElementUtil.findParent(
                        element, el -> el.hasAttribute("data-expander-span-id"), 15);
                if (expander != null) {
                    toggleSpan(expander.getAttribute("data-expander-span-id"),
                            parseDepth(expander.getAttribute("data-expander-depth")));
                } else {
                    // Clicked a span row/bar — open the Span Info panel for that span.
                    final Element row = ElementUtil.findParent(
                            element, el -> el.hasAttribute("data-span-id"), 15);
                    if (row != null) {
                        final Span span = spanById.get(row.getAttribute("data-span-id"));
                        if (span != null) {
                            selectedSpan = span;
                            refresh();
                        }
                    }
                }
            }

        });
        panel.addMouseUpHandler(e -> {
            releaseCapture();
            resizingStart = false;
            resizingEnd = false;
            resizingPanel = false;
        });
        panel.addMouseMoveHandler(e -> {
            if (resizingPanel) {
                // Drag the panel's left edge: moving left widens it, right narrows it.
                final int delta = startX - e.getClientX();
                startX = e.getClientX();
                final int max = Math.max(SPAN_INFO_MIN_WIDTH,
                        panel.getElement().getClientWidth() - WATERFALL_MIN_WIDTH);
                spanInfoWidth = Math.max(SPAN_INFO_MIN_WIDTH, Math.min(max, spanInfoWidth + delta));
                refresh();
                return;
            }
            if (resizingStart && resizingEnd) {
                final NanoDuration windowSize = windowEnd.subtract(windowStart);
                final NanoDuration start = calcWindow(e);
                if (start != null) {
                    if (start.add(windowSize).isGreaterThan(extents.totalDuration)) {
                        windowStart = extents.totalDuration.subtract(windowSize);
                        windowEnd = extents.totalDuration;
                    } else {
                        windowStart = start;
                        windowEnd = start.add(windowSize);
                    }
                    refresh();
                }

            } else if (resizingStart) {
                final NanoDuration start = calcWindow(e);
                if (start != null) {
                    if (start.isGreaterThan(windowEnd)) {
                        windowStart = windowEnd;
                    } else {
                        windowStart = start;
                    }
                    refresh();
                }

            } else if (resizingEnd) {
                final NanoDuration end = calcWindow(e);
                if (end != null) {
                    if (end.isLessThan(windowStart)) {
                        windowEnd = windowStart;
                    } else {
                        windowEnd = end;
                    }
                    refresh();
                }
            }
        });
    }

    private NanoDuration calcWindow(final MouseMoveEvent e) {
        final Element element = ElementUtil.findChild(panel.getElement(), "trace-overview-chart__timeline-controls");
        if (element == null) {
            return null;
        }

        final int x = e.getClientX() - offsetX;
        final Rect rect = ElementUtil.getBoundingClientRect(element);
        final double width = rect.getWidth();
        final double left = rect.getLeft();
        final double right = rect.getRight();
        final double diff = x - left;
        final double increments = extents.totalDuration.getNanos() / width;
        if (x < left) {
            return NanoDuration.ZERO;
        } else if (x > right) {
            return extents.totalDuration;
        } else {
            return NanoDuration.ofNanos((long) (increments * diff));
        }
    }

    private void capture() {
        glass.show();
        Event.setCapture(panel.getElement());
    }

    private void releaseCapture() {
        glass.hide();
        Event.releaseCapture(panel.getElement());
    }

    public void setTrace(final Trace trace) {
        this.largeMode = false;
        this.largeRoot = null;
        this.fetcher = null;
        this.pageRows.clear();
        this.trace = trace;
        this.selectedSpan = null;
        spanById.clear();
        if (trace != null) {
            trace.getParentSpanIdMap().values()
                    .forEach(spans -> spans.forEach(span -> spanById.put(span.getSpanId(), span)));
        }
        this.extents = computeExtents();
        windowStart = NanoDuration.ZERO;
        windowEnd = extents.totalDuration;
        // Fully expanded by default (null selection); the expand/collapse controls prune from here.
        this.groupSelection = null;
        this.maxDepth = computeMaxDepth();
        // Normal (whole) trace: a single page covering every span, so the pager is shown but its nav
        // buttons are all disabled (start == 0, and the visible range already spans the whole count).
        final int spanCount = spanById.size();
        suppressRangeEvent = true;
        pagerRows.setVisibleRange(0, Math.max(1, spanCount));
        pagerRows.setRowCount(spanCount, true);
        suppressRangeEvent = false;
        refresh();
    }

    // Enters paged large-trace mode: extents/total start from the TraceRoot and are then recomputed per
    // page as spans arrive; the detail waterfall shows one page at a time via the fetcher, navigated by
    // the pager. Requires a real root (extents present); orphan/rootless traces use the whole-trace
    // setTrace path instead.
    public void setLargeTrace(final TraceRoot root,
                              final SpanWindowFetcher fetcher) {
        this.largeMode = true;
        this.trace = null;
        this.largeRoot = root;
        this.fetcher = fetcher;
        this.totalSpans = Math.max(0, root.getTotalSpans());
        this.selectedSpan = null;
        spanById.clear();
        // Fully expanded by default (null selection → server returns the whole tree). The deepest 0-based
        // depth is root.getDepth() - 1; that caps the expand-level buttons.
        this.groupSelection = null;
        this.maxDepth = Math.max(0, root.getDepth() - 1);
        this.extents = computeExtentsFromRoot(root);
        windowStart = NanoDuration.ZERO;
        windowEnd = extents.totalDuration;
        pageRows.clear();
        pageCursors.clear();
        pageIndex = 0;
        hasMore = false;
        fetching = false;
        // Reset the pager to a loading state (unknown total) until the first page arrives and syncs it.
        suppressRangeEvent = true;
        pagerRows.setVisibleRange(0, SPANS_PER_PAGE);
        pagerRows.setRowCount(0, false);
        suppressRangeEvent = false;
        refresh();
        goToPage(0);
    }

    private Extents computeExtentsFromRoot(final TraceRoot root) {
        final NanoTime min = root.getStartTime();
        final NanoTime max = root.getEndTime();
        if (min == null || max == null) {
            return new Extents(NanoTime.ZERO, NanoTime.ZERO, NanoDuration.ZERO, 100);
        }
        final NanoDuration totalDuration = min.diff(max);
        final double increments = totalDuration.getNanos() == 0
                ? 100D
                : 100D / totalDuration.getNanos();
        return new Extents(min, max, totalDuration, increments);
    }

    // ---- Expand/collapse helpers -----------------------------------------------------------------

    // A concrete selection for rendering/button state; null (fully expanded) becomes an all-open selection.
    private GroupSelection effectiveSelection() {
        return groupSelection != null
                ? groupSelection
                : GroupSelection.builder().expandedDepth(maxDepth).build();
    }

    // Whether a span is expanded (its children shown). A null selection means everything is open.
    private boolean isOpen(final String spanId, final int depth) {
        return groupSelection == null || groupSelection.isGroupOpen(spanId, depth);
    }

    private void toggleSpan(final String spanId, final int depth) {
        if (spanId == null) {
            return;
        }
        final GroupSelection selection = effectiveSelection();
        if (selection.isGroupOpen(spanId, depth)) {
            selection.close(spanId);
        } else {
            selection.open(spanId);
        }
        groupSelection = selection;
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        if (largeMode) {
            // The visible row set (and offsets) changes with the selection → refetch from the first page.
            pageCursors.clear();
            goToPage(0);
        } else {
            refresh();
        }
        updateExpandButtons();
    }

    private void updateExpandButtons() {
        final GroupSelection selection = effectiveSelection();
        groupLevelButtons.update(selection, maxDepth);
    }

    private static int parseDepth(final String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    // Deepest 0-based depth in the whole-trace forest (for the expand-level button cap).
    private int computeMaxDepth() {
        if (trace == null) {
            return 0;
        }
        final int[] max = {0};
        forestRoots().forEach(root -> accMaxDepth(root, 0, max));
        return max[0];
    }

    private void accMaxDepth(final Span span, final int depth, final int[] max) {
        if (depth > max[0]) {
            max[0] = depth;
        }
        trace.children(span).forEach(child -> accMaxDepth(child, depth + 1, max));
    }

    private void refresh() {
        // Capture the operation list scroll position; setHTML() below rebuilds the
        // whole DOM and would otherwise reset it to the top on every span click/resize.
        final int operationScrollTop = getOperationListScrollTop();
        final HtmlBuilder hb = new HtmlBuilder();
        final boolean hasContent = (largeMode && largeRoot != null) || trace != null;
        // The pager is shown only when a trace is displayed (disabled for normal/whole traces).
        pagerBar.setVisible(hasContent);
        updateExpandButtons();
        if (hasContent) {
            hb.div(div -> {
                appendTimelineHeader(div);
                appendTimelineControls(div);
                appendButtons(div);

            }, Attribute.className("trace-overview-chart__timeline"));

            hb.div(body -> {
                appendServiceOperations(body);
                if (selectedSpan != null) {
                    appendSpanInfo(body, selectedSpan);
                }
            }, Attribute.className("trace-overview-chart__body"));
        }
        panel.setHTML(hb.toSafeHtml());
        setOperationListScrollTop(operationScrollTop);
    }

    private int getOperationListScrollTop() {
        final Element list = ElementUtil.findChild(panel.getElement(), "trace-overview-chart__operation-list");
        return list == null ? 0 : list.getScrollTop();
    }

    private void setOperationListScrollTop(final int scrollTop) {
        final Element list = ElementUtil.findChild(panel.getElement(), "trace-overview-chart__operation-list");
        if (list != null) {
            list.setScrollTop(scrollTop);
        }
    }

    private void appendTimelineHeader(final HtmlBuilder hb) {
        hb.div(div -> {
            final long duration = extents.totalDuration.getNanos();

            // Quantise into 10 chunks.
            final long quantised = duration / 10;

            // Get the logarithm base 10
            final double log10 = Math.log10(quantised);

            // Round to nearest integer to get the exponent
            final int exponent = (int) Math.ceil(log10);

            // Return 10 raised to that power
            // Ensure each big chunk is at least 100ns.
            final long bigChunk = (long) Math.max(100, Math.pow(10, exponent));

            final double pctInc = duration == 0
                    ? 100D
                    : (100D / duration) * bigChunk;

            // Add markers in between.
            double pct = 0;
            long time = 0;
            while (pct < 100) {
                final long t = time;
                div.span(s -> s.append(NanoTime.ofNanos(t).toString()),
                        Attribute.style("--trace-overview-chart__marker-left: " + pct + "%;"));
                pct += pctInc;
                time += bigChunk;
            }

            // Add last marker if we think there is room.
            if (pct - pctInc < 90) {
                div.span(s -> s.append(extents.totalDuration.toString()),
                        Attribute.className("trace-overview-chart__timeline-end"));
            }

            div.append(SafeHtmlUtil.NBSP);

        }, Attribute.className("trace-overview-chart__timeline-header"));
    }

    private void appendTimelineControls(final HtmlBuilder hb) {
        hb.div(div -> {
            appendTimelineBar(div);
            appendTimeSlider(div);
        }, Attribute.className("trace-overview-chart__timeline-controls"));
    }

    private void appendTimelineBar(final HtmlBuilder hb) {
        final String style = computeGridStyle(0, extents.totalDuration.getNanos());
        hb.div(div -> {
            if (largeMode) {
                // The current page's spans, drawn against the page-relative axis — no downsampling.
                final int rowCount = pageRows.size();
                for (int i = 0; i < pageRows.size(); i++) {
                    appendSpanBar(div, pageRows.get(i).getSpan(), i, rowCount);
                }
            } else {
                final List<Span> roots = forestRoots();
                final int rowCount = forestSize(roots);
                final int[] rowIndex = {0};
                roots.forEach(span -> appendSpanForest(div, span, rowIndex, rowCount));
            }
        }, Attribute.className("trace-overview-chart__timeline-bar"),
                Attribute.id("timelineBar"),
                Attribute.style(style));
    }

    private int forestSize(final List<Span> spans) {
        int n = 0;
        for (final Span span : spans) {
            n += 1 + forestSize(trace.children(span));
        }
        return n;
    }

    // Top-level spans to render as waterfall roots: the single root for a normal trace, or — for an
    // orphan-only trace (no root) — every span whose parent is absent from this trace. Callers recurse
    // via Trace.children(span).
    private List<Span> forestRoots() {
        if (trace == null) {
            return List.of();
        }
        final Span root = trace.root();
        if (root != null) {
            return List.of(root);
        }
        final List<Span> roots = new ArrayList<>();
        trace.getParentSpanIdMap().values().forEach(spans -> spans.forEach(span -> {
            final String parentSpanId = span.getParentSpanId();
            if (parentSpanId == null || parentSpanId.isEmpty() || !spanById.containsKey(parentSpanId)) {
                roots.add(span);
            }
        }));
        // Deterministic order (the map iteration order is not stable) — earliest span first.
        roots.sort(Comparator.comparing(Span::start, Comparator.nullsLast(Comparator.naturalOrder())));
        return roots;
    }

    private void appendSpanForest(final HtmlBuilder hb,
                                  final Span span,
                                  final int[] rowIndex,
                                  final int rowCount) {
        appendSpanBar(hb, span, rowIndex[0]++, rowCount);
        trace.children(span).forEach(child -> appendSpanForest(hb, child, rowIndex, rowCount));
    }

    private void appendSpanBar(final HtmlBuilder hb,
                               final Span span,
                               final int rowIndex,
                               final int rowCount) {
        final double leftPct = span.start().diff(extents.min).getNanos() * extents.increments;
        final double widthPct = span.duration().getNanos() * extents.increments;
        // One row per span, positioned horizontally by time. If the whole set fits at the fixed row
        // stride, stack at that stride (a single span is one thin bar); otherwise compress the rows so
        // they fill the strip and none are clipped.
        final int usable = OVERVIEW_STRIP_PX - 2;
        final int topPx;
        final int barH;
        if (rowCount * OVERVIEW_ROW_PX <= usable) {
            topPx = 1 + (rowIndex * OVERVIEW_ROW_PX);
            barH = OVERVIEW_BAR_PX;
        } else {
            topPx = 1 + (rowCount <= 0 ? 0 : (int) ((double) rowIndex * usable / rowCount));
            barH = Math.max(1, rowCount <= 0 ? usable : (usable + rowCount - 1) / rowCount);
        }

        hb.div("",
                Attribute.className("trace-overview-chart__timeline-span"),
                Attribute.title(span.getName() + " (" + span.duration() + ")"),
                new Attribute("data-span-id", span.getSpanId()),
                Attribute.style(
                        "--trace-overview-chart__timeline-span-left: " + leftPct
                        + "%; --trace-overview-chart__timeline-span-width: " + widthPct
                        + "%; --trace-overview-chart__timeline-span-top: " + topPx
                        + "px; --trace-overview-chart__timeline-span-height: " + barH + "px;"));
    }

    private void appendTimeSlider(final HtmlBuilder hb) {
        final double startPct = (100D / extents.totalDuration.getNanos()) * windowStart.getNanos();
        final double endPct = (100D / extents.totalDuration.getNanos()) * windowEnd.getNanos();
        hb.div(div -> {
            div.div("",
                    Attribute.className("trace-overview-chart__dim-overlay trace-overview-chart__dim-overlay--before"),
                    Attribute.id("leftDimOverlay"));
            div.div("",
                    Attribute.className("trace-overview-chart__dim-overlay trace-overview-chart__dim-overlay--after"),
                    Attribute.id("rightDimOverlay"));
            div.div("",
                    Attribute.className("trace-overview-chart__range-overlay"),
                    Attribute.id("timeRangeOverlay"));
            div.div(dim -> div.div(d -> d.append(formatHandleTime(windowStart)),
                            Attribute.className("trace-overview-chart__slider-label"),
                            Attribute.id("startLabel")),
                    Attribute.className("trace-overview-chart__slider trace-overview-chart__slider--start"),
                    Attribute.id("startSlider"));
            div.div(dim -> div.div(d -> d.append(formatHandleTime(windowEnd)),
                            Attribute.className("trace-overview-chart__slider-label"),
                            Attribute.id("endLabel")),
                    Attribute.className("trace-overview-chart__slider trace-overview-chart__slider--end"),
                    Attribute.id("endSlider"));

        }, Attribute.className("trace-overview-chart__slider-container"),
                // Where the window starts and ends, which the overlays and sliders are placed by
                Attribute.style("--trace-overview-chart__window-start: " + startPct
                                + "%; --trace-overview-chart__window-end: " + endPct + "%;"));
    }

    private void appendButtons(final HtmlBuilder hb) {
        hb.div(div -> {
            String info = "Full Range";
            final NanoDuration windowSize = NanoDuration.ofNanos(windowEnd.getNanos() - windowStart.getNanos());
            if (windowSize.isLessThan(extents.totalDuration)) {
                info = windowSize +
                       " (" +
                       StringUtil.formatDouble((100D / extents.totalDuration.getNanos()) * windowSize.getNanos()) +
                       "%)";
            }

            div.elem("Reset Range", SafeHtmlUtil.from("button"),
                    Attribute.className("trace-overview-chart__reset-range-button"),
                    Attribute.id("resetRangeBtn"));
            div.div(info,
                    Attribute.className("trace-overview-chart__range-info"),
                    Attribute.id("timeRangeInfo"));

        }, Attribute.className("trace-overview-chart__timeline-buttons"));
    }

    private Extents computeExtents() {
        if (trace == null) {
            return new Extents(NanoTime.ZERO, NanoTime.ZERO, NanoDuration.ZERO, 100);
        } else {
            // Discover min and max time ranges.
            final AtomicReference<NanoTime> minRef = new AtomicReference<>();
            final AtomicReference<NanoTime> maxRef = new AtomicReference<>();
            trace.getParentSpanIdMap().values().stream().flatMap(List::stream).forEach(span -> {
                final NanoTime start = span.start();
                final NanoTime end = span.end();
                if (minRef.get() == null || minRef.get().isGreaterThan(start)) {
                    minRef.set(start);
                }
                if (maxRef.get() == null || maxRef.get().isLessThan(end)) {
                    maxRef.set(end);
                }
            });

            final NanoTime min = minRef.get();
            final NanoTime max = maxRef.get();
            final NanoDuration totalDuration = min.diff(max);
            final double increments = 100D / totalDuration.getNanos();
            return new Extents(min, max, totalDuration, increments);
        }
    }


    private void appendServiceOperations(final HtmlBuilder hb) {

        hb.div(div -> {
            appendGridLines(div);
            appendSectionHeader(div);
            appendOperationList(div);

        }, Attribute.className("trace-overview-chart__operations"));
    }

    private void appendGridLines(final HtmlBuilder hb) {
        final String style = computeGridStyle(windowStart.getNanos(), windowEnd.subtract(windowStart).getNanos());
        hb.div("", Attribute.className("trace-overview-chart__grid-lines"), Attribute.style(style));
    }

    private String computeGridStyle(final long start,
                                    final long duration) {
        // Quantise into 10 chunks.
        final long quantised = duration / 10;

        // Get the logarithm base 10
        final double log10 = Math.log10(quantised);

        // Round to nearest integer to get the exponent
        final int exponent = (int) Math.ceil(log10);

        // Return 10 raised to that power
        // Ensure each big chunk is at least 100ns so small chunks are no smaller than 10ns.
        final long bigChunk = (long) Math.max(100, Math.pow(10, exponent));
        final long smallChunk = bigChunk / 10;

        final double inc = duration == 0
                ? 0D
                : 100D / duration;
        final double bigPreChunk = start % bigChunk;
        final double smallPreChunk = start % smallChunk;

        final double bigWidthPct = bigChunk * inc;
        final double smallWidthPct = smallChunk * inc;

        final double bigAbsolute = -bigPreChunk;
        final double smallAbsolute = -smallPreChunk;

        final double bigOffsetPct = (bigAbsolute / (duration - bigChunk)) * 100D;
        final double smallOffsetPct = (smallAbsolute / (duration - smallChunk)) * 100D;

        // The grid's spacing and offsets, which __timeline-bar and __grid-lines draw their lines by
        return "--trace-overview-chart__grid-big-size: " +
               bigWidthPct +
               "%; --trace-overview-chart__grid-small-size: " +
               smallWidthPct +
               "%; --trace-overview-chart__grid-big-offset: " +
               bigOffsetPct +
               "%; --trace-overview-chart__grid-small-offset: " +
               smallOffsetPct +
               "%;";
    }


    private void appendSectionHeader(final HtmlBuilder hb) {
        hb.div(div -> div.append(SafeHtmlUtils.fromTrustedString("Service &amp; Operation")),
                Attribute.className("trace-overview-chart__section-header"));
    }


    // The list scrolls when it is taller than its pane and nothing in it takes the focus, so it is a
    // named region that does, so that the keyboard can scroll it (a keyboard list or tree of its rows
    // would be better still)
    private void appendOperationList(final HtmlBuilder hb) {
        final Attribute[] attributes = {
                Attribute.className("trace-overview-chart__operation-list scroll-region"),
                Attribute.id("operationList"),
                new Attribute("tabindex", "0"),
                new Attribute("role", "region"),
                new Attribute("aria-label", "Service and operation")};
        if (largeMode) {
            // One page of already-flattened tree-order rows (depth carried per row). The server has already
            // pruned collapsed subtrees, so we just render the rows and their expanders.
            hb.div(div -> pageRows.forEach(r ->
                            appendPagedRow(div, r.getSpan(), r.getDepth(), r.isHasChildren())),
                    attributes);
        } else {
            hb.div(div -> forestRoots().forEach(span -> appendOperationItem(div, span, extents, 0)),
                    attributes);
        }
    }

    private void appendPagedRow(final HtmlBuilder hb, final Span span, final int depth,
                                final boolean hasChildren) {
        hb.div(div -> {
            appendOperationContent(div, span, depth, hasChildren);
            appendWaterfall(div, span);
        }, Attribute.className(isSelected(span)
                        ? "trace-overview-chart__operation trace-overview-chart__operation--selected"
                        : "trace-overview-chart__operation"),
                new Attribute("data-span-id", span.getSpanId()));
    }

    private void appendOperationItem(final HtmlBuilder hb,
                                     final Span span,
                                     final Extents extents,
                                     final int depth) {
        final List<Span> children = trace.children(span);
        final boolean hasChildren = !children.isEmpty();

        hb.div(div -> {
            appendOperationContent(div, span, depth, hasChildren);
            appendWaterfall(div, span);
        }, Attribute.className(isSelected(span)
                        ? "trace-overview-chart__operation trace-overview-chart__operation--selected"
                        : "trace-overview-chart__operation"),
                new Attribute("data-span-id", span.getSpanId()));

        // Client-side prune: only descend into a span's children when it is expanded.
        if (hasChildren && isOpen(span.getSpanId(), depth)) {
            hb.div(div -> children.forEach(child -> appendOperationItem(div, child, extents, depth + 1)),
                    Attribute.className("trace-overview-chart__children"));
        }

    }

    // Indent computed from depth so nesting works at any depth (there is no fixed set of indent-N
    // CSS classes to run out of). The name is truncated with an ellipsis by CSS, so expose the full
    // name as a hover tooltip.
    private void appendOperationContent(final HtmlBuilder hb, final Span span, final int depth,
                                        final boolean hasChildren) {
        // Indent the whole content row (expander + name) by depth so they stay aligned.
        hb.div(c -> {
            appendExpander(c, span, depth, hasChildren);
            final String name = span.getName() == null ? "" : span.getName();
            final HtmlBuilder nameHtml = new HtmlBuilder();
            nameHtml.span(name, Attribute.className("trace-overview-chart__operation-name"), Attribute.title(name));
            CopyTextUtil.render(name, nameHtml.toSafeHtml(), c, false);
        }, Attribute.className("trace-overview-chart__operation-content"),
                Attribute.style("--trace-overview-chart__depth: " + depth + ";"));
    }

    // The expander cell: a clickable +/- for a span with children (▾ open, ▸ collapsed), or a leaf spacer
    // so names line up. Clicking it toggles the span (see the mouse-down handler + data-expander-* attrs).
    private void appendExpander(final HtmlBuilder hb, final Span span, final int depth,
                                final boolean hasChildren) {
        if (!hasChildren) {
            hb.span("", Attribute.className("trace-overview-chart__expand trace-overview-chart__expand--leaf"));
            return;
        }
        // The same ARROW_DOWN / ARROW_RIGHT SVGs used by the query/dashboard tables and the navigator.
        final SafeHtml icon = SvgImageUtil.toSafeHtml(
                isOpen(span.getSpanId(), depth) ? SvgImage.ARROW_DOWN : SvgImage.ARROW_RIGHT,
                "trace-overview-chart__expand-icon");
        hb.span(s -> s.append(icon),
                Attribute.className("trace-overview-chart__expand"),
                new Attribute("data-expander-span-id", span.getSpanId()),
                new Attribute("data-expander-depth", String.valueOf(depth)));
    }

    private void appendWaterfall(final HtmlBuilder hb, final Span span) {
        hb.div(c -> {
            final NanoDuration windowSize = windowEnd.subtract(windowStart);
            NanoDuration offsetStart = span.start().diff(extents.min);
            NanoDuration offsetEnd = span.end().diff(extents.min);
            offsetStart = offsetStart.subtract(windowStart);
            offsetEnd = offsetEnd.subtract(windowStart);

            if (offsetStart.isLessThan(NanoDuration.ZERO)) {
                offsetStart = NanoDuration.ZERO;
            }
            if (offsetEnd.isLessThan(NanoDuration.ZERO)) {
                offsetEnd = NanoDuration.ZERO;
            } else if (offsetEnd.isGreaterThan(windowSize)) {
                offsetEnd = windowSize;
            }

            final NanoDuration duration = offsetEnd.subtract(offsetStart);
            if (duration.isGreaterThan(NanoDuration.ZERO)) {
                final double increment = 100D / windowSize.getNanos();
                double leftPct = offsetStart.getNanos() * increment;
                double widthPct = offsetEnd.subtract(offsetStart).getNanos() * increment;

                leftPct = Math.max(Math.min(leftPct, 100), 0);
                widthPct = Math.max(Math.min(widthPct, 100), 0);

                // A span that reported an error status gets a red bar; otherwise the default style.
                final String barClass = isErrorSpan(span)
                        ? "trace-overview-chart__span-bar trace-overview-chart__span-bar--error"
                        : "trace-overview-chart__span-bar trace-overview-chart__span-bar--http";
                c.div("",
                        Attribute.className(barClass),
                        Attribute.style("--trace-overview-chart__bar-left: " + leftPct
                                        + "%; --trace-overview-chart__bar-width: " + widthPct + "%;"));
                c.span(span.duration().toString(),
                        Attribute.className("trace-overview-chart__duration"),
                        Attribute.style("--trace-overview-chart__bar-end: " + (leftPct + widthPct) + "%;"));
            }
        }, Attribute.className("trace-overview-chart__waterfall"));
    }

    private static boolean isErrorSpan(final Span span) {
        final SpanStatus status = span.getStatus();
        return status != null && StatusCode.STATUS_CODE_ERROR.equals(status.getCode());
    }

    // ---- Paged waterfall navigation --------------------------------------------------------------

    // A pager button (or from/to edit) changed the range. 'next' is gated by hasMore so it reverts at the
    // true end even when an over-counted total left the button enabled; every other target — first /
    // prev / jump / last / refresh — is served by goToPage.
    private void onPagerRange(final Range range) {
        if (!largeMode || fetcher == null || fetching) {
            return;
        }
        final int targetIndex = Math.max(0, range.getStart() / SPANS_PER_PAGE);
        if (targetIndex == pageIndex + 1 && !hasMore) {
            revertPagerRange();
        } else {
            goToPage(targetIndex);
        }
    }

    private void revertPagerRange() {
        suppressRangeEvent = true;
        pagerRows.setVisibleRange(pageIndex * SPANS_PER_PAGE, SPANS_PER_PAGE);
        suppressRangeEvent = false;
    }

    // Fetches the target page, sending its offset AND the resume cursor if we know one. The server uses
    // the cursor when present (cheap sequential next/prev) and the offset otherwise (live checkpoints, or
    // a merged checkpoint index for split traces), so first/prev/next/last/jump all work.
    private void goToPage(final int targetIndex) {
        if (fetcher == null || fetching) {
            return;
        }
        fetching = true;
        final int offset = targetIndex * SPANS_PER_PAGE;
        final String cursor = targetIndex == 0 ? null : pageCursors.get(targetIndex);
        fetcher.fetch(offset, cursor, SPANS_PER_PAGE, groupSelection, page -> {
            fetching = false;
            if (!largeMode) {
                return; // switched trace/mode while the request was in flight
            }
            applyPage(targetIndex, page);
        });
    }

    private void applyPage(final int targetIndex, final TraceSpanPage page) {
        pageIndex = targetIndex;
        pageRows.clear();
        if (page != null && page.getRows() != null) {
            pageRows.addAll(page.getRows());
            page.getRows().forEach(r -> spanById.put(r.getSpan().getSpanId(), r.getSpan()));
        }
        // Record the resume cursor that fetches the NEXT page (null once the last page is reached), so a
        // subsequent 'next' stays on the cheap sequential path.
        pageCursors.put(targetIndex + 1, page == null ? null : page.getNextCursor());
        hasMore = page != null && page.isMore();
        // The server's exact merged total (split traces, once its checkpoints are built) corrects "of N".
        if (page != null && page.getTotalSpans() != null) {
            totalSpans = page.getTotalSpans();
        }

        // The timeline reflects only this page's spans (min start / max end), so the slider can't be
        // dragged beyond the page's extent.
        this.extents = computeExtentsFromRows(pageRows);
        windowStart = NanoDuration.ZERO;
        windowEnd = extents.totalDuration;

        syncPager();
        refresh();
        // A new page starts at the top (refresh() otherwise restores the previous page's scroll offset,
        // which is right for a span-click/slider re-render but not for navigating to a different page).
        setOperationListScrollTop(0);
    }

    // Pushes the current page state into the pager's HasRows adapter so its buttons/labels reflect it: the
    // exact "of N" total and full first/prev/next/last/jump (offset access now works for both live and
    // split traces). Guarded so these write-backs don't re-trigger a fetch.
    private void syncPager() {
        suppressRangeEvent = true;
        pagerRows.setVisibleRange(pageIndex * SPANS_PER_PAGE, SPANS_PER_PAGE);
        pagerRows.setRowCount(totalSpans, true);
        pager.setLastPageAllowed(true);
        suppressRangeEvent = false;
    }

    private Extents computeExtentsFromRows(final List<TraceSpanRow> rows) {
        NanoTime min = null;
        NanoTime max = null;
        for (final TraceSpanRow r : rows) {
            final NanoTime start = r.getSpan().start();
            final NanoTime end = r.getSpan().end();
            if (start != null && (min == null || min.isGreaterThan(start))) {
                min = start;
            }
            if (end != null && (max == null || max.isLessThan(end))) {
                max = end;
            }
        }
        if (min == null || max == null) {
            return new Extents(NanoTime.ZERO, NanoTime.ZERO, NanoDuration.ZERO, 100);
        }
        final NanoDuration totalDuration = min.diff(max);
        final double increments = totalDuration.getNanos() == 0 ? 100D : 100D / totalDuration.getNanos();
        return new Extents(min, max, totalDuration, increments);
    }

    // Drives THIS widget's pager refresh spinner (not the traces list's) while span pages are fetched.
    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                pager.getRefreshButton().setRefreshing(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;
                pager.getRefreshButton().setRefreshing(taskCount > 0);
            }
        };
    }

    private boolean isSelected(final Span span) {
        return selectedSpan != null && selectedSpan.getSpanId().equals(span.getSpanId());
    }

    /**
     * Renders the Span Info side panel for the given span: identity, timing and all
     * OTel attributes. Shown when a span row is clicked; closed via the header button
     * (id {@code closeSpanInfo}).
     */
    private void appendSpanInfo(final HtmlBuilder hb, final Span span) {
        hb.div(sp -> {
            // Left-edge drag handle for resizing the panel.
            sp.div("", Attribute.className("trace-overview-chart__span-info-resize"), Attribute.id("spanInfoResize"));
            sp.div(header -> {
                header.span("Span Info", Attribute.className("trace-overview-chart__span-info-title"));
                header.div("×",
                        Attribute.className("trace-overview-chart__span-info-close"),
                        Attribute.id("closeSpanInfo"),
                        Attribute.title("Close"));
            }, Attribute.className("trace-overview-chart__span-info-header"));

            sp.div(body -> {
                body.div(span.getName(), Attribute.className("trace-overview-chart__span-info-name"));

                appendInfoRow(body, "Kind", span.getKind() == null ? "" : span.getKind().toString());
                if (span.start() != null && span.end() != null) {
                    appendInfoRow(body, "Duration", span.duration().toString());
                }
                appendInfoRow(body, "Start", formatTime(span.start()));
                appendInfoRow(body, "End", formatTime(span.end()));
                appendInfoRow(body, "Span ID", span.getSpanId());
                appendInfoRow(body, "Parent Span ID", span.getParentSpanId());

                final List<KeyValue> attributes = span.getAttributes();
                if (attributes != null && !attributes.isEmpty()) {
                    body.div("Attributes", Attribute.className("trace-overview-chart__span-info-section"));
                    attributes.forEach(attr -> {
                        final String value = rawValue(attr.getValue());
                        body.div(row -> {
                            row.span(attr.getKey() + ": ", Attribute.className("trace-overview-chart__span-info-key"));
                            CopyTextUtil.render(value, row, false);
                        }, Attribute.className("trace-overview-chart__span-info-attr"));
                    });
                }
            }, Attribute.className("trace-overview-chart__span-info-body scroll-region"),
                    // It scrolls when it is taller than the panel, so, as the operation list, it is
                    // a named region that takes the focus, so that the keyboard can scroll it
                    new Attribute("tabindex", "0"),
                    new Attribute("role", "region"),
                    new Attribute("aria-label", "Span info"));
        }, Attribute.className("trace-overview-chart__span-info"),
                Attribute.id("spanInfo"),
                Attribute.style("--trace-overview-chart__span-info-width: " + spanInfoWidth + "px;"));
    }

    private void appendInfoRow(final HtmlBuilder hb, final String key, final String value) {
        hb.div(row -> {
            row.span(key + ": ", Attribute.className("trace-overview-chart__span-info-key"));
            CopyTextUtil.render(value, row, false);
        }, Attribute.className("trace-overview-chart__span-info-row"));
    }

    // Raw string for string values (toString() would add surrounding quotes, which CopyTextUtil would copy).
    private static String rawValue(final AnyValue value) {
        if (value == null) {
            return "";
        }
        return value.getStringValue() != null
                ? value.getStringValue()
                : value.toString();
    }

    private static String formatTime(final NanoTime time) {
        if (time == null) {
            return "";
        }
        return DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss.SSS")
                .format(new Date(time.toEpochMillis()));
    }

    // Absolute wall-clock time at a slider handle = the page's earliest span start (extents.min) plus the
    // handle's offset into the page. So the handles read the min/max span times of the current page (and
    // update as the window is dragged), rather than a duration-from-page-start (which was always "0ms").
    private String formatHandleTime(final NanoDuration offsetFromMin) {
        if (extents == null || extents.min == null || offsetFromMin == null) {
            return "";
        }
        final long millis = extents.min.toEpochMillis() + (offsetFromMin.getNanos() / 1_000_000L);
        return DateTimeFormat.getFormat("HH:mm:ss.SSS").format(new Date(millis));
    }

    // A minimal HasRows the standalone Pager can drive: it holds the row count / exact flag / visible
    // range and fires the range/row-count events the pager listens to. The detail waterfall isn't a cell
    // widget, so this bridges the pager's offset model to our page fetches (see onPagerRange / syncPager).
    private static final class TracePagerRows implements HasRows {

        private final HandlerManager handlerManager = new HandlerManager(this);
        private Range visibleRange = new Range(0, SPANS_PER_PAGE);
        private int rowCount;
        private boolean rowCountExact;

        @Override
        public HandlerRegistration addRangeChangeHandler(final RangeChangeEvent.Handler handler) {
            return handlerManager.addHandler(RangeChangeEvent.getType(), handler);
        }

        @Override
        public HandlerRegistration addRowCountChangeHandler(final RowCountChangeEvent.Handler handler) {
            return handlerManager.addHandler(RowCountChangeEvent.getType(), handler);
        }

        @Override
        public int getRowCount() {
            return rowCount;
        }

        @Override
        public Range getVisibleRange() {
            return visibleRange;
        }

        @Override
        public boolean isRowCountExact() {
            return rowCountExact;
        }

        @Override
        public void setRowCount(final int count) {
            setRowCount(count, true);
        }

        @Override
        public void setRowCount(final int count, final boolean isExact) {
            this.rowCount = count;
            this.rowCountExact = isExact;
            RowCountChangeEvent.fire(this, count, isExact);
        }

        @Override
        public void setVisibleRange(final int start, final int length) {
            setVisibleRange(new Range(start, length));
        }

        @Override
        public void setVisibleRange(final Range range) {
            this.visibleRange = range;
            RangeChangeEvent.fire(this, range);
        }

        @Override
        public void fireEvent(final GwtEvent<?> event) {
            handlerManager.fireEvent(event);
        }
    }

    interface Binder extends UiBinder<Widget, TraceOverviewChart> {

    }

    private static class Extents {

        public final NanoTime min;
        public final NanoTime max;
        public final NanoDuration totalDuration;
        public final double increments;

        public Extents(final NanoTime min,
                       final NanoTime max,
                       final NanoDuration totalDuration,
                       final double increments) {
            this.min = min;
            this.max = max;
            this.totalDuration = totalDuration;
            this.increments = increments;
        }
    }
}
