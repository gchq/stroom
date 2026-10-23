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

import stroom.pathways.shared.TraceHistogram;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.NumberUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A compact bar strip showing trace counts per equal time-bucket over the selected window, rendered
 * as absolutely-positioned percentage-height divs. Clicking a bar zooms the time range to that
 * bucket via the registered handler.
 */
public class HistogramWidget extends Composite {

    private final HTML panel = new HTML();
    private final DateTimeFormatter dateTimeFormatter;
    private TraceHistogram data;
    private boolean rendered;
    private BiConsumer<Long, Long> zoomHandler;
    // What to say when there is no histogram to draw. The default talks about narrowing a time range,
    // which is the answer on the traces list and no answer at all where the bars are counted from
    // something already in hand.
    private String emptyText;
    // Which moment is being looked at elsewhere, so the bar holding it can be picked out. Held as a
    // time rather than a bar, because what is being looked at is a row in a list and which bar that
    // falls in is this widget's business.
    private Long selectedMs;
    // Who to tell when the reader drags across the plot. Unset where scrubbing means nothing, which
    // leaves the gesture off altogether rather than moving a line nothing is listening to.
    private Consumer<Long> scrubHandler;
    private boolean scrubbing;
    // Where the reader left the line, which is the moment being looked at rather than the change being
    // shown. The two are not the same: a change is a moment, and so is the stretch between two of
    // them, throughout which the model stood exactly as the earlier one left it. A line dropped in a
    // gap is pointing at something real, so it is left there until a selection made somewhere other
    // than the plot says otherwise.
    private Long scrubbedMs;
    // Whether the labels along the bottom say which day as well as what time. Off, because a window of
    // a few minutes is what this was built for and a date on both ends of that is two thirds of the
    // label saying the same thing. On where the bars cover a model's whole life, which runs to days.
    private boolean showDate;

    public HistogramWidget(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
        panel.addStyleName("trace-histogram");
        initWidget(panel);

        panel.addMouseDownHandler(e -> {
            if (scrubHandler != null && MouseUtil.isPrimary(e.getNativeEvent())) {
                // Held on to while the button is down, so dragging off the end of the plot goes on
                // scrubbing rather than stopping at the edge.
                scrubbing = true;
                Event.setCapture(panel.getElement());
                scrub(e.getClientX());
                e.preventDefault();
                return;
            }
            if (zoomHandler == null || data == null || !data.isAvailable() || !drillable()
                    || !MouseUtil.isPrimary(e.getNativeEvent())) {
                return;
            }
            final Element element = e.getNativeEvent().getEventTarget().cast();
            final Element bar = ElementUtil.findParent(
                    element, el -> el.hasAttribute("data-bucket-index"), 5);
            if (bar != null) {
                final int index = parseIndex(bar.getAttribute("data-bucket-index"));
                if (index >= 0) {
                    final long start = data.getFromMs() + (long) index * data.getBucketWidthMs();
                    final long end = Math.min(data.getToMs(), start + data.getBucketWidthMs() - 1);
                    zoomHandler.accept(start, end);
                }
            }
        });

        panel.addMouseMoveHandler(e -> {
            if (scrubbing) {
                scrub(e.getClientX());
            }
        });
        panel.addMouseUpHandler(e -> release());
        // The pointer can leave the page with the button still down, and a scrub that never ended
        // would follow it back in without being asked.
        panel.addMouseOutHandler(e -> {
            if (scrubbing && e.getRelatedTarget() == null) {
                release();
            }
        });
    }

    private void scrub(final int x) {
        final Long at = timeAt(x);
        if (at != null) {
            // Put where the pointer is before anything is told about it, so the line travels with the
            // hand. Telling first and waiting to be told back would move it a change at a time, which
            // is the hopping this avoids — and the sparser a model's history, the further each hop.
            scrubbedMs = at;
            place(marker());
            scrubHandler.accept(at);
        }
    }

    private void release() {
        if (scrubbing) {
            scrubbing = false;
            Event.releaseCapture(panel.getElement());
        }
    }

