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

package stroom.floormap.client;

import stroom.floormap.client.geometry.FloorMapMarker;
import stroom.floormap.client.geometry.FloorMapShapes;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/// Builds the small preview of a layer's graphic — the swatch in each Layers panel
/// row and the preview in the appearance dialog.
///
/// The geometry comes from [FloorMapShapes] and [FloorMapIcon], the
/// same sources the canvas glyphs use, so the legend and the map cannot drift
/// apart. The dispatch matches the canvas's render precedence: a layer with a
/// [graphic][TypeStyle#getGraphic()] previews as a thumbnail of that image,
/// one with an [icon][TypeStyle#getIcon()] as that icon in the layer's
/// colour, and anything else as its shape.
public final class FloorMapSwatchHtml {

    /// Shape extent as a fraction of the swatch box, leaving a little breathing
    /// room around the edge.
    private static final double SHAPE_EXTENT_RATIO = 0.34;

    /// Icon extent as a fraction of the swatch box. Larger than
    /// [#SHAPE_EXTENT_RATIO] because an icon's ink does not fill its grid —
    /// a person is mostly empty at the corners — so matching the shapes' inset
    /// would leave it visibly smaller than the shape it replaces.
    private static final double ICON_EXTENT_RATIO = 0.44;

    /// Marker extent as a fraction of the preview box. Smaller than
    /// [#ICON_EXTENT_RATIO] because a marker *does* fill its grid,
    /// and its white outline needs somewhere to go.
    private static final double MARKER_EXTENT_RATIO = 0.42;

    /// The marker's outline and the icon knocked out of it. See the canvas's copy.
    private static final String MARKER_OUTLINE = "#ffffff";

    private FloorMapSwatchHtml() {
        // Utility class
    }

    /// A square preview of the given style at `sizePx`.
    ///
    /// @param style  the layer style; `null` previews as the fallback glyph
    /// @param sizePx the width and height of the preview in pixels
    /// @return safe markup for the preview
    public static SafeHtml swatch(final TypeStyle style, final int sizePx) {
        if (style != null && style.hasGraphic()) {
            return imageSwatch(style.getGraphic(), sizePx);
        }
        if (style != null && style.hasIcon()) {
            return iconSwatch(style.iconOrNull(),
                    style.getColour(), style.getType(), sizePx);
        }
        return shapeSwatch(style == null ? null : style.getShape(),
                style == null ? null : style.getColour(),
                style == null ? null : style.getType(),
                sizePx);
    }

    /// A preview of exactly what the **map** draws for this style —
    /// which, for a layer using a built-in icon, is the icon inside its
    /// [marker][FloorMapMarker], not the bare icon.
    ///
    /// Distinct from [#swatch] on purpose. A swatch is a legend key: it
    /// sits in a Layers row answering "which layer is this", and at that size the
    /// marker's outline and white knock-out would cost legibility for nothing. A
    /// preview is a promise about the canvas, so it has to carry the chrome.
    ///
    /// @param style  the layer style; `null` previews as the fallback glyph
    /// @param sizePx the width and height of the preview in pixels
    public static SafeHtml mapPreview(final TypeStyle style, final int sizePx) {
        if (style != null && !style.hasGraphic() && style.hasIcon()) {
            return markerSwatch(style.iconOrNull(),
                    style.getColour(), style.getType(), sizePx);
        }
        return swatch(style, sizePx);
    }

