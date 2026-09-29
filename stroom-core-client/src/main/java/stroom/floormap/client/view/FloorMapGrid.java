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

package stroom.floormap.client.view;

import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

/// Generates an adaptive SVG grid overlay for the floor map canvas.
///
/// The grid is a non-interactive UI aid that visualises map space; it is
/// not a background and is not tied to any background image.
///
/// The grid fills the entire SVG viewport and dynamically adjusts its
/// spacing based on the combined zoom level (world-to-map matrix scale
/// × user zoom), so there is always a visually comfortable number of
/// grid squares on screen.
///
/// ### Grid levels
///
/// Major grid lines are drawn at power-of-10 intervals in
/// *display* units. Each major square contains 10 subdivisions (minor
/// grid). As the user zooms in, minor lines fade in; once they are large enough
/// on screen they promote to become the next major level and a finer set of
/// subdivisions appears. Zooming out reverses this.
///
/// The decade is chosen in the unit the map is *displayed* in rather
/// than in map units, so one grid square is a round real-world distance — 1 m,
/// 10 m, 100 m — rather than whatever a power of ten in map units happens to
/// convert to. The grid draws no text of its own; the corner scale bar states
/// what a square is worth.
///
/// ### Rendering
///
/// Uses SVG `<pattern>` elements with a `patternTransform`
/// that aligns the grid with the map coordinate system. The grid rects use
/// `width="100%" height="100%"` so the grid always extends to the
/// edges of the viewport regardless of pan/zoom.
///
/// Grid lines hold a constant screen-pixel width, but *not* via
/// `vector-effect="non-scaling-stroke"` — that has no effect inside a
/// `<pattern>` tile, because the tile is rasterised once in pattern
/// coordinate space and then tiled. The stroke widths are instead divided by
/// the effective scale so they work out to a fixed pixel width once the
/// pattern transform has been applied. That arithmetic is the reason
/// `MAJOR_SCREEN_PX / effectiveScale` appears in `appendGrid`.
///
/// This method should be called at the **SVG root level**,
/// outside the pan/zoom and matrix transform groups.
public final class FloorMapGrid {

    // -- Appearance constants ------------------------------------------------

    /// Background fill — references the CSS variable
    /// `--floormap-grid__background-color` so the grid respects
    /// light/dark themes.
    // These three are emitted as `style="stroke: var(...)"` rather than as
    // `stroke="var(...)"`. CSS Custom Properties defines var() substitution for property
    // declarations; whether a presentation *attribute* substitutes is not something the
    // specs clearly guarantee, and implementations have differed. Chromium resolves both
    // forms (measured), so the attribute form was not broken here - but the style form is
    // the one that is actually defined, and it costs nothing.
    //
    // Safe to raise the precedence this way because nothing in the stylesheet targets these
    // elements: the grid lines and background rect carry no class, and the tokens below are
    // already the theme indirection (--floormap-grid__major-stroke -> --vis-axis__color), so
    // there is no rule for an inline style to override.
    private static final String BG_FILL = "var(--floormap-grid__background-color)";
    /// Major grid line colour — references the CSS variable
    /// `--floormap-grid__major-stroke`.
    private static final String MAJOR_STROKE = "var(--floormap-grid__major-stroke)";
    /// Desired screen-pixel width for major grid lines.
    private static final double MAJOR_SCREEN_PX = 1.0;
    /// Maximum opacity for minor grid lines (reached mid-decade).
    private static final double MINOR_MAX_OPACITY = 0.25;
    /// Desired screen-pixel width for minor grid lines.
    private static final double MINOR_SCREEN_PX = 0.5;

    /// Minor grid line colour — references the CSS variable
    /// `--floormap-grid__minor-stroke`. The dynamic
    /// zoom-dependent opacity is applied via the SVG
    /// `stroke-opacity` attribute rather than being baked
    /// into an `rgba()` value, so the base colour can
    /// come from CSS.
    private static final String MINOR_STROKE = "var(--floormap-grid__minor-stroke)";

