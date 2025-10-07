package stroom.pathways.client.presenter;

import stroom.data.grid.client.DefaultResources;
import stroom.data.grid.client.Glass;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.util.shared.StringUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TraceOverviewWidget extends Composite {

    private final HTML panel = new HTML();
    private Trace trace;


    private Extents extents;
    private Glass glass;
    private boolean resizingStart;
    private boolean resizingEnd;
    private int offsetX;
    private int startX;
    private NanoDuration windowStart = NanoDuration.ZERO;
    private NanoDuration windowEnd = NanoDuration.ZERO;

    public TraceOverviewWidget(final DefaultResources resources) {
        initWidget(panel);
        glass = new Glass(resources.dataGridStyle().resizeGlass());

        panel.addMouseDownHandler(e -> {

            final Element element = e.getNativeEvent().getEventTarget().cast();
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
            }

        });
        panel.addMouseUpHandler(e -> {
            releaseCapture();
            resizingStart = false;
            resizingEnd = false;
        });
        panel.addMouseMoveHandler(e -> {
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
        final Element element = ElementUtil.findChild(panel.getElement(), "timeline-controls");
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

//    @Override
//    public void onBrowserEvent(final Event event) {
//        super.onBrowserEvent(event);
//        if (event.getTypeInt() == Event.ONMOUSEDOWN) {
//            final Element element = event.getEventTarget().cast();
//            if ("startSlider".equals(element.getId())) {
//                startX = event.getClientX();
//                offsetX = startX - element.getAbsoluteLeft();
//
//
//                if (glass == null) {
//                    glass = new Glass(resources.dataGridStyle().resizeGlass());
//                }
//                glass.show();
//                resizing = true;
//
//
//            }
//        } else if (event.getTypeInt() == Event.ONMOUSEUP) {
//            if (glass != null) {
//                glass.hide();
//                resizing = false;
//            }
//        } else if (event.getTypeInt() == Event.ONMOUSEMOVE) {
//            if (resizing) {
//                final int width = panel.getElement().getClientWidth();
//                final int left = panel.getElement().getScrollLeft();
//                final int right = panel.getElement().getScrollLeft() + panel.getElement().getClientWidth();
//                final double diff = event.getClientX() - left;
//                final double increments = (double) extents.totalDuration.toEpochNanos() / width;
//                if (event.getClientX() < left) {
//                    windowStart = extents.min;
//                } else if (event.getClientX() > right) {
//                    windowStart = extents.max;
//                } else {
//                    windowStart = NanoTime.ofNanos((long) (diff * increments));
//                }
//
//                if (windowEnd != null && windowStart.isGreaterThan(windowEnd)) {
//                    windowStart = windowEnd;
//                }
//
//                refresh();
//            }
//        }
//
//    }

    public void setTrace(final Trace trace) {
        this.trace = trace;
        this.extents = computeExtents();
        windowStart = NanoDuration.ZERO;
        windowEnd = extents.totalDuration;
        refresh();
    }

    private void refresh() {
        final HtmlBuilder hb = new HtmlBuilder();
        if (trace != null) {
            hb.div(div -> {
                appendTimelineHeader(div);
                appendTimelineControls(div);
                appendButtons(div);

            }, Attribute.className("timeline-container"));

            appendServiceOperations(hb);
        }
        panel.setHTML(hb.toSafeHtml());
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
                        Attribute.style("left: " + pct + "%;"));
                pct += pctInc;
                time += bigChunk;
            }

            // Add last marker if we think there is room.
            if (pct - pctInc < 90) {
                div.span(s -> s.append(extents.totalDuration.toString()), Attribute.style("left: 100%;"));
            }

            div.append(SafeHtmlUtil.NBSP);

        }, Attribute.className("timeline-header"));
    }

    private void appendTimelineControls(final HtmlBuilder hb) {
        hb.div(div -> {
            appendTimelineBar(div);
            appendTimeSlider(div);
        }, Attribute.className("timeline-controls"));
    }

    private void appendTimelineBar(final HtmlBuilder hb) {
        final String style = computeGridStyle(0, extents.totalDuration.getNanos());
        hb.div(div -> {
            final AtomicInteger row = new AtomicInteger();
            appendSpan(div, trace.root(), row);
        }, Attribute.className("timeline-bar"),
                Attribute.id("timelineBar"),
                Attribute.style("height: 40px;" + style));
    }

    private void appendSpan(final HtmlBuilder hb,
                            final Span span,
                            final AtomicInteger row) {
        final double leftPct = span.start().diff(extents.min).getNanos() * extents.increments;
        final double widthPct = span.duration().getNanos() * extents.increments;
        final int topPx = 2 + (3 * row.getAndIncrement());

        hb.div("",
                Attribute.className("timeline-span"),
                Attribute.title(span.getName() + " (" + span.duration() + ")"),
                new Attribute("data-span-id", span.getSpanId()),
                Attribute.style(
                        "left: " + leftPct + "%; width: " + widthPct +
                        "%; background-color: rgb(255, 140, 66); top: " + topPx +
                        "px; height: 2px; opacity: 0.9; position: absolute; border-radius: 1px; min-width: 1px;"));

        trace.children(span).forEach(child -> appendSpan(hb, child, row));
    }

    private void appendTimeSlider(final HtmlBuilder hb) {
        hb.div(div -> {

            final double startPct = (100D / extents.totalDuration.getNanos()) * windowStart.getNanos();
            final double endPct = (100D / extents.totalDuration.getNanos()) * windowEnd.getNanos();

            div.div("",
                    Attribute.className("time-dim-overlay"),
                    Attribute.id("leftDimOverlay"),
                    Attribute.style("left: 0%; width: " + startPct + "%;"));
            div.div("",
                    Attribute.className("time-dim-overlay"),
                    Attribute.id("rightDimOverlay"),
                    Attribute.style("left: " + endPct + "%; width: " + (100 - endPct) + "%;"));
            div.div("",
                    Attribute.className("time-range-overlay"),
                    Attribute.id("timeRangeOverlay"),
                    Attribute.style("left: " + startPct + "%; width: " + (endPct - startPct) + "%;"));
            div.div(dim -> div.div(d -> d.append(windowStart.toString()),
                            Attribute.className("slider-label"),
                            Attribute.id("startLabel")),
                    Attribute.className("time-slider"),
                    Attribute.id("startSlider"),
                    Attribute.style("left: " + startPct + "%;"));
            div.div(dim -> div.div(d -> d.append(windowEnd.toString()),
                            Attribute.className("slider-label"),
                            Attribute.id("endLabel")),
                    Attribute.className("time-slider"),
                    Attribute.id("endSlider"),
                    Attribute.style("left: " + endPct + "%;"));

        }, Attribute.className("time-slider-container"));
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
                    Attribute.className("reset-range-btn"),
                    Attribute.id("resetRangeBtn"));
            div.div(info,
                    Attribute.className("time-range-info"),
                    Attribute.id("timeRangeInfo"));

        }, Attribute.className("timeline-buttons"));
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

        }, Attribute.className("service-operations"));
    }

    private void appendGridLines(final HtmlBuilder hb) {
        final String style = computeGridStyle(windowStart.getNanos(), windowEnd.subtract(windowStart).getNanos());
        hb.div("", Attribute.className("grid-lines"), Attribute.style(style));
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

//        GWT.log("bigChunk=" + bigChunk +
//                ", smallChunk=" + smallChunk +
//                ", bigPreChunk=" + bigPreChunk +
//                ", smallPreChunk=" + smallPreChunk +
//                ", duration=" + duration +
//                ", bigWidthPct=" + bigWidthPct +
//                ", smallWidthPct=" + smallWidthPct);

        return "background-size:" +
               bigWidthPct +
               "% 100%, " +
               smallWidthPct +
               "% 100%;" +
               "background-position:" +
               bigOffsetPct +
               "% 0, " +
               smallOffsetPct +
               "% 0;";
    }


    private void appendSectionHeader(final HtmlBuilder hb) {
        hb.div(div -> div.append(SafeHtmlUtils.fromTrustedString("Service &amp; Operation")),
                Attribute.className("section-header"));
    }


    private void appendOperationList(final HtmlBuilder hb) {

        hb.div(div -> appendOperationItem(div, trace.root(), extents, 0),
                Attribute.className("operation-list"),
                Attribute.id("operationList"));
    }

    private void appendOperationItem(final HtmlBuilder hb,
                                     final Span span,
                                     final Extents extents,
                                     final int depth) {

        hb.div(div -> {
            div.div(c -> {
//                c.span("▼", Attribute.className("expand-icon"));
//                c.span(span.getName(), Attribute.className("service-name"));
                c.span(span.getName(),
                        Attribute.className("operation-name indent-" + depth));
            }, Attribute.className("operation-content"));
            div.div(c -> {
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

//                GWT.log("name=" + span.getName() + ", offsetstart = " + offsetStart + ", offsetend = " + offsetEnd);

                final NanoDuration duration = offsetEnd.subtract(offsetStart);
                if (duration.isGreaterThan(NanoDuration.ZERO)) {
                    final double increment = 100D / windowSize.getNanos();

//                    final NanoDuration start = offsetStart.subtract(windowStart);
                    double leftPct = offsetStart.getNanos() * increment;
                    double widthPct = offsetEnd.subtract(offsetStart).getNanos() * increment;

                    leftPct = Math.max(Math.min(leftPct, 100), 0);
                    widthPct = Math.max(Math.min(widthPct, 100), 0);

                    c.div("",
                            Attribute.className("span-bar span-http"),
                            Attribute.style("left: " + leftPct + "%; width: " + widthPct + "%;"));
                    c.span(span.duration().toString(),
                            Attribute.className("duration"),
                            Attribute.style("left: " + (leftPct + widthPct) + "%;"));
                }
            }, Attribute.className("waterfall-container"));

        }, Attribute.className("operation-item"), Attribute.id("operationList"));

        final List<Span> children = trace.children(span);
        if (!children.isEmpty()) {
            hb.div(div -> {
                children.forEach(child -> appendOperationItem(div, child, extents, depth + 1));
            }, Attribute.className("children"));
        }


//                    <div class="operation-item " data-expanded="true" data-span-id="7d62e820df6d74d8">
//                        <div class="operation-content">
//                            <span class="expand-icon">▼</span>
//                            <span class="service-name">order-service</span>
//                            <span class="operation-name">POST /orders</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-http" style="left: 0%; width: 100%;"></div>
//                            <span class="duration" style="left: 100%;">245.00ms</span>
//                        </div>
//                    </div>
//                <div class="children" data-parent="7d62e820df6d74d8">
//                    <div class="operation-item indent-1" data-expanded="true" data-span-id="d1e5f7a9b2c8e123">
//                        <div class="operation-content">
//                            <span class="expand-icon">▼</span>
//                            <span class="service-name">inventory-service</span>
//                            <span class="operation-name">check_inventory</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-internal" style="left: 0%; width: 56.37545993458708%;"></div>
//                            <span class="duration" style="left: 56.37545993458708%;">65.00ms</span>
//                        </div>
//                    </div>
//                <div class="children" data-parent="d1e5f7a9b2c8e123">
//                    <div class="operation-item indent-2" data-expanded="true" data-span-id="e3f8a1c5d9e7b456">
//                        <div class="operation-content">
//                            <span class="expand-icon"></span>
//                            <span class="service-name">inventory-service</span>
//                            <span class="operation-name">GET /inventory/:product</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-internal" style="left: 0%; width: 20.602769828291088%;"></div>
//                            <span class="duration" style="left: 20.602769828291088%;">25.00ms</span>
//                        </div>
//                    </div>
//
//                    <div class="operation-item indent-2" data-expanded="true" data-span-id="f5b9c2e6a4d8f789">
//                        <div class="operation-content">
//                            <span class="expand-icon"></span>
//                            <span class="service-name">inventory-service</span>
//                            <span class="operation-name">cache.get</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-cache"
//                            style="left: 25.713154129190514%; width: 5.110384300899427%;"></div>
//                            <span class="duration" style="left: 30.82353843008994%;">5.00ms</span>
//                        </div>
//                    </div>
//                </div>
//                    <div class="operation-item indent-1" data-expanded="true" data-span-id="a7c3e8f1b5d9a012">
//                        <div class="operation-content">
//                            <span class="expand-icon">▼</span>
//                            <span class="service-name">payment-service</span>
//                            <span class="operation-name">process_payment</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-internal"
//                            style="left: 61.48584423548651%; width: 38.51415576451349%;"></div>
//                            <span class="duration" style="left: 100%;">85.00ms</span>
//                        </div>
//                    </div>
//                <div class="children" data-parent="a7c3e8f1b5d9a012">
//                    <div class="operation-item indent-2" data-expanded="true" data-span-id="b8d4f9a2c6e1b345">
//                        <div class="operation-content">
//                            <span class="expand-icon"></span>
//                            <span class="service-name">payment-service</span>
//                            <span class="operation-name">stripe.charge</span>
//                        </div>
//                        <div class="waterfall-container">
//                            <div class="span-bar span-grpc"
//                            style="left: 66.59622853638594%; width: 33.40377146361406%;"></div>
//                            <span class="duration" style="left: 100%;">75.00ms</span>
//                        </div>
//                    </div>
//                </div></div></div>
//    </div>
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
