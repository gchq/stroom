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

import stroom.pathways.shared.TraceHistogram;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.NumberUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A compact bar strip showing trace counts per equal time-bucket over the selected window, rendered
 * as absolutely-positioned percentage-height divs. Clicking a bar zooms the time range to that
 * bucket via the registered handler.
 * <p>
 * The BEM block {@code trace-histogram-chart} ({@code trace-histogram-chart.css}). Every bar is as wide as the
 * others ({@code --trace-histogram-chart__bar-width}, on the plot); each has its place and height
 * ({@code --trace-histogram-chart__bar-index}, {@code --trace-histogram-chart__bar-height}).
 */
public class TraceHistogramChart extends Composite {

    private static final String BLOCK = "trace-histogram-chart";

    private final HTML panel = new HTML();
    private DateTimeFormatter dateTimeFormatter;
    private TraceHistogram data;
    private boolean rendered;
    private BiConsumer<Long, Long> onZoom;

    public TraceHistogramChart() {
        panel.addStyleName(BLOCK);
        initWidget(panel);

        panel.addMouseDownHandler(e -> {
            if (onZoom == null || data == null || !data.isAvailable() || !drillable()
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
                    onZoom.accept(start, end);
                }
            }
        });
    }

    // The server says whether a bucket is worth narrowing to; at its narrowest bucket width the
    // narrowed range comes back as a single bucket, so the click is refused rather than wasted.
    private boolean drillable() {
        return data != null && data.isDrillable();
    }

    /// @param dateTimeFormatter Formats the times of the bars' tooltips and the axis, in the user's
    ///                          preferred form. Set before the data.
    public void setDateTimeFormatter(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /// @param onZoom Told the start and end of a bar's time when the user clicks it, to narrow the
    ///               time range to it, or null for nothing.
    public void setOnZoom(final BiConsumer<Long, Long> onZoom) {
        this.onZoom = onZoom;
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
        final String plotClass = drillable()
                ? BLOCK + "__plot"
                : BLOCK + "__plot " + BLOCK + "__plot--min";
        final HtmlBuilder hb = new HtmlBuilder();

        hb.div(y -> {
            y.div(NumberUtil.formatInt((int) finalMax), Attribute.className(BLOCK + "__y-label"));
            y.div("0", Attribute.className(BLOCK + "__y-label"));
        }, Attribute.className(BLOCK + "__y-axis"));

        hb.div(plot -> {
            for (int i = 0; i < n; i++) {
                final long count = counts.get(i) == null ? 0L : counts.get(i);
                final double heightPct = count == 0 ? 0D : count * 100D / finalMax;
                final long start = data.getFromMs() + (long) i * data.getBucketWidthMs();
                final long end = Math.min(data.getToMs(), start + data.getBucketWidthMs());
                final String title = dateTimeFormatter.format(start) + " – "
                        + dateTimeFormatter.format(end) + ": " + count;
                // A bar with any traces can be seen however few it has
                final String barClass = count > 0
                        ? BLOCK + "__bar " + BLOCK + "__bar--filled"
                        : BLOCK + "__bar";
                plot.div("",
                        Attribute.className(barClass),
                        Attribute.title(title),
                        new Attribute("data-bucket-index", String.valueOf(i)),
                        Attribute.style("--trace-histogram-chart__bar-index: " + i
                                + "; --trace-histogram-chart__bar-height: " + heightPct + "%"));
            }
        }, Attribute.className(plotClass),
                Attribute.style("--trace-histogram-chart__bar-width: " + barWidthPct + "%"));

        hb.div(x -> {
            x.div(dateTimeFormatter.format(data.getFromMs(), "HH:mm:ss.SSS"),
                    Attribute.className(BLOCK + "__x-label"));
            x.div(dateTimeFormatter.format(data.getToMs(), "HH:mm:ss.SSS"),
                    Attribute.className(BLOCK + "__x-label"));
        }, Attribute.className(BLOCK + "__x-axis"));

        panel.setHTML(hb.toSafeHtml());
    }

    private SafeHtml hint() {
        // maxWindowMs is 0 when there is no histogram at all (no data source, or the request failed),
        // so there is no limit to name.
        final long maxWindowMs = data == null ? 0L : data.getMaxWindowMs();
        final String text = maxWindowMs > 0
                ? "Select a time range of " + ModelStringUtil.formatDurationString(maxWindowMs, true)
                  + " or less to view the trace histogram"
                : "Select a narrower time range to view the trace histogram";
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(h -> h.append(text), Attribute.className(BLOCK + "__hint"));
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