    // The server says whether a bucket is worth narrowing to; at its narrowest bucket width the
    // narrowed range comes back as a single bucket, so the click is refused rather than wasted.
    private boolean drillable() {
        return data != null && data.isDrillable();
    }

    public void setEmptyText(final String emptyText) {
        this.emptyText = emptyText;
    }

    public void setShowDate(final boolean showDate) {
        this.showDate = showDate;
    }

    public void setSelectedTime(final Long selectedMs) {
        // While the reader is dragging, the line belongs to the pointer. The selection follows the
        // drag and reports back here a change at a time, and letting that place the line would undo
        // the following.
        if (scrubbing) {
            this.selectedMs = selectedMs;
            return;
        }
        if (Objects.equals(this.selectedMs, selectedMs) && scrubbedMs == null) {
            return;
        }
        this.selectedMs = selectedMs;
        // A selection made anywhere but the plot — a row picked, a step taken, a pathway opened — puts
        // the line back on the change it names.
        scrubbedMs = null;
        // Moved rather than drawn again. Drawing again would put a new line on the plot every time,
        // and a line that has only just appeared has nowhere to slide from — it would jump to each
        // new place instead of travelling there.
        place(marker());
    }

    // The line put where the moment being looked at falls, and taken off the screen where there is no
    // such moment. Always on the plot so there is something to move; nothing is selected far more
    // often than something is, and an empty plot must not carry a line at its left edge.
    private void place(final Element marker) {
        if (marker == null) {
            return;
        }
        final double at = position();
        if (at < 0) {
            marker.getStyle().setProperty("display", "none");
        } else {
            marker.getStyle().setProperty("display", "");
            marker.getStyle().setProperty("left", at + "%");
        }
    }

    // How far across the plot the moment being looked at falls, or less than zero where none is.
    // Measured against the whole span rather than snapped to a bar, so the line stands where the
    // moment actually was and moves by what the reader picked rather than a bar at a time.
    private double position() {
        final Long at = scrubbedMs != null
                ? scrubbedMs
                : selectedMs;
        if (at == null
            || data == null
            || !data.isAvailable()
            || at < data.getFromMs()
            || at > data.getToMs()) {
            return -1D;
        }
        return (at - data.getFromMs()) * 100D / Math.max(1L, data.getToMs() - data.getFromMs());
    }

    private Element marker() {
        return byClass(panel.getElement(), "histogram-marker");
    }

