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

package stroom.widget.histogram.client;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import java.util.function.Consumer;

/**
 * A histogram widget that renders on {@code int[]} of bin counts as vertical bars on an HTML5 Canvas.
 * <p>
 *     Features:
 *     <ul>
 *         <li>Normalised bar heights (tallest bin fills the widget height)</li>
 *         <li>Hover tooltip showing per-bin count</li>
 *         <li>Click callback with the clicked percentage [0..1]</li>
 *         <li>Tick marks at regular intervals.</li>
 *         <li>Placeholder text when all bins are zero.</li>
 *     </ul>
 * </p>
 */
public class HistogramWidget extends Composite {

    /**
     * Height of the widget in pixels.
     *
     * <p>Must stay in step with the {@code height: 48px} in
     * {@code stroom-histogram.css}: CSS lays the canvas element out, while this
     * sizes its coordinate space. If the two disagree the browser scales the
     * drawing to fit, so the bars stretch or squash rather than looking wrong in
     * an obvious way.</p>
     */
    private static final int HEIGHT_PX = 48;

    /** Shown in place of bars when every bin is zero. */
    private static final String EMPTY_MESSAGE = "No events in this time range";

    /**
     * CSS custom properties holding the bar colours, defined per theme in
     * {@code stroom-histogram.css} and resolved at draw time by
     * {@link #themeColour}.
     */
    private static final String BAR_COLOUR_PROPERTY = "--histogram-bar__color";
    private static final String PEAK_COLOUR_PROPERTY = "--histogram-peak__color";

    /**
     * Used when the CSS custom property cannot be resolved — an unstyled test
     * harness, or a consumer whose page has not loaded {@code stroom-histogram.css}.
     * Kept in step with the light theme's value in {@code stroom-histogram.css}, so a
     * missing stylesheet degrades to the styled appearance rather than to invisible bars.
     */
    private static final String DEFAULT_BAR_COLOUR = "rgba(21,101,192,0.8)";
    private static final String DEFAULT_PEAK_COLOUR = "rgba(21,101,192,1.0)";
    private static final String TICK_COLOUR = "rgba(128,128,128,0.25)";
    private static final int TARGET_TICK_COUNT = 10;

    private final FlowPanel container;
    private final Canvas canvas;
    private final Label tooltip;
    private int[] bins;
    // Where each bin belongs across the widget; never null once data has been set.
    private HistogramLayout layout = HistogramLayout.even(1);

    // Called when the user clicks, with the fractional position [0..1]
    private Consumer<Double> clickHandler;

