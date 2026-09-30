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

package stroom.floormap.client.geometry;

import stroom.floormap.shared.TypeStyle.Shape;

/// Geometry for the default graphic drawn for an imageless fact (see the redesign
/// §5 rendering rules and §6 type settings). Kept as pure, GWT-free logic so the
/// point maths can be unit-tested; the canvas view turns these into SVG elements.
///
/// Shapes are centred on the origin and fit within `±halfSize`, in a
/// **screen-like frame where `+y` points down** (the canvas
/// glyph group counter-flips the map's Y-up axis, so an upward-pointing triangle
/// has its apex at `-halfSize`). Circle and square are rendered natively by
/// the view (as `<circle>` / `<rect>`); triangle and diamond are
/// polygons whose points [#polygonPoints] computes; pin is a teardrop path
/// from [#pinPath] plus a knocked-out hole.
public final class FloorMapShapes {

    /// Centre of the pin's hole as a fraction of `halfSize` — negative, so
    /// the hole sits above the centre, inside the bulb.
    public static final double PIN_HOLE_CENTRE_Y_RATIO = -0.2125;

    /// Radius of the pin's hole as a fraction of `halfSize`.
    public static final double PIN_HOLE_RADIUS_RATIO = 0.2;

    private FloorMapShapes() {
        // Utility class
    }

    /// The SVG `points` attribute (space-separated `"x,y"` pairs) for a
    /// polygon shape centred on the origin, or `null` for shapes the view
    /// renders natively — `CIRCLE` and `SQUARE` as primitives,
    /// `PIN` as the path from [#pinPath], and `null` as the
    /// fallback rounded rectangle.
    ///
    /// @param shape    the shape, or `null`
    /// @param halfSize half the graphic's extent (so it spans `2 * halfSize`)
    /// @return the polygon points string, or `null` if not a polygon shape
    public static String polygonPoints(final Shape shape, final double halfSize) {
        if (shape == null) {
            return null;
        }
        //noinspection EnhancedSwitchMigration
        switch (shape) {
            case TRIANGLE:
                // Upward-pointing triangle.
                return point(0, -halfSize)
                        + " " + point(halfSize, halfSize)
                        + " " + point(-halfSize, halfSize);
            case DIAMOND:
                return point(0, -halfSize)
                        + " " + point(halfSize, 0)
                        + " " + point(0, halfSize)
                        + " " + point(-halfSize, 0);
            case CIRCLE:
            case SQUARE:
            case PIN:
            default:
                return null;
        }
    }

    /// The SVG path `d` for a map-pin teardrop centred on the origin: a round
    /// bulb at the top narrowing to a point at `0.75 * halfSize`
    /// (screen-down), the bulb reaching up to `-0.5625 * halfSize`. The pin
    /// is deliberately taller below the centre than above, so its *tip* —
    /// the part that marks the position — is what the eye follows.
    ///
    /// The proportions match the 16&nbsp;×&nbsp;16 pin drawn in the Layers
    /// panel's swatch, so the canvas glyph and the legend agree. The caller knocks
    /// out the hole using [#PIN_HOLE_CENTRE_Y_RATIO] and
    /// [#PIN_HOLE_RADIUS_RATIO].
    ///
    /// @param halfSize half the graphic's extent (so it spans `2 * halfSize`)
    /// @return the path `d` attribute value
    public static String pinPath(final double halfSize) {
        return "M" + point(0, 0.75 * halfSize)
                + " C" + point(-0.5 * halfSize, 0.125 * halfSize)
                + " " + point(-0.475 * halfSize, -0.5625 * halfSize)
                + " " + point(0, -0.5625 * halfSize)
                + " C" + point(0.475 * halfSize, -0.5625 * halfSize)
                + " " + point(0.5 * halfSize, 0.125 * halfSize)
                + " " + point(0, 0.75 * halfSize)
                + " Z";
    }

    private static String point(final double x, final double y) {
        return x + "," + y;
    }
}
