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

package stroom.ai.client;

import stroom.ai.client.AiGanttChart.Scale;
import stroom.ai.shared.MermaidGantt;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The browser's side of zooming the chat's charts ([AiMermaidCharts], [AiGanttChart]): a drag across a chart's
/// plot or axis zooms it to the span dragged (a band shown while dragging; Escape cancels), a double-click on the
/// plot or its "Reset zoom" button shows the whole period again, and its "Maximise" button is passed on to be shown
/// in a dialog. A click on a bar, a marker or a legend item (or Enter or Space on a legend item) picks its name out:
/// its bars highlighted, the others dimmed; a click on it again, or on the chart's background, clears it. A click
/// isn't a drag: a drag too short to zoom is a click. The maths are [AiGanttZoom]'s.
///
/// The charts are HTML inserted into the conversation (and drawn again as answers are), so nothing is bound to
/// them: the handlers are on the element that holds them (the conversation, or the dialog), delegated by the event's
/// target, and released with the registrations [#bind] returns. A drag listens to the page (a native preview
/// handler) only from its mouse down to its mouse up. A chart is drawn again from the text it keeps, so its state
/// is only its attributes and goes with it when it is replaced.
final class AiGanttInteraction {

    // How far up from an event's target a chart's element or SVG may be.
    private static final int MAX_DEPTH = 10;
    private static final String VISIBILITY = "visibility";

    private final Consumer<Element> onMaximise;
    private HandlerRegistration drag;
    // True from a drag's zoom until the click its mouse up gives, which isn't a pick.
    private boolean zoomed;

    /// @param onMaximise Shows a chart (its element) in a larger dialog.
    AiGanttInteraction(final Consumer<Element> onMaximise) {
        this.onMaximise = onMaximise;
    }

    /// Handles the charts in a container.
    ///
    /// @param container The element the charts are in, e.g. the conversation.
    /// @return The handlers' registrations, to be removed with the container's owner (e.g. by a presenter's
    /// `registerHandler`).
    List<HandlerRegistration> bind(final Widget container) {
        final List<HandlerRegistration> registrations = new ArrayList<>();
        registrations.add(container.addDomHandler(this::onMouseDown, MouseDownEvent.getType()));
        registrations.add(container.addDomHandler(this::onDoubleClick, DoubleClickEvent.getType()));
        registrations.add(container.addDomHandler(this::onClick, ClickEvent.getType()));
        registrations.add(container.addDomHandler(this::onKeyDown, KeyDownEvent.getType()));
        registrations.add(() -> endDrag(null));
        return registrations;
    }

    /// Draws a chart again, in place.
    ///
    /// @param chart Its element (of [AiMermaidCharts#CHART_CLASS]).
    /// @param view  The span to show, or null for the whole period.
    /// @param width The width to draw it at.
    static void draw(final Element chart, final long[] view, final int width) {
        final MermaidGantt gantt = AiMermaidCharts.parse(chart.getAttribute(AiMermaidCharts.SOURCE_ATTRIBUTE));
        final Element plot = ElementUtil.findChild(chart, AiMermaidCharts.PLOT_CLASS);
        if (gantt == null || plot == null) {
            return;
        }
        // The same escaped SVG as the chart was first drawn with, of the text it was drawn from.
        plot.setInnerHTML(AiGanttChart.svg(gantt, view, width, highlightOf(chart)));
        if (view == null) {
            chart.removeAttribute(AiMermaidCharts.VIEW_ATTRIBUTE);
        } else {
            chart.setAttribute(AiMermaidCharts.VIEW_ATTRIBUTE, AiGanttZoom.toAttribute(view));
        }
        final Element reset = ElementUtil.findChild(chart, AiMermaidCharts.RESET_CLASS);
        if (reset != null) {
            if (view == null) {
                reset.setAttribute("hidden", "");
            } else {
                reset.removeAttribute("hidden");
            }
        }
    }

    /// Draws each chart in an element again at the width it has room for ([AiGanttChart#fitWidth]), keeping the
    /// span it shows; one already drawn at that width, or with no room known, is left as it is.
    ///
    /// @param root The element the charts are in, e.g. the conversation or one message.
    static void fit(final Element root) {
        if (root == null) {
            return;
        }
        final NodeList<Element> charts = charts(root, "." + AiMermaidCharts.CHART_CLASS);
        for (int i = 0; i < charts.getLength(); i++) {
            final Element chart = charts.getItem(i);
            final int width = AiGanttChart.fitWidth(chart.getClientWidth());
            if (width > 0 && width != widthOf(chart)) {
                chart.setAttribute(AiMermaidCharts.WIDTH_ATTRIBUTE, String.valueOf(width));
                draw(chart, viewOf(chart), width);
            }
        }
    }

    /// @param chart A chart's element.
    /// @return The series picked out in it (a colour's class), or null for none.
    static String highlightOf(final Element chart) {
        final String highlight = chart.getAttribute(AiMermaidCharts.HIGHLIGHT_ATTRIBUTE);
        return highlight == null || highlight.isEmpty()
                ? null
                : highlight;
    }