    /// The marker as the canvas draws it: the layer's colour filling a
    /// white-outlined teardrop with the icon knocked out of it.
    ///
    /// Built from [FloorMapMarker] and [FloorMapIcon], the same
    /// geometry the canvas uses, so the preview cannot promise something the map
    /// does not deliver.
    private static SafeHtml markerSwatch(final FloorMapIcon icon,
                                         final String colour,
                                         final String type,
                                         final int sizePx) {
        final String fill = isValidColour(colour)
                ? colour
                : TypeStyle.colourForType(type, null);
        // The marker fills its grid, unlike a bare icon, so it gets the shapes'
        // inset rather than the icon's more generous one.
        final double extent = sizePx * MARKER_EXTENT_RATIO;
        final double half = sizePx / 2.0;
        return SafeHtmlUtils.fromTrustedString(
                "<svg width=\"" + sizePx + "\" height=\"" + sizePx + "\""
                + " viewBox=\"0 0 " + sizePx + " " + sizePx + "\""
                + " xmlns=\"http://www.w3.org/2000/svg\">"
                + "<g transform=\"translate(" + half + "," + half + ")\">"
                + "<g transform=\"" + FloorMapIcon.transform(extent) + "\">"
                + "<path d=\"" + FloorMapMarker.getPath() + "\""
                + " fill=\"" + fill + "\""
                + " stroke=\"" + MARKER_OUTLINE + "\""
                + " stroke-width=\"" + FloorMapMarker.OUTLINE_WIDTH + "\""
                + " stroke-linejoin=\"round\"/>"
                + "<g transform=\"" + FloorMapMarker.iconTransform() + "\">"
                + "<path d=\"" + icon.getPath() + "\" fill=\"" + MARKER_OUTLINE + "\"/>"
                + "</g></g></g></svg>");
    }

    /// A preview of one of the built-in icons, filled with the layer's colour —
    /// the same path and the same fill the canvas draws, so the swatch is the
    /// glyph in miniature rather than a likeness of it.
    ///
    /// @param icon   the icon; never `null` here
    /// @param colour the layer's colour, or `null` to resolve the default
    /// @param type   the layer's type, for that default
    /// @param sizePx the width and height of the preview
    public static SafeHtml iconSwatch(final FloorMapIcon icon,
                                      final String colour,
                                      final String type,
                                      final int sizePx) {
        final String fill = isValidColour(colour)
                ? colour
                : TypeStyle.colourForType(type, null);
        // The icon fills its grid edge to edge, so it is inset to leave the same
        // breathing room a shape swatch has.
        final double extent = sizePx * ICON_EXTENT_RATIO;
        final double half = sizePx / 2.0;
        return SafeHtmlUtils.fromTrustedString(
                "<svg width=\"" + sizePx + "\" height=\"" + sizePx + "\""
                + " viewBox=\"0 0 " + sizePx + " " + sizePx + "\""
                + " xmlns=\"http://www.w3.org/2000/svg\">"
                + "<g transform=\"translate(" + half + "," + half + ")\">"
                + "<path d=\"" + icon.getPath() + "\""
                + " transform=\"" + FloorMapIcon.transform(extent) + "\""
                + " fill=\"" + fill + "\"/>"
                + "</g></svg>");
    }

    /// A thumbnail of an asset-store image, letterboxed into the box so a
    /// non-square image is not distorted — the same `contain` fit the canvas
    /// gives a layer graphic.
    ///
    /// **Why there is no SVG scale compensation here.** The canvas
    /// has to compensate for SVGs with no `viewBox` (see
    /// `FloorMapCanvasViewImpl.appendScaledImage`), because an SVG
    /// `<image>` scales its referent *through that referent's viewBox*
    /// and so cannot scale one that lacks it. An HTML `<img>` scales by a
    /// different route: the element's own box is the concrete object size and the SVG
    /// is scaled into it as an ordinary replaced element, viewBox or not. So sizing
    /// the element is sufficient here, and the two surfaces agree.
    ///
    /// The size is set as both attributes and inline style so a stylesheet rule on
    /// `img` cannot collapse the box and, with it, `object-fit`.
    ///
    /// The URL is document-controlled data going into `innerHTML`, so it
    /// is escaped; an unescaped value (a stray quote) would allow attribute
    /// injection.
    private static SafeHtml imageSwatch(final String url, final int sizePx) {
        return SafeHtmlUtils.fromTrustedString(
                "<img src=\"" + SafeHtmlUtils.htmlEscape(url) + "\""
                + " width=\"" + sizePx + "\" height=\"" + sizePx + "\""
                + " style=\"width:" + sizePx + "px;height:" + sizePx + "px;"
                + "object-fit:contain;\" alt=\"\"/>");
    }