    public HistogramWidget() {
        container = new FlowPanel();
        container.addStyleName("stroom-histogram-container");

        canvas = Canvas.createIfSupported();
        if (canvas != null) {
            canvas.addStyleName("stroom-histogram-canvas");
            // Canvas pixels are opaque to assistive technology: without a role and
            // a name the whole distribution — and the per-bin counts the hover
            // tooltip reveals — simply do not exist for a screen-reader user.
            // role="img" plus a generated aria-label is the standard text
            // alternative for a chart that is a picture of its data rather than a
            // set of controls; updateTextAlternative() keeps the label current.
            canvas.getElement().setAttribute("role", "img");
            updateTextAlternative();

            tooltip = new Label();
            tooltip.addStyleName("stroom-histogram-tooltip");

            final FlowPanel wrapper = new FlowPanel();
            wrapper.addStyleName("stroom-histogram-wrapper");
            wrapper.add(canvas);
            wrapper.add(tooltip);
            container.add(wrapper);

            canvas.addDomHandler(this::onMouseMove, MouseMoveEvent.getType());
            //noinspection unused event
            canvas.addDomHandler(event -> hideTooltip(), MouseOutEvent.getType());
            canvas.addDomHandler(this::onClick, ClickEvent.getType());
            // WCAG 1.4.13 requires hover content to be dismissible without moving
            // the pointer. Bound on the container rather than the canvas because
            // the canvas is not focusable, so it never receives a key event; the
            // container sees it bubble up from whatever does have focus.
            container.addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        } else {
            tooltip = null;
        }

        initWidget(container);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Sets the bin data, drawn as an even division of the width, and triggers a redraw. */
    public void setData(final int[] binCounts) {
        setData(binCounts, null);
    }

    /// Sets the bin data and where each bin belongs across the widget, and triggers a redraw.
    ///
    /// Bucketed counts need the layout: their first and last bins hang over the edges of the
    /// visible range, so an even division of the width draws every bar away from its own time.
    ///
    /// @param binCounts the count in each bin
    /// @param layout    where each bin belongs, or `null` for an even division of the width
    public void setData(final int[] binCounts, final HistogramLayout layout) {
        this.bins = binCounts;
        this.layout = layout != null
                ? layout
                : HistogramLayout.even(binCounts == null ? 1 : binCounts.length);
        updateTextAlternative();
        Scheduler.get().scheduleDeferred(this::draw);
    }

    /** Sets the click handler - receives the fractional position [0..1]. */
    public void setClickHandler(final Consumer<Double> handler) {
        this.clickHandler = handler;
    }

    /**
     * Redraws with the current data, picking up any theme change.
     *
     * <p>Needed because the bar colours are baked into canvas pixels at draw time:
     * unlike the rest of this widget, which is styled by CSS and re-styles itself
     * when the theme class changes, the bars keep whatever colour they were painted
     * until something repaints them. Callers that live long enough to see a theme
     * change should call this from a
     * {@code ChangeCurrentPreferencesEvent} handler.</p>
     */
    public void redraw() {
        // Deferred for the same reason setData() defers: draw() needs the container
        // to have been laid out before it can read a width.
        Scheduler.get().scheduleDeferred(this::draw);
    }

    /**
     * Resolves a CSS custom property to a concrete colour for the canvas context.
     *
     * <p>A canvas {@code fillStyle} takes a colour <em>value</em>; {@code var()} and
     * {@code currentColor} are CSS-level constructs and mean nothing to it. So the
     * property has to be read off the computed style — from the canvas element
     * itself, so that custom properties inherit down from whichever ancestor
     * carries the theme class.</p>
     *
     * @param property the custom property name, e.g. {@code --histogram-bar__color}
     * @param fallback returned when the property is absent or empty
     */
    private String themeColour(final String property, final String fallback) {
        if (canvas == null) {
            return fallback;
        }
        final String value = computedPropertyValue(canvas.getElement(), property);
        return value != null && !value.trim().isEmpty()
                ? value.trim()
                : fallback;
    }

    /**
     * Reads one computed CSS property (custom properties included) off an element.
     *
     * <p>Native because GWT's {@code Style} exposes only inline styles, and a custom
     * property set in a stylesheet is never inline. Guarded rather than trusting the
     * environment: {@code getComputedStyle} returns null for a detached element in
     * some browsers, which would otherwise throw during an early draw.</p>
     */
    private static native String computedPropertyValue(final Element element,
                                                       final String property) /*-{
        var view = element.ownerDocument && element.ownerDocument.defaultView;
        if (!view || !view.getComputedStyle) {
            return null;
        }
        var computed = view.getComputedStyle(element, null);
        return computed ? computed.getPropertyValue(property) : null;
    }-*/;


    /**
     * Names the canvas after what it is currently showing.
     *
     * <p>Describes the <em>shape</em> of the distribution — total, peak, and where
     * the peak falls — rather than reading out every bin. A bin-by-bin list would
     * be a faithful transcription and useless to listen to; "busiest around 60%
     * of the way through" is the thing the picture actually communicates at a
     * glance, and it is what a sighted user takes from it before deciding where
     * to seek.</p>
     *
     * <p>Position is given as a percentage rather than a time because this widget
     * is deliberately time-agnostic: it is handed bare bin counts and has no idea
     * what range they span. The timeline's own start/end labels supply that.</p>
     */
    private void updateTextAlternative() {
        if (canvas == null) {
            return;
        }
        canvas.getElement().setAttribute("aria-label", describeDistribution());
    }

    /** Builds the summary used as the canvas's accessible name. */
    private String describeDistribution() {
        if (bins == null || bins.length == 0) {
            // Not "no events" — nothing has been loaded yet, which is a different
            // statement and one a user should not be told is an empty result.
            return "Event distribution over time: no data loaded";
        }

        int total = 0;
        int max = 0;
        int peakIndex = 0;
        for (int i = 0; i < bins.length; i++) {
            total += bins[i];
            if (bins[i] > max) {
                max = bins[i];
                peakIndex = i;
            }
        }

        if (max == 0) {
            return "Event distribution over time: " + EMPTY_MESSAGE;
        }

        // Midpoint of the peak bin, so a single-bin histogram reads as 50% rather
        // than 0% — the bar spans the whole width, and its left edge is not where
        // the events are. Clamped because an edge bin can hang over the range.
        final double peakMid = (layout.barStart(peakIndex) + layout.barEnd(peakIndex)) / 2;
        final int peakPct = (int) Math.round(Math.max(0, Math.min(1, peakMid)) * 100.0);

        return "Event distribution over time: " + total + " events in "
                + bins.length + " intervals, busiest interval has " + max
                + ", about " + peakPct + "% of the way through the range";
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    /** Draws the histogram bars and tick marks onto the canvas. Called whenever new data arrives. */
    private void draw() {
        if (canvas  == null || bins == null || bins.length == 0) {
            return;
        }

        // Sync the canvas pixel dimensions to its CSS layout dimensions.
        final int width = container.getOffsetWidth();
        if (width <= 0) {
            return;
        }

        canvas.setCoordinateSpaceWidth(width);
        canvas.setCoordinateSpaceHeight(HEIGHT_PX);

        final Context2d ctx = canvas.getContext2d();
        ctx.clearRect(0, 0, width, HEIGHT_PX);

        // Find peak count for normalisation.
        int max = 0;
        for (final int count : bins) {
            if (count > max) {
                max = count;
            }
        }
        if (max == 0) {
            // A query has run but returned no events for this time range.
            // Draw a centred placeholder so the user knows what the space is for.
            ctx.save();
            ctx.setFont("italic 11px sans-serif");
            ctx.setTextAlign(Context2d.TextAlign.CENTER);
            ctx.setTextBaseline(Context2d.TextBaseline.MIDDLE);
            ctx.setFillStyle("rgba(128,128,128,0.45)");
            ctx.fillText(EMPTY_MESSAGE, width / 2.0, HEIGHT_PX / 2.0);
            ctx.restore();
            return;
        }

        // ---- Draw histogram bars ----
        final int n = bins.length;

        // Resolved once per draw rather than per bar: reading a computed style
        // forces the browser to flush pending style work, so doing it inside the
        // loop would pay that cost on every bar of every frame.
        final String barFill = themeColour(BAR_COLOUR_PROPERTY, DEFAULT_BAR_COLOUR);
        final String peakFill = themeColour(PEAK_COLOUR_PROPERTY, DEFAULT_PEAK_COLOUR);

        for (int i = 0; i < n; i++) {
            final double barH = ((double) bins[i] / max) * (HEIGHT_PX - 2);
            if (barH < 1) {
                continue;
            }
            // Placed by the layout and clipped to the canvas: the first and last bars can hang
            // over the edges of the visible range.
            final double x = Math.max(0, layout.barStart(i) * width);
            final double right = Math.min(width, layout.barEnd(i) * width);
            if (right <= x) {
                continue;
            }
            final double y = HEIGHT_PX - barH;

            // Use a brighter colour for the peak bin.
            ctx.setFillStyle(bins[i] == max ? peakFill : barFill);
            ctx.fillRect(x, y, Math.max(1, right - x - 1), barH);
        }

        // ---- Draw tick marks at regular intervals ----
        // Calculate a tick interval so ticks align with even bin boundaries.
        final int tickInterval = tickInterval(n);
        ctx.setFillStyle(TICK_COLOUR);
        for (int i = tickInterval; i < n; i += tickInterval) {
            final double x = layout.barStart(i) * width;
            if (x > 0 && x < width) {
                ctx.fillRect(x, 0, 1, HEIGHT_PX);
            }
        }
    }

    /**
     * Returns a tick interval (in bins) that divides {@code binCount} into approximately
     * {@code targetCount} evenly-spaced ticks, rounded to a "nice" number (1, 2, 5, 10, 20…).
     */
    private static int tickInterval(final int binCount) {
        final int raw = Math.max(1, binCount / TARGET_TICK_COUNT);
        // Round up to the nearest step: 1, 2, 5, 10, 20, 25, 50, 100…
        final int[] interval = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500};
        for (final int n : interval) {
            if (n >= raw) {
                return n;
            }
        }
        return raw;
    }

    // -----------------------------------------------------------------------
    // Interaction
    // -----------------------------------------------------------------------

    private void onMouseMove(final MouseMoveEvent event) {
        if (bins == null || bins.length == 0 || tooltip == null) {
            return;
        }
        final int containerWidth = container.getOffsetWidth();
        if (containerWidth <= 0) {
            return;
        }
        final int relX = event.getX();
        final int binIndex = layout.barAt(relX / (double) containerWidth, bins.length);
        final int count = bins[binIndex];

        tooltip.setText(count + " event" + (count == 1 ? "" : "s"));

        // Position the tooltip horizontally centred on the cursor, within the canvas bounds.
        final int tooltipW = tooltip.getOffsetWidth();
        final int clampedX = Math.max(0, Math.min(containerWidth - tooltipW, relX - tooltipW / 2));
        tooltip.getElement().getStyle().setLeft(clampedX, Unit.PX);
        tooltip.addStyleName("stroom-histogram-tooltip--visible");
    }

    private void hideTooltip() {
        if (tooltip != null) {
            tooltip.removeStyleName("stroom-histogram-tooltip--visible");
        }
    }

    /**
     * Dismisses the hover tooltip on Escape.
     *
     * <p>Deliberately does not consume the event: Escape usually also means
     * "close the dialog or popup I am in", and swallowing it here would strand a
     * user inside whatever contains the histogram.</p>
     */
    private void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            hideTooltip();
        }
    }

    private void onClick(final ClickEvent event) {
        if (clickHandler == null) {
            return;
        }

        final int w = container.getOffsetWidth();
        if (w <= 0) {
            return;
        }

        clickHandler.accept(Math.max(0, Math.min(1.0, event.getX() / (double) w)));
    }
}