    /// @param chart A chart's element.
    /// @return The span it shows, or null for the whole period.
    static long[] viewOf(final Element chart) {
        return AiGanttZoom.fromAttribute(chart.getAttribute(AiMermaidCharts.VIEW_ATTRIBUTE));
    }

    /// @param chart A chart's element.
    /// @return The width it is drawn at.
    static int widthOf(final Element chart) {
        final String width = chart.getAttribute(AiMermaidCharts.WIDTH_ATTRIBUTE);
        if (width == null || width.isEmpty()) {
            return AiGanttChart.WIDTH;
        }
        try {
            return Integer.parseInt(width);
        } catch (final NumberFormatException e) {
            return AiGanttChart.WIDTH;
        }
    }

    private void onMouseDown(final MouseDownEvent event) {
        if (!MouseUtil.isPrimary(event)) {
            return;
        }
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element svg = findSvg(target);
        final Element chart = findChart(svg);
        if (chart == null) {
            return;
        }
        final double x = svgX(svg, event.getClientX());
        final double y = svgY(svg, event.getClientY());
        if (!isInPlot(svg, x, y)) {
            return;
        }
        // No text selected (or dragged) by a drag across the plot.
        event.preventDefault();
        startDrag(chart, svg, x);
    }

    private void onDoubleClick(final DoubleClickEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element svg = findSvg(target);
        final Element chart = findChart(svg);
        if (chart != null && viewOf(chart) != null
            && isInPlot(svg, svgX(svg, event.getClientX()), svgY(svg, event.getClientY()))) {
            event.preventDefault();
            draw(chart, null, widthOf(chart));
        }
    }

    private void onClick(final ClickEvent event) {
        final boolean afterZoom = zoomed;
        zoomed = false;
        if (!MouseUtil.isPrimary(event)) {
            return;
        }
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element button = ElementUtil.findParent(target, element ->
                element.getTagName().equalsIgnoreCase("button"), 4);
        if (button == null) {
            if (!afterZoom) {
                pick(target, false);
            }
            return;
        }
        final Element chart = ElementUtil.findParent(button, element ->
                element.hasAttribute(AiMermaidCharts.SOURCE_ATTRIBUTE), 4);
        if (chart == null) {
            return;
        }
        if (button.hasClassName(AiMermaidCharts.RESET_CLASS)) {
            draw(chart, null, widthOf(chart));
            // The button is hidden once the whole period is shown, so the focus goes to the chart's next button.
            final Element maximise = ElementUtil.findChild(chart, AiMermaidCharts.MAXIMISE_CLASS);
            if (maximise != null) {
                maximise.focus();
            }
        } else if (button.hasClassName(AiMermaidCharts.MAXIMISE_CLASS)) {
            onMaximise.accept(chart);
        }
    }

    private void onKeyDown(final KeyDownEvent event) {
        final int key = event.getNativeKeyCode();
        if (key != KeyCodes.KEY_ENTER && key != KeyCodes.KEY_SPACE) {
            return;
        }
        final Element target = event.getNativeEvent().getEventTarget().cast();
        // A legend item is known by its attributes: an SVG element's className isn't a string in the browser.
        if (target != null && target.hasAttribute(AiGanttChart.SERIES_ATTRIBUTE)
            && "button".equals(target.getAttribute("role")) && pick(target, true)) {
            // Not a scroll of the conversation (Space), nor a press of anything else.
            event.preventDefault();
        }
    }

    // Picks out the name of what was clicked (a bar, a marker or a legend item), or clears what is picked out for
    // a click on the chart's background. True if a chart was drawn again.
    private static boolean pick(final Element target, final boolean fromKeyboard) {
        final Element svg = findSvg(target);
        final Element chart = findChart(svg);
        if (chart == null) {
            return false;
        }
        final Element item = ElementUtil.findParent(target, element ->
                element.hasAttribute(AiGanttChart.SERIES_ATTRIBUTE), 3);
        final String series = item == null
                ? null
                : item.getAttribute(AiGanttChart.SERIES_ATTRIBUTE);
        final String current = highlightOf(chart);
        final String picked = AiGanttChart.toggled(current, series);
        if (picked == null && current == null) {
            return false;
        }
        if (picked == null) {
            chart.removeAttribute(AiMermaidCharts.HIGHLIGHT_ATTRIBUTE);
        } else {
            chart.setAttribute(AiMermaidCharts.HIGHLIGHT_ATTRIBUTE, picked);
        }
        draw(chart, viewOf(chart), widthOf(chart));
        if (fromKeyboard && series != null) {
            // Drawn again, the legend item is new: the focus goes back to it.
            final Element again = legendItem(chart, series);
            if (again != null) {
                again.focus();
            }
        }
        return true;
    }

