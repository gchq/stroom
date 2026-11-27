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
import stroom.ai.shared.MermaidGantt.Task;

import java.util.ArrayList;
import java.util.List;

/// The maths of zooming a chart drawn by [AiGanttChart]: where a drag across it falls in time, which tasks a span
/// shows and how much of each, and the span written on the chart's element so it can be drawn again. Plain Java, so
/// it can be tested; the browser's side (the mouse, the buttons) is [AiGanttInteraction].
final class AiGanttZoom {

    /// The least a drag must cover, in the chart's pixels, to zoom: less is a click.
    static final double MIN_DRAG_PIXELS = 5;
    /// The shortest span a chart zooms to: the axis's least step.
    static final long MIN_SPAN = 1_000L;

    private AiGanttZoom() {
        // Static only.
    }

    /// @param clientX   Where the pointer is, across the page.
    /// @param boxLeft   Where the chart's SVG starts on the page.
    /// @param boxWidth  How wide it is shown on the page (scaled to fit the chat, so not always as drawn).
    /// @param svgWidth  How wide it is drawn (its `viewBox`).
    /// @return Where the pointer is in the chart's own pixels.
    static double toSvg(final double clientX, final double boxLeft, final double boxWidth, final double svgWidth) {
        if (boxWidth <= 0) {
            return clientX - boxLeft;
        }
        return (clientX - boxLeft) * svgWidth / boxWidth;
    }

    /// @param scale Where times are drawn.
    /// @param x     A position across the chart, in its pixels.
    /// @return The time drawn there, held to the scale's span when `x` is outside the plot.
    static long timeAt(final Scale scale, final double x) {
        final double clamped = Math.max(scale.left(), Math.min(scale.right(), x));
        final double fraction = (clamped - scale.left()) / (scale.right() - scale.left());
        return scale.from() + Math.round(fraction * (scale.to() - scale.from()));
    }

    /// @param scale  Where times are drawn now (the whole period, or a span already zoomed to).
    /// @param startX Where a drag started, in the chart's pixels.
    /// @param endX   Where it ended.
    /// @return The span the drag covered, earlier time first, held to the scale's span and at least [#MIN_SPAN]
    /// long; or null when it covered less than [#MIN_DRAG_PIXELS] of the plot (a click, not a zoom).
    static long[] dragRange(final Scale scale, final double startX, final double endX) {
        final double left = Math.max(scale.left(), Math.min(startX, endX));
        final double right = Math.min(scale.right(), Math.max(startX, endX));
        if (right - left < MIN_DRAG_PIXELS) {
            return null;
        }
        long from = timeAt(scale, left);
        long to = timeAt(scale, right);
        if (to - from < MIN_SPAN) {
            to = from + MIN_SPAN;
            if (to > scale.to() && scale.to() - MIN_SPAN >= scale.from()) {
                to = scale.to();
                from = to - MIN_SPAN;
            }
        }
        return new long[]{from, to};
    }

    /// @param task A task.
    /// @param from The span's first time.
    /// @param to   Its last.
    /// @return True if the span shows any of it: a bar that runs into the span (not one that only touches its
    /// ends), or a marker in it, its ends included.
    static boolean isVisible(final Task task, final long from, final long to) {
        return task.isMarker()
                ? task.start() >= from && task.start() <= to
                : task.start() < to && task.end() > from;
    }

    /// @param tasks A section's tasks.
    /// @param from  The span's first time.
    /// @param to    Its last.
    /// @return Those the span shows ([#isVisible]), in their order.
    static List<Task> visible(final List<Task> tasks, final long from, final long to) {
        final List<Task> visible = new ArrayList<>();
        for (final Task task : tasks) {
            if (isVisible(task, from, to)) {
                visible.add(task);
            }
        }
        return visible;
    }

    /// @param task A task the span shows.
    /// @param from The span's first time.
    /// @param to   Its last.
    /// @return The part of it that is drawn: its start and end held to the span.
    static long[] clip(final Task task, final long from, final long to) {
        return new long[]{Math.max(task.start(), from), Math.min(task.end(), to)};
    }

    /// @param view A span, as [#dragRange] gives it, or null for the whole period.
    /// @return It as an attribute's value (`from,to`), or null for the whole period.
    static String toAttribute(final long[] view) {
        return view == null
                ? null
                : view[0] + "," + view[1];
    }

    /// @param value An attribute's value, as [#toAttribute] writes it, or null or empty.
    /// @return The span it holds, or null (the whole period) if it holds none that can be read.
    static long[] fromAttribute(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        final int comma = value.indexOf(',');
        if (comma <= 0) {
            return null;
        }
        try {
            final long from = Long.parseLong(value.substring(0, comma).trim());
            final long to = Long.parseLong(value.substring(comma + 1).trim());
            return to > from
                    ? new long[]{from, to}
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