    // -- Zoom range constants ------------------------------------------------

    /// Minimum desired screen-pixel spacing for major grid lines.
    /// Below this, we switch to the next coarser decade.
    static final double TARGET_MIN_PX = 40.0;

    /// Maximum desired screen-pixel spacing for major grid lines.
    /// Above this, we switch to the next finer decade.
    static final double TARGET_MAX_PX = 400.0;


    private FloorMapGrid() {
        // utility class
    }

    /// Appends SVG elements for an adaptive grid background into the given
    /// [HtmlBuilder]. This should be called at the **SVG root
    /// level**, outside the pan/zoom and matrix transform groups.
    ///
    /// The grid fills the entire viewport (`width="100%"
    /// height="100%"`) and uses `patternTransform` to align the grid
    /// with the map coordinate system. This means the grid always extends
    /// to the edges of the screen regardless of pan or zoom.
    ///
    /// @param builder    the HtmlBuilder to append into (at SVG root level)
    /// @param matrix     the map-to-screen transformation matrix
    /// @param userZoom   the current user zoom level (the `scale` in
    ///         the pan group)
    /// @param panX       the current horizontal pan offset (the `x`
    ///         translation in the pan group)
    /// @param panY       the current vertical pan offset (the `y`
    ///         translation in the pan group)
    /// @param units      what one map unit means in the real world, or
    ///         `null` on a map with no scale set — in which case
    ///         the default scale (one centimetre per map unit) applies.
    ///         Sizes the grid decade; the grid itself carries no text
    /// @param patternId  id for the `<pattern>` this appends, and for the
    ///         `url(#...)` that fills the overlay rect with it. Must be
    ///         unique across the whole page, not merely within the enclosing
    ///         `<svg>`: for inline SVG, id resolution is document-wide,
    ///         so two canvases using one id makes the second rect pick up the
    ///         first canvas's pattern — including its `patternTransform`,
    ///         which encodes that canvas's pan and zoom. The grid then visibly
    ///         scrolls with the wrong map. Callers should mint one id per view
    ///         instance and reuse it across frames rather than generating a
    ///         fresh one per draw.
    public static void appendGrid(final HtmlBuilder builder,
                                  final FloorMapTransformationMatrix matrix,
                                  final double userZoom,
                                  final double panX,
                                  final double panY,
                                  final FloorMapMeasurementUnits units,
                                  final String patternId) {

        // -- 1. Compute effective pixels-per-world-unit ----------------------
        //    matrixScale = scale factor of the map-to-screen affine matrix
        //    effectiveScale = matrixScale × userZoom
        final double matrixScale = Math.sqrt(
                matrix.getA() * matrix.getA()
                + matrix.getB() * matrix.getB());
        final double effectiveScale = matrixScale * userZoom;

        // -- 2. Pick the major grid decade -----------------------------------
        final double[] params = computeGridParams(effectiveScale, unitsPerMapUnit(units));
        final double majorWorldSpacing = params[0];
        final double minorOpacity = params[1];

        // World-space size of one minor grid cell (1/10th of major)
        final double minorWorldSpacing = majorWorldSpacing / 10.0;

        final String minorOpacityStr = formatDouble(minorOpacity);

        // Compute stroke widths in world-space units so that lines render
        // at a constant screen-pixel width.  We cannot use
        // vector-effect="non-scaling-stroke" because it does not work
        // inside SVG <pattern> tiles (the tile is rasterised once in
        // pattern-coordinate space, then tiled).
        final String majorStrokeWidth = formatDouble(MAJOR_SCREEN_PX / effectiveScale);
        final String minorStrokeWidth = formatDouble(MINOR_SCREEN_PX / effectiveScale);

        // The combined transform that maps pattern (world-space) coordinates
        // to SVG viewport coordinates: first the world-to-map matrix, then
        // the user pan/zoom.  Pattern tile dimensions are in world-space —
        // this transform handles the entire mapping to screen pixels.
        final String patternTransform = "translate(" + formatDouble(panX)
                + "," + formatDouble(panY) + ") scale(" + formatDouble(userZoom)
                + ") " + matrix.toSvgMatrix();

        // -- 3. Emit SVG <defs> with grid pattern and arrowhead marker -------
        //    One tile = one major grid cell. Minor subdivisions (9 lines)
        //    are drawn directly inside the tile — no nested patterns, which
        //    avoids patternTransform compounding issues.
        //noinspection CodeBlock2Expr
        builder.elem(defs -> {

            defs.elem(gridPattern -> {

                // Minor subdivision lines (positions 1/10 .. 9/10 of the tile).
                // Line 0 is the major line drawn below.
                if (minorOpacity > 0.01) {
                    for (int i = 1; i < 10; i++) {
                        final String pos = formatDouble(i * minorWorldSpacing);
                        // Horizontal minor line
                        gridPattern.elem(SafeHtmlUtil.from("line"),
                                new Attribute("x1", "0"),
                                new Attribute("y1", pos),
                                new Attribute("x2", formatDouble(majorWorldSpacing)),
                                new Attribute("y2", pos),
                                new Attribute("style", "stroke: " + MINOR_STROKE),
                                new Attribute("stroke-opacity", minorOpacityStr),
                                new Attribute("stroke-width", minorStrokeWidth));
                        // Vertical minor line
                        gridPattern.elem(SafeHtmlUtil.from("line"),
                                new Attribute("x1", pos),
                                new Attribute("y1", "0"),
                                new Attribute("x2", pos),
                                new Attribute("y2", formatDouble(majorWorldSpacing)),
                                new Attribute("style", "stroke: " + MINOR_STROKE),
                                new Attribute("stroke-opacity", minorOpacityStr),
                                new Attribute("stroke-width", minorStrokeWidth));
                    }
                }

                // Major grid lines (at tile edges: top and left).
                // Horizontal major line
                gridPattern.elem(SafeHtmlUtil.from("line"),
                        new Attribute("x1", "0"),
                        new Attribute("y1", "0"),
                        new Attribute("x2", formatDouble(majorWorldSpacing)),
                        new Attribute("y2", "0"),
                        new Attribute("style", "stroke: " + MAJOR_STROKE),
                        new Attribute("stroke-width", majorStrokeWidth));
                // Vertical major line
                gridPattern.elem(SafeHtmlUtil.from("line"),
                        new Attribute("x1", "0"),
                        new Attribute("y1", "0"),
                        new Attribute("x2", "0"),
                        new Attribute("y2", formatDouble(majorWorldSpacing)),
                        new Attribute("style", "stroke: " + MAJOR_STROKE),
                        new Attribute("stroke-width", majorStrokeWidth));

            },
                    SafeHtmlUtil.from("pattern"),
                    new Attribute("id", patternId),
                    new Attribute("width", formatDouble(majorWorldSpacing)),
                    new Attribute("height", formatDouble(majorWorldSpacing)),
                    new Attribute("patternUnits", "userSpaceOnUse"),
                    new Attribute("patternTransform", patternTransform));

        }, SafeHtmlUtil.from("defs"));

        // -- 4. Background fill (dark) — fills entire viewport --------------
        //    No 'id' attribute: this rect is purely decorative and must not
        //    intercept edit-mode click detection (which keys on id="background").
        builder.elem(SafeHtmlUtil.from("rect"),
                new Attribute("width", "100%"),
                new Attribute("height", "100%"),
                new Attribute("style", "fill: " + BG_FILL));

        // -- 5. Grid overlay — fills entire viewport ------------------------
        builder.elem(SafeHtmlUtil.from("rect"),
                new Attribute("width", "100%"),
                new Attribute("height", "100%"),
                new Attribute("fill", "url(#" + patternId + ")"),
                new Attribute("pointer-events", "none"));
    }