    private static Element byClass(final Element element, final String className) {
        final NodeList<Node> children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.getItem(i);
            if (Element.is(node)) {
                final Element child = Element.as(node);
                if ((" " + child.getAttribute("class") + " ").contains(" " + className + " ")) {
                    return child;
                }
                final Element found = byClass(child, className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public void setZoomHandler(final BiConsumer<Long, Long> zoomHandler) {
        this.zoomHandler = zoomHandler;
    }

    /**
     * Lets the reader drag across the plot, and says where they are as they go. What the line shows is
     * still whatever was last set on it — this reports a moment and leaves whoever is listening to
     * decide what that lands on, so the line follows what is selected rather than the pointer.
     */
    public void setScrubHandler(final Consumer<Long> scrubHandler) {
        this.scrubHandler = scrubHandler;
        if (scrubHandler != null) {
            panel.addStyleName("trace-histogram--scrub");
        } else {
            panel.removeStyleName("trace-histogram--scrub");
        }
    }

    // Where along the plot a pointer at this position falls, as a moment. Measured off the plot rather
    // than the widget, so the labels down the side and along the bottom are not counted as part of it.
    private Long timeAt(final int x) {
        final Element plot = byClass(panel.getElement(), "histogram-plot");
        if (plot == null || data == null || !data.isAvailable() || plot.getOffsetWidth() <= 0) {
            return null;
        }
        final double across = Math.max(0D, Math.min(1D,
                (x - plot.getAbsoluteLeft()) / (double) plot.getOffsetWidth()));
        return data.getFromMs() + Math.round(across * (data.getToMs() - data.getFromMs()));
    }

    public void setData(final TraceHistogram data) {
        // The same histogram draws the same HTML, so skip the repaint. The list re-fetches its
        // histogram on every page turn and it almost always comes back unchanged, the window being a
        // property of the query rather than of the page.
        if (rendered && Objects.equals(this.data, data)) {
            return;
        }
        this.data = data;
        this.rendered = true;
        // Bars over a different stretch of time, so where the reader last left the line says nothing
        // about this one.
        scrubbedMs = null;
        render();
    }

    private void render() {
        if (data == null || !data.isAvailable()) {
            panel.setHTML(hint());
            return;
        }

        final List<Long> counts = data.getCounts();
        long max = 1L;
        for (final Long c : counts) {
            if (c != null && c > max) {
                max = c;
            }
        }

        final long finalMax = max;
        final int n = counts.size();
        final double barWidthPct = 100D / n;
        final HtmlBuilder hb = new HtmlBuilder();

        hb.div(y -> {
            y.div(NumberUtil.formatInt((int) finalMax), Attribute.className("histogram-ylabel"));
            y.div("0", Attribute.className("histogram-ylabel"));
        }, Attribute.className("histogram-yaxis"));

        hb.div(plot -> {
            for (int i = 0; i < n; i++) {
                final long count = counts.get(i) == null ? 0L : counts.get(i);
                final double leftPct = i * barWidthPct;
                final double heightPct = count == 0 ? 0D : count * 100D / finalMax;
                final long start = data.getFromMs() + (long) i * data.getBucketWidthMs();
                final long end = Math.min(data.getToMs(), start + data.getBucketWidthMs());
                final String title = dateTimeFormatter.format(start) + " – "
                        + dateTimeFormatter.format(end) + ": " + count;
                final String minHeight = count > 0 ? " min-height: 3px;" : "";
                plot.div("",
                        Attribute.className("histogram-bar"),
                        Attribute.title(title),
                        new Attribute("data-bucket-index", String.valueOf(i)),
                        Attribute.style("left: " + leftPct + "%; width: " + barWidthPct
                                + "%; height: " + heightPct + "%;" + minHeight));
            }
            // Last, so it stands over the bars rather than behind them. Always there, and put in its
            // place below once the plot exists.
            plot.div("", Attribute.className("histogram-marker"),
                    Attribute.style("display: none;"));
        }, Attribute.className(drillable() ? "histogram-plot" : "histogram-plot histogram-plot--min"));

        // Moment patterns, not Java ones: the formatter takes what it is given and passes it straight
        // through, and the two agree on the time but not the date — Java's dd is the day of the month
        // where moment's is the name of the day.
        final String axisFormat = showDate
                ? "YYYY-MM-DD HH:mm:ss"
                : "HH:mm:ss.SSS";
        hb.div(x -> {
            x.div(dateTimeFormatter.format(data.getFromMs(), axisFormat),
                    Attribute.className("histogram-xlabel"));
            x.div(dateTimeFormatter.format(data.getToMs(), axisFormat),
                    Attribute.className("histogram-xlabel"));
        }, Attribute.className("histogram-xaxis"));

        panel.setHTML(hb.toSafeHtml());
        place(marker());
    }

    private SafeHtml hint() {
        // maxWindowMs is 0 when there is no histogram at all (no data source, or the request failed),
        // so there is no limit to name.
        final long maxWindowMs = data == null ? 0L : data.getMaxWindowMs();
        final String text = emptyText != null
                ? emptyText
                : maxWindowMs > 0
                        ? "Select a time range of "
                          + ModelStringUtil.formatDurationString(maxWindowMs, true)
                          + " or less to view the trace histogram"
                        : "Select a narrower time range to view the trace histogram";
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(h -> h.append(text), Attribute.className("histogram-hint"));
        return hb.toSafeHtml();
    }

    private static int parseIndex(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }
}