    private void startDrag(final Element chart, final Element svg, final double startX) {
        endDrag(null);
        final Element band = ElementUtil.findChild(svg, AiGanttChart.BAND_CLASS);
        drag = Event.addNativePreviewHandler(preview -> {
            final NativeEvent nativeEvent = preview.getNativeEvent();
            switch (preview.getTypeInt()) {
                case Event.ONMOUSEMOVE -> {
                    showBand(svg, band, startX, svgX(svg, nativeEvent.getClientX()));
                    preview.cancel();
                }
                case Event.ONMOUSEUP -> {
                    endDrag(band);
                    final long[] range = AiGanttZoom.dragRange(scaleOf(svg), startX,
                            svgX(svg, nativeEvent.getClientX()));
                    // Not if the chart was drawn again (its answer re-rendered) during the drag.
                    if (range != null && Document.get().getBody().isOrHasChild(svg)) {
                        draw(chart, range, widthOf(chart));
                        // The click this mouse up gives is the drag's, not a pick.
                        zoomed = true;
                    }
                }
                case Event.ONKEYDOWN -> {
                    if (nativeEvent.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                        endDrag(band);
                        preview.cancel();
                    }
                }
                default -> {
                    // Nothing else is affected.
                }
            }
        });
    }

    private void endDrag(final Element band) {
        if (drag != null) {
            drag.removeHandler();
            drag = null;
        }
        if (band != null) {
            band.setAttribute(VISIBILITY, "hidden");
        }
    }

    private static void showBand(final Element svg, final Element band, final double startX, final double x) {
        if (band == null) {
            return;
        }
        final Scale scale = scaleOf(svg);
        final double left = Math.max(scale.left(), Math.min(startX, x));
        final double right = Math.min(scale.right(), Math.max(startX, x));
        band.setAttribute("x", String.valueOf(left));
        band.setAttribute("width", String.valueOf(Math.max(0, right - left)));
        band.setAttribute(VISIBILITY, right - left >= AiGanttZoom.MIN_DRAG_PIXELS
                ? "visible"
                : "hidden");
    }

    private static Element findSvg(final Element target) {
        return target == null
                ? null
                : ElementUtil.findParent(target, element ->
                        element.hasAttribute(AiGanttChart.PLOT_LEFT_ATTRIBUTE), MAX_DEPTH);
    }

    private static Element findChart(final Element svg) {
        return svg == null
                ? null
                : ElementUtil.findParent(svg, element ->
                        element.hasAttribute(AiMermaidCharts.SOURCE_ATTRIBUTE), 3);
    }

    // True if a point (in the chart's pixels) is over the plot or its axis.
    private static boolean isInPlot(final Element svg, final double x, final double y) {
        return x >= number(svg, AiGanttChart.PLOT_LEFT_ATTRIBUTE)
               && x <= number(svg, AiGanttChart.PLOT_RIGHT_ATTRIBUTE)
               && y >= number(svg, AiGanttChart.PLOT_TOP_ATTRIBUTE)
               && y <= number(svg, AiGanttChart.PLOT_BOTTOM_ATTRIBUTE);
    }

    private static Scale scaleOf(final Element svg) {
        return new Scale(
                Long.parseLong(svg.getAttribute(AiGanttChart.FROM_ATTRIBUTE)),
                Long.parseLong(svg.getAttribute(AiGanttChart.TO_ATTRIBUTE)),
                number(svg, AiGanttChart.PLOT_LEFT_ATTRIBUTE),
                number(svg, AiGanttChart.PLOT_RIGHT_ATTRIBUTE));
    }

    private static double svgX(final Element svg, final int clientX) {
        return AiGanttZoom.toSvg(clientX, boxLeft(svg), boxWidth(svg), number(svg, AiGanttChart.WIDTH_ATTRIBUTE));
    }

    private static double svgY(final Element svg, final int clientY) {
        return AiGanttZoom.toSvg(clientY, boxTop(svg), boxHeight(svg), number(svg, AiGanttChart.HEIGHT_ATTRIBUTE));
    }

    private static double number(final Element svg, final String attribute) {
        try {
            return Double.parseDouble(svg.getAttribute(attribute));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    // A chart's legend item of a series, or null.
    private static Element legendItem(final Element chart, final String series) {
        final NodeList<Element> items = charts(chart, "." + AiGanttChart.LEGEND_ITEM_CLASS);
        for (int i = 0; i < items.getLength(); i++) {
            if (series.equals(items.getItem(i).getAttribute(AiGanttChart.SERIES_ATTRIBUTE))) {
                return items.getItem(i);
            }
        }
        return null;
    }

    private static native NodeList<Element> charts(Element root, String selector) /*-{
        return root.querySelectorAll(selector);
    }-*/;

    private static native double boxLeft(Element element) /*-{
        return element.getBoundingClientRect().left;
    }-*/;

    private static native double boxTop(Element element) /*-{
        return element.getBoundingClientRect().top;
    }-*/;

    private static native double boxWidth(Element element) /*-{
        return element.getBoundingClientRect().width;
    }-*/;

    private static native double boxHeight(Element element) /*-{
        return element.getBoundingClientRect().height;
    }-*/;
}