    // -- Helpers -------------------------------------------------------------

    /// Computes the grid parameters for a given effective scale.
    ///
    /// Returns a two-element array:
    ///
    /// 1. `majorWorldSpacing` — the world-unit distance between
    ///    major grid lines (always a power of 10)
    /// 2. `minorOpacity` — the opacity for the minor grid lines
    ///    (0.0 when subdivisions are invisible, up to
    ///    [#MINOR_MAX_OPACITY] when fully visible)
    ///
    /// @param effectiveScale combined pixels-per-world-unit
    ///         (`matrixScale × userZoom`)
    /// @return a two-element array `{majorWorldSpacing, minorOpacity}`
    static double[] computeGridParams(final double effectiveScale) {
        return computeGridParams(effectiveScale, 1.0);
    }

    /// As [#computeGridParams(double)], but choosing the decade in
    /// *display* units so a calibrated map's grid lands on round real-world
    /// distances.
    ///
    /// The returned spacing is still in **map** units — that is
    /// what the pattern is drawn in — but it is a power of ten once multiplied by
    /// `unitsPerMapUnit`. A factor of 1 (an uncalibrated map) makes this
    /// identical to the single-argument form.
    ///
    /// @param effectiveScale  combined pixels-per-map-unit
    ///         (`matrixScale × userZoom`)
    /// @param unitsPerMapUnit how many display units one map unit spans; a
    ///         non-positive or non-finite value is treated as 1
    /// @return a two-element array `{majorWorldSpacing, minorOpacity}`
    static double[] computeGridParams(final double effectiveScale,
                                      final double unitsPerMapUnit) {
        // Guard against non-positive or non-finite values that would
        // produce NaN/Infinity in the log calculations.
        if (!(effectiveScale > 0) || !Double.isFinite(effectiveScale)) {
            return new double[]{1.0, 0.0};
        }
        // A zero or non-finite factor would put NaN into every pattern
        // coordinate, and an SVG with NaN coordinates renders nothing at all —
        // a blank canvas with no error. Fall back to "unscaled" instead.
        final double factor = unitsPerMapUnit > 0 && Double.isFinite(unitsPerMapUnit)
                ? unitsPerMapUnit
                : 1.0;

        // Pixels per *display* unit, so the decade below is chosen in the unit
        // the user reads, not in map units.
        final double displayScale = effectiveScale / factor;

        final double rawLogSpacing = Math.log10(TARGET_MIN_PX / displayScale);
        final double decadeExponent = Math.ceil(rawLogSpacing);
        final double majorDisplaySpacing = Math.pow(10, decadeExponent);
        final double majorWorldSpacing = majorDisplaySpacing / factor;

        final double screenPx = majorWorldSpacing * effectiveScale;
        final double t = Math.log10(screenPx / TARGET_MIN_PX)
                / Math.log10(TARGET_MAX_PX / TARGET_MIN_PX);
        final double minorOpacity = clampZeroToOne(t) * MINOR_MAX_OPACITY;

        return new double[]{majorWorldSpacing, minorOpacity};
    }

