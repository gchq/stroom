/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.geometry;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Decides which glyph captions the canvas can draw without any of them
/// overlapping.
///
/// ## Why this is needed
///
/// Clustering merges entities of the *same* type that crowd each other,
/// deliberately leaving different types alone — a user standing at a desk is two
/// glyphs, and that is correct, because merging them would destroy the type colour,
/// shape and layer dimming that say what each one is. Captions are far wider than
/// the glyphs they hang under, though, so two glyphs that sit together perfectly
/// well produce two captions written on top of each other.
///
/// Clustering also cannot help within a type: its merge radius is about
/// three-quarters of a glyph, so two entities just beyond it stay separate while
/// their captions — easily wider than a glyph — still collide.
///
/// ## How it decides
///
/// Greedy placement in **priority order**: the most important
/// caption is placed first and keeps its spot, and any caption that would overlap
/// something already placed is dropped for this frame. So crowding costs the
/// *least* important names, never the most important ones, and nothing is
/// ever drawn illegibly on top of anything else.
///
/// Priority is the caller's to decide — this class only orders by it — but the
/// canvas uses: the tracked entity, then clusters (largest first, since a cluster's
/// caption speaks for many entities), then live event entities, then static facts.
/// Ties break on the label's key so the result never depends on the order the
/// caller happened to collect them in.
///
/// A dropped caption is not lost information: zooming in separates the glyphs
/// and every caption reappears, and hovering names things regardless.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public final class FloorMapLabelPlacement {

    /// Screen-space bucket size for the overlap search, in pixels. Only labels in
    /// the cells a candidate touches are tested against, which keeps a crowded
    /// frame from degenerating into comparing every label with every other one.
    private static final double CELL_PX = 128;

    private FloorMapLabelPlacement() {
        // Utility class.
    }

    /// Chooses which labels to draw.
    ///
    /// @param labels           the candidates, in any order
    /// @param viewportWidthPx  the canvas width; `0` or less disables the
    ///         off-screen cull
    /// @param viewportHeightPx the canvas height; `0` or less disables the
    ///         off-screen cull
    /// @return the keys of the labels to draw; never `null`
    public static Set<String> place(final List<Label> labels,
                                    final double viewportWidthPx,
                                    final double viewportHeightPx) {
        if (NullSafe.isEmptyCollection(labels)) {
            return Collections.emptySet();
        }

        final List<Label> ordered = new ArrayList<>(labels);
        // Deterministic: the same frame must place the same labels however the
        // caller collected them, or captions would flicker as rows reorder.
        ordered.sort((a, b) -> a.priority != b.priority
                ? Integer.compare(a.priority, b.priority)
                : a.key.compareTo(b.key));

        final Set<String> visible = new HashSet<>();
        final Map<String, List<double[]>> placedByCell = new HashMap<>();

        for (final Label label : ordered) {
            if (!visible.contains(label.key)) {
                final double[] rect = label.rect();
                // A label wholly off-screen is not drawn, and must not reserve
                // space either — otherwise something just outside the view would
                // suppress a caption the user can actually see.
                if (!offScreen(rect, viewportWidthPx, viewportHeightPx)
                        && !collides(rect, placedByCell)) {
                    visible.add(label.key);
                    addToCells(rect, placedByCell);
                }
            }
        }
        return visible;
    }

    private static boolean offScreen(final double[] rect,
                                     final double viewportWidthPx,
                                     final double viewportHeightPx) {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return false;
        }
        return rect[2] < 0
                || rect[0] > viewportWidthPx
                || rect[3] < 0
                || rect[1] > viewportHeightPx;
    }

    /// `true` if the rectangle overlaps one already placed. Touching edges do
    /// not count — two captions exactly abutting are still both readable.
    private static boolean collides(final double[] rect,
                                    final Map<String, List<double[]>> placedByCell) {
        final int minCx = cellIndex(rect[0]);
        final int maxCx = cellIndex(rect[2]);
        final int minCy = cellIndex(rect[1]);
        final int maxCy = cellIndex(rect[3]);
        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                final List<double[]> placed = placedByCell.get(cellKey(cx, cy));
                if (placed != null) {
                    for (final double[] other : placed) {
                        if (rect[0] < other[2]
                                && rect[2] > other[0]
                                && rect[1] < other[3]
                                && rect[3] > other[1]) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /// Registers a placed rectangle in every cell it touches.
    private static void addToCells(final double[] rect,
                                   final Map<String, List<double[]>> placedByCell) {
        final int minCx = cellIndex(rect[0]);
        final int maxCx = cellIndex(rect[2]);
        final int minCy = cellIndex(rect[1]);
        final int maxCy = cellIndex(rect[3]);
        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                //noinspection unused k
                placedByCell.computeIfAbsent(cellKey(cx, cy), k -> new ArrayList<>())
                        .add(rect);
            }
        }
    }

    /// The bucket a screen coordinate falls in. An `int` rather than a
    /// `long` because this runs for every label on every animation frame and
    /// GWT emulates `long` as a three-`int` tuple; the clamp covers
    /// coordinates far outside any real canvas.
    private static int cellIndex(final double valuePx) {
        final double index = Math.floor(valuePx / CELL_PX);
        if (index <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (index >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) index;
    }

    private static String cellKey(final int cx, final int cy) {
        return cx + "," + cy;
    }

    // --------------------------------------------------------------------------------

    /// One caption competing for space: where it would go, how big it would be, and
    /// how much it matters.
    public static final class Label {

        private final String key;
        private final double centreXPx;
        private final double topYPx;
        private final double widthPx;
        private final double heightPx;
        private final int priority;

        /// @param key       identifies the caption — an entity id or a cluster key.
        ///         Also the tie-break when two labels have equal priority,
        ///         so it must be stable from frame to frame
        /// @param centreXPx the caption's centre on screen; captions are centred
        ///         under their glyph
        /// @param topYPx    the caption's top edge on screen
        /// @param widthPx   its estimated width — err generous, since underestimating
        ///         lets captions touch
        /// @param heightPx  its estimated height
        /// @param priority  **lower is placed first** and so survives
        ///         crowding; see the class javadoc for the canvas's scheme
        public Label(final String key,
                     final double centreXPx,
                     final double topYPx,
                     final double widthPx,
                     final double heightPx,
                     final int priority) {
            this.key = key;
            this.centreXPx = centreXPx;
            this.topYPx = topYPx;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.priority = priority;
        }

        public String getKey() {
            return key;
        }

        public int getPriority() {
            return priority;
        }

        /// The label's screen rectangle as `{minX, minY, maxX, maxY}`.
        double[] rect() {
            final double half = widthPx / 2.0;
            return new double[]{
                    centreXPx - half,
                    topYPx,
                    centreXPx + half,
                    topYPx + heightPx};
        }
    }
}
