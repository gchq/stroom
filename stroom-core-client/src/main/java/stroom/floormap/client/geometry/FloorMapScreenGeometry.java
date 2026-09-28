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

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;
import stroom.util.shared.NullSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure map/screen geometry for the floor-map canvas — fact bounding boxes,
 * content bounds, marquee hit-testing and the selection frame. Extracted from
 * the GWT view so the projection maths (which must stay in lock-step with the
 * renderer's transform pipeline) is unit-testable on the JVM.
 *
 * <p>Screen projection matches the view's draw transform: a map point
 * {@code (mx, my)} maps to screen {@code (offsetX + scale·mx, offsetY − scale·my)}
 * (map space is Y-up, SVG is Y-down). Image facts are placed by the render
 * wrapper {@code worldToMap · translate(0,h) · scale(1,-1)}, matching
 * {@code FloorMapCanvasViewImpl.appendImageGlyph}.</p>
 *
 * <p>Holds no GWT/DOM types. The view constructs one per query with the
 * last-drawn scale/pan and an {@link AspectRatioSource} backed by its image
 * aspect-ratio cache.</p>
 */
public final class FloorMapScreenGeometry {

    /** Supplies an image's aspect ratio (width/height) by URL, or {@code null} if unknown. */
    public interface AspectRatioSource {

        Double aspectRatio(String imageUrl);
    }

    /**
     * Longest edge a layer graphic may occupy, as a multiple of the glyph size —
     * stops a banner-shaped image becoming an unreadably wide sliver once its box
     * is area-matched. See {@link #graphicBox}.
     */
    public static final double MAX_GRAPHIC_EDGE_RATIO = 2.0;

    /**
     * The screen size, in pixels, of the square box a point glyph occupies —
     * an event entity or an imageless, vertex-less fact, drawn at a fixed screen
     * size whatever the zoom.
     *
     * <p>Lives here, rather than only in the renderer, because it is not just a
     * drawing detail: clustering merges entities whose glyphs would overlap, so
     * the merge distance is a function of this. Two independent constants that
     * had to agree by hand is exactly how the merge distance ended up smaller
     * than the glyph it was meant to keep clear.</p>
     */
    public static final int POINT_GLYPH_SIZE_PX = 60;

    /**
     * The map-space width an image fact is rendered at before its placement
     * matrix scales it — so an image's size on the map is this times the
     * matrix's scale, and its height that divided by the image's aspect ratio.
     *
     * <p>Lives here so the renderer and anything that needs to state or set an
     * image's real-world size agree on the same base.</p>
     */
    public static final double DEFAULT_IMAGE_DISPLAY_WIDTH = 1000;

    private final double scale;
    private final double offsetX;
    private final double offsetY;
    private final double imageDisplayWidth;
    private final double objectSize;
    private final AspectRatioSource aspectRatioSource;
    private final List<TypeStyle> typeStyles;

    /**
     * @param scale             the last-drawn zoom scale
     * @param offsetX           the last-drawn pan offset X (screen px)
     * @param offsetY           the last-drawn pan offset Y (screen px)
     * @param imageDisplayWidth the map-space width image facts render at
     * @param objectSize        the fixed on-screen size (px) of an imageless glyph
     * @param aspectRatioSource supplies image aspect ratios (may return {@code null})
     * @param typeStyles        per-type styles, so a layer that draws an image is
     *                          measured at the box that image actually occupies;
     *                          may be {@code null}, in which case every imageless
     *                          fact measures as a square glyph
     */
    public FloorMapScreenGeometry(final double scale,
                                  final double offsetX,
                                  final double offsetY,
                                  final double imageDisplayWidth,
                                  final double objectSize,
                                  final AspectRatioSource aspectRatioSource,
                                  final List<TypeStyle> typeStyles) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.imageDisplayWidth = imageDisplayWidth;
        this.objectSize = objectSize;
        this.aspectRatioSource = aspectRatioSource;
        this.typeStyles = typeStyles;
    }

    /**
     * The on-screen {@code {width, height}} a layer graphic is drawn at, sized so it
     * carries the <strong>same visual weight as a shape glyph</strong>.
     *
     * <p>Matching the shape's bounding box is not enough: a shape is solid ink out
     * to its box edge, whereas an image preserving its aspect ratio inside a square
     * box only reaches the edge on its longer side. So the box matches the shape
     * box's <em>area</em> instead — for aspect ratio {@code r} that is
     * {@code (S·√r, S/√r)}, which has area {@code S²} for every {@code r} and gives
     * a square image exactly {@code S × S}.</p>
     *
     * <p>This lives here, rather than in the renderer, because hit-testing and the
     * selection frame must use the identical box. When they disagreed, a wide icon
     * drew 120×30 but hit-tested as 60×60, so a marquee over its outer edges missed
     * it and the selection frame was drawn inside the glyph.</p>
     *
     * @param objectSize  the glyph size {@code S} in screen px
     * @param aspectRatio the image's width/height, or {@code null} when not yet
     *                    known (the caller then draws it square)
     * @return a two-element {@code {width, height}} in screen px
     */
    public static double[] graphicBox(final double objectSize, final Double aspectRatio) {
        if (aspectRatio == null
                || aspectRatio <= 0
                || Double.isNaN(aspectRatio)
                || Double.isInfinite(aspectRatio)) {
            return new double[]{objectSize, objectSize};
        }
        final double root = Math.sqrt(aspectRatio);
        double width = objectSize * root;
        double height = objectSize / root;

        final double longest = Math.max(width, height);
        final double cap = objectSize * MAX_GRAPHIC_EDGE_RATIO;
        if (longest > cap) {
            final double shrink = cap / longest;
            width = width * shrink;
            height = height * shrink;
        }
        return new double[]{width, height};
    }

    /**
     * The asset URL of the image configured as {@code type}'s layer graphic, or
     * {@code null} to draw a shape. Mirrors the renderer's lookup.
     */
    private String graphicForType(final String type) {
        if (type != null && typeStyles != null) {
            for (final TypeStyle style : typeStyles) {
                if (style != null && type.equals(style.getType()) && style.hasGraphic()) {
                    return style.getGraphic();
                }
            }
        }
        return null;
    }

    /**
     * Returns a fact's on-screen bounding box {@code {minX, minY, maxX, maxY}};
     * never {@code null}. Dispatch order matches the renderer: image wins over
     * vertices, else a fixed-size glyph box.
     *
     * <p>This used to document a {@code null} return "if the fact has no matrix",
     * which cannot happen: {@link Fact}'s canonical constructor - the only way to
     * build one, including via {@code withWorldToMap} and {@code withVertices} -
     * substitutes {@link FloorMapTransformationMatrix#identity()} for a null
     * matrix. The claim had propagated into null checks at the call sites.</p>
     */
    public double[] factScreenBounds(final Fact fact) {
        final FloorMapTransformationMatrix w2m = fact.getWorldToMap();
        if (!fact.hasImage() && fact.hasVertices()) {
            final double[] acc = newBoundsAccumulator();
            for (final double[] v : fact.getVertices()) {
                final double[] mapPt = w2m.transformPoint(v[0], v[1]);
                expandScreen(acc, mapPt[0], mapPt[1]);
            }
            return acc;
        }
        if (fact.hasImage()) {
            final double[] acc = newBoundsAccumulator();
            for (final double[] c : imageCornersMap(fact, w2m)) {
                expandScreen(acc, c[0], c[1]);
            }
            return acc;
        }
        final double[] pos = fact.getPosition();
        final double[] mapPt = w2m.transformPoint(
                pos != null ? pos[0] : 0, pos != null ? pos[1] : 0);
        final double sx = mapToScreenX(mapPt[0]);
        final double sy = mapToScreenY(mapPt[1]);
        // A layer that draws an image occupies the area-matched graphic box, which
        // is not square; measuring it as a square would put the marquee hit area
        // and the selection frame in the wrong place.
        final String graphic = graphicForType(fact.getType());
        final double[] box = graphic != null
                ? graphicBox(objectSize, aspectRatioSource != null
                        ? aspectRatioSource.aspectRatio(graphic)
                        : null)
                : new double[]{objectSize, objectSize};
        final double halfW = box[0] / 2.0;
        final double halfH = box[1] / 2.0;
        return new double[]{sx - halfW, sy - halfH, sx + halfW, sy + halfH};
    }

    /**
     * Returns the map-space bounding box {@code {minX, minY, maxX, maxY}} of all
     * facts, or {@code null} if there is none. Independent of the current
     * scale/pan (used to compute an initial zoom-to-fit).
     */
    public double[] contentMapBounds(final List<Fact> facts) {
        if (NullSafe.isEmptyCollection(facts)) {
            return null;
        }
        final double[] acc = newBoundsAccumulator();
        boolean any = false;
        for (final Fact fact : facts) {
            final FloorMapTransformationMatrix w2m = fact.getWorldToMap();
            if (w2m == null) {
                continue;
            }
            final double[][] pts;
            if (!fact.hasImage() && fact.hasVertices()) {
                final double[][] verts = fact.getVertices();
                pts = new double[verts.length][];
                for (int i = 0; i < verts.length; i++) {
                    pts[i] = w2m.transformPoint(verts[i][0], verts[i][1]);
                }
            } else if (fact.hasImage()) {
                pts = imageCornersMap(fact, w2m);
            } else {
                final double[] pos = fact.getPosition();
                pts = new double[][]{w2m.transformPoint(
                        pos != null ? pos[0] : 0, pos != null ? pos[1] : 0)};
            }
            for (final double[] p : pts) {
                expandMap(acc, p[0], p[1]);
                any = true;
            }
        }
        return any ? acc : null;
    }

    /**
     * Returns the keys of facts whose on-screen AABB intersects the rectangle
     * {@code {minX, minY, maxX, maxY}} (element pixels); touch counts as a hit.
     */
    public Set<String> hitTestRect(final List<Fact> facts, final double[] rectPx) {
        final Set<String> hits = new HashSet<>();
        if (facts == null || rectPx == null) {
            return hits;
        }
        for (final Fact fact : facts) {
            // A fact the renderer refuses to draw must not be selectable either; see
            // Fact.hasUsablePlacement(). Its bounds would be a zero-size box at the origin,
            // which almost every marquee overlaps.
            if (!fact.hasUsablePlacement()) {
                continue;
            }
            final double[] b = factScreenBounds(fact);
            if (b[0] <= rectPx[2] && b[2] >= rectPx[0]
                    && b[1] <= rectPx[3] && b[3] >= rectPx[1]) {
                hits.add(fact.getKey());
            }
        }
        return hits;
    }

    /**
     * Returns the screen-space bounding box of the selected facts (union of
     * their on-screen bounds), padded to {@code minFramePx} so handles stay
     * separable. Returns {@code null} when nothing is selected or laid out.
     */
    public double[] selectionFrame(final List<Fact> facts,
                                   final Set<String> selectedIds,
                                   final double minFramePx) {
        if (facts == null || NullSafe.isEmptyCollection(selectedIds)) {
            return null;
        }
        final double[] acc = newBoundsAccumulator();
        boolean any = false;
        for (final Fact f : facts) {
            if (selectedIds.contains(f.getKey())) {
                final double[] b = factScreenBounds(f);
                acc[0] = Math.min(acc[0], b[0]);
                acc[1] = Math.min(acc[1], b[1]);
                acc[2] = Math.max(acc[2], b[2]);
                acc[3] = Math.max(acc[3], b[3]);
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        if (acc[2] - acc[0] < minFramePx) {
            final double c = (acc[0] + acc[2]) / 2;
            acc[0] = c - minFramePx / 2;
            acc[2] = c + minFramePx / 2;
        }
        if (acc[3] - acc[1] < minFramePx) {
            final double c = (acc[1] + acc[3]) / 2;
            acc[1] = c - minFramePx / 2;
            acc[3] = c + minFramePx / 2;
        }
        return acc;
    }

    /**
     * The four corners of an image fact in map space, via the render wrapper
     * transform {@code worldToMap · translate(0,h) · scale(1,-1)}. Aspect ratio
     * falls back to square when unknown, matching the renderer's pre-load state.
     */
    private double[][] imageCornersMap(final Fact fact, final FloorMapTransformationMatrix w2m) {
        final Double ar = aspectRatioSource != null
                ? aspectRatioSource.aspectRatio(fact.getImage())
                : null;
        final double aspect = ar != null ? ar : 1.0;
        final double w = imageDisplayWidth;
        final double h = w / aspect;
        final FloorMapTransformationMatrix m = w2m
                .multiply(FloorMapTransformationMatrix.translate(0, h))
                .multiply(FloorMapTransformationMatrix.scale(1, -1));
        return new double[][]{
                m.transformPoint(0, 0), m.transformPoint(w, 0),
                m.transformPoint(0, h), m.transformPoint(w, h)};
    }

    private static double[] newBoundsAccumulator() {
        return new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
    }

    /**
     * Projects a point from map space to screen space.
     *
     * <p>The Y flip is the whole reason this exists as a named method: map space is Y-up and
     * the SVG viewport is Y-down, so the correct projection is {@code +} on X and {@code -}
     * on Y. Get that wrong and objects still appear on the map, just mirrored about the
     * horizontal axis - which on a symmetrical floor plan can go unnoticed indefinitely.
     * This class owned the formula but kept it private, so callers needing a point-level
     * projection re-derived it by hand.</p>
     *
     * <p><strong>Not the same as
     * {@link FloorMapViewport#mapToScreen(double, double, FloorMapTransformationMatrix)}</strong>,
     * despite the name. That one takes a point in a fact's <em>world</em> frame and applies
     * the background matrix first, so the Y flip is already inside the matrix and the
     * projection that follows is {@code +} on both axes. This one takes a point already in
     * <em>map</em> space and so must apply the flip itself. Using either where the other
     * belongs produces a vertically mirrored result, which is why both spell out which
     * space they expect.</p>
     *
     * @param mapX the x coordinate in map space
     * @param mapY the y coordinate in map space
     * @return {@code {screenX, screenY}}
     */
    public double[] mapToScreen(final double mapX, final double mapY) {
        return new double[]{mapToScreenX(mapX), mapToScreenY(mapY)};
    }

    /**
     * Projects a point with an explicit viewport, for callers holding scale and pan directly
     * rather than a {@code FloorMapScreenGeometry}.
     *
     * @param mapX    the x coordinate in map space
     * @param mapY    the y coordinate in map space
     * @param scale   pixels per map unit
     * @param offsetX the pan offset in screen pixels
     * @param offsetY the pan offset in screen pixels
     * @return {@code {screenX, screenY}}
     */
    public static double[] mapToScreen(final double mapX,
                                       final double mapY,
                                       final double scale,
                                       final double offsetX,
                                       final double offsetY) {
        return new double[]{offsetX + scale * mapX, offsetY - scale * mapY};
    }

    /**
     * The X half of {@link #mapToScreen(double, double)}, allocation-free for the render
     * path - {@code expandScreen} runs once per vertex per fact per frame.
     */
    public double mapToScreenX(final double mapX) {
        return offsetX + scale * mapX;
    }

    /** The Y half of {@link #mapToScreen(double, double)}, including the Y-up to Y-down flip. */
    public double mapToScreenY(final double mapY) {
        return offsetY - scale * mapY;
    }

    private void expandScreen(final double[] acc, final double mapX, final double mapY) {
        final double sx = mapToScreenX(mapX);
        final double sy = mapToScreenY(mapY);
        acc[0] = Math.min(acc[0], sx);
        acc[1] = Math.min(acc[1], sy);
        acc[2] = Math.max(acc[2], sx);
        acc[3] = Math.max(acc[3], sy);
    }

    private static void expandMap(final double[] acc, final double x, final double y) {
        acc[0] = Math.min(acc[0], x);
        acc[1] = Math.min(acc[1], y);
        acc[2] = Math.max(acc[2], x);
        acc[3] = Math.max(acc[3], y);
    }
}