    /// Sizes the on-screen scale bar: the longest "nice" distance that fits in
    /// `maxWidthPx`, and how wide that is on screen.
    ///
    /// Unlike the grid, the bar walks the 1-2-5 series rather than powers of
    /// ten alone ([FloorMapMeasurementUnits#niceRoundLength]) — restricted
    /// to decades it would spend most of the zoom range at a tenth of the width
    /// available to it. The "nice" value is chosen in *display* units so
    /// the bar reads `50 m`, not `47.3 m`.
    ///
    /// @param effectiveScale combined pixels-per-map-unit
    /// @param maxWidthPx     the widest the bar may be drawn
    /// @param units          the document's measurement units, or `null` —
    ///         in which case the bar is measured in map units
    /// @return a two-element array `{mapLength, widthPx}`, or `{0, 0}` when no usable bar
    ///         can be drawn (pass the map length to
    ///         [FloorMapMeasurementUnits#format(FloorMapMeasurementUnits, double)]
    ///         for the label)
    public static double[] scaleBar(final double effectiveScale,
                                    final double maxWidthPx,
                                    final FloorMapMeasurementUnits units) {
        if (!(effectiveScale > 0) || !Double.isFinite(effectiveScale)
            || !(maxWidthPx > 0) || !Double.isFinite(maxWidthPx)) {
            return new double[]{0, 0};
        }
        final double factor = unitsPerMapUnit(units);

        // The longest distance that fits, expressed in display units, rounded
        // down to something worth printing.
        final double maxDisplayLength = maxWidthPx / effectiveScale * factor;
        final double displayLength = FloorMapMeasurementUnits.niceRoundLength(maxDisplayLength);
        if (!(displayLength > 0)) {
            return new double[]{0, 0};
        }

        final double mapLength = displayLength / factor;
        return new double[]{mapLength, mapLength * effectiveScale};
    }