    /// An inline-SVG preview of `shape` filled with `colour`.
    ///
    /// A layer with no colour of its own previews in the colour the canvas will
    /// actually draw it in ([TypeStyle#colourForType]) rather than a swatch-only
    /// grey, so the legend states the map's appearance.
    private static SafeHtml shapeSwatch(final Shape shape,
                                        final String colour,
                                        final String type,
                                        final int sizePx) {
        final String fill = isValidColour(colour)
                ? colour
                : TypeStyle.colourForType(type, null);
        final double half = sizePx / 2.0;
        final double extent = sizePx * SHAPE_EXTENT_RATIO;
        return SafeHtmlUtils.fromTrustedString(
                "<svg width=\"" + sizePx + "\" height=\"" + sizePx + "\""
                + " viewBox=\"0 0 " + sizePx + " " + sizePx + "\""
                + " xmlns=\"http://www.w3.org/2000/svg\">"
                // Centre the origin so FloorMapShapes' origin-centred, y-down
                // geometry can be reused verbatim.
                + "<g transform=\"translate(" + half + "," + half + ")\">"
                + shapeSvg(shape, fill, extent)
                + "</g></svg>");
    }

    /// The inner SVG for a shape centred on the origin and spanning
    /// `±extent`. Mirrors the canvas's dispatch in
    /// `FloorMapCanvasViewImpl.appendStyledGlyph`.
    private static String shapeSvg(final Shape shape, final String fill, final double extent) {
        if (shape == null) {
            // The fallback graphic for an unconfigured layer is a rounded rect.
            return "<rect x=\"" + (-extent) + "\" y=\"" + (-extent * 0.62) + "\""
                    + " width=\"" + (extent * 2) + "\" height=\"" + (extent * 1.24) + "\""
                    + " rx=\"" + (extent * 0.2) + "\" fill=\"" + fill + "\"/>";
        }
        //noinspection EnhancedSwitchMigration
        switch (shape) {
            case SQUARE:
                return "<rect x=\"" + (-extent) + "\" y=\"" + (-extent) + "\""
                        + " width=\"" + (extent * 2) + "\" height=\"" + (extent * 2) + "\""
                        + " rx=\"" + (extent * 0.15) + "\" fill=\"" + fill + "\"/>";
            case TRIANGLE:
            case DIAMOND:
                return "<polygon points=\"" + FloorMapShapes.polygonPoints(shape, extent) + "\""
                        + " fill=\"" + fill + "\"/>";
            case PIN:
                return "<path d=\"" + FloorMapShapes.pinPath(extent) + "\" fill=\"" + fill + "\"/>"
                        + "<circle cx=\"0\""
                        + " cy=\"" + (extent * FloorMapShapes.PIN_HOLE_CENTRE_Y_RATIO) + "\""
                        + " r=\"" + (extent * FloorMapShapes.PIN_HOLE_RADIUS_RATIO) + "\""
                        + " fill=\"#ffffff\"/>";
            case CIRCLE:
            default:
                return "<circle cx=\"0\" cy=\"0\" r=\"" + extent + "\" fill=\"" + fill + "\"/>";
        }
    }

    /// True if `colour` is a hex colour literal. Guards the swatch against a
    /// hand-edited document injecting markup through the colour field, which is
    /// interpolated into trusted SVG.
    private static boolean isValidColour(final String colour) {
        // Checked by hand rather than with a regex: this runs once per layer row on
        // every Layers rebuild, and GWT does not emulate java.util.regex.Pattern,
        // so a precompiled pattern is not an option here — String.matches would
        // build a fresh JS RegExp on each call.
        if (colour == null || colour.length() < 4 || colour.length() > 9) {
            return false;
        }
        if (colour.charAt(0) != '#') {
            return false;
        }
        for (int i = 1; i < colour.length(); i++) {
            final char c = colour.charAt(i);
            final boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
