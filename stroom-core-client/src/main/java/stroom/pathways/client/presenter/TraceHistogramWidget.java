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
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A compact bar strip showing trace counts per equal time-bucket over the selected window, rendered
 * as absolutely-positioned percentage-height divs. Clicking a bar zooms the time range to that
 * bucket via the registered handler.
 */
public class TraceHistogramWidget extends Composite {

    private final HTML panel = new HTML();
    private final DateTimeFormatter dateTimeFormatter;
    private TraceHistogram data;
    private BiConsumer<Long, Long> zoomHandler;

    public TraceHistogramWidget(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
        panel.addStyleName("trace-histogram");
        initWidget(panel);

        panel.addMouseDownHandler(e -> {
            if (zoomHandler == null || data == null || !data.isAvailable()
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
                    // Half-open: end at the last ms still inside this bucket, so drilling doesn't also
                    // catch the next bucket's leading ms (the server buckets [start, start+width)).
                    final long end = Math.min(data.getToMs(), start + data.getBucketWidthMs() - 1);
                    zoomHandler.accept(start, end);
                }
            }
        });
    }

    public void setZoomHandler(final BiConsumer<Long, Long> zoomHandler) {
        this.zoomHandler = zoomHandler;
    }

    public void setData(final TraceHistogram data) {
        this.data = data;
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

        final int n = counts.size();
        final double barWidthPct = 100D / n;
        final HtmlBuilder hb = new HtmlBuilder();
        for (int i = 0; i < n; i++) {
            final long count = counts.get(i) == null ? 0L : counts.get(i);
            final double leftPct = i * barWidthPct;
            final double heightPct = count * 100D / max;
            final long start = data.getFromMs() + (long) i * data.getBucketWidthMs();
            final long end = Math.min(data.getToMs(), start + data.getBucketWidthMs());
            final String title = dateTimeFormatter.format(start) + " – "
                    + dateTimeFormatter.format(end) + ": " + count;
            hb.div("",
                    Attribute.className("histogram-bar"),
                    Attribute.title(title),
                    new Attribute("data-bucket-index", String.valueOf(i)),
                    Attribute.style("left: " + leftPct + "%; width: " + barWidthPct
                            + "%; height: " + heightPct + "%;"));
        }
        panel.setHTML(hb.toSafeHtml());
    }

    private SafeHtml hint() {
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(h -> h.append("Select a narrower time range to view the trace histogram"),
                Attribute.className("histogram-hint"));
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