    /// The scale factor to compute with, defaulting to 1 for a map that has no
    /// scale set (or one whose scale is unusable).
    private static double unitsPerMapUnit(final FloorMapMeasurementUnits units) {
        return units != null && units.checkUnitIsValid()
                ? units.getUnitsPerMapUnit()
                : 1.0;
    }

    /// Returns the on-screen pixel distance spanned by one major grid division
    /// at the given effective scale — i.e. `majorWorldSpacing ×
    /// effectiveScale`.
    ///
    /// This is the single source of truth for the adaptive decade, shared by
    /// any caller that positions the view relative to the grid (for example the
    /// initial pan that insets the origin by half a division) so it stays aligned
    /// with the drawn grid at whatever zoom is in effect.
    ///
    /// @param effectiveScale combined pixels-per-world-unit
    ///         (`matrixScale × userZoom`)
    /// @return the major grid division size in screen pixels
    public static double majorDivisionScreenPx(final double effectiveScale) {
        return majorDivisionScreenPx(effectiveScale, null);
    }

    /// As [#majorDivisionScreenPx(double)], for a map with a scale set.
    ///
    /// Callers **must** pass the same units the grid is drawn
    /// with. This method exists so the view that positions content relative to
    /// the grid stays aligned with it; given different units it would quietly
    /// stop matching the lines on screen.
    ///
    /// @param effectiveScale combined pixels-per-map-unit
    /// @param units          the document's measurement units, or `null`
    /// @return the major grid division size in screen pixels
    public static double majorDivisionScreenPx(final double effectiveScale,
                                               final FloorMapMeasurementUnits units) {
        return computeGridParams(effectiveScale, unitsPerMapUnit(units))[0] * effectiveScale;
    }

    /// Returns the world-unit distance between adjacent minor grid lines at the
    /// given effective scale — i.e. one tenth of the adaptive major
    /// spacing.
    ///
    /// Exposed so callers that position content relative to the grid (for
    /// example nudging a duplicated object clear of its original) can express an
    /// offset as a number of minor grid divisions, keeping it visually
    /// consistent at any magnification.
    ///
    /// @param effectiveScale combined pixels-per-world-unit
    ///         (`matrixScale × userZoom`)
    /// @return the minor grid spacing in world-space units
    public static double minorWorldSpacing(final double effectiveScale) {
        return minorWorldSpacing(effectiveScale, null);
    }

    /// As [#minorWorldSpacing(double)], for a map with a scale set. The
    /// same alignment caveat applies: pass the units the grid is drawn with.
    ///
    /// @param effectiveScale combined pixels-per-map-unit
    /// @param units          the document's measurement units, or `null`
    /// @return the minor grid spacing in map-space units
    public static double minorWorldSpacing(final double effectiveScale,
                                           final FloorMapMeasurementUnits units) {
        return computeGridParams(effectiveScale, unitsPerMapUnit(units))[0] / 10.0;
    }

    private static double clampZeroToOne(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String formatDouble(final double value) {
        // Avoid trailing zeros for clean SVG output
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
