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

import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;

/**
 * The teardrop a {@link FloorMapIcon} is drawn inside on the map — a coloured
 * pin with a white outline and the icon knocked out of it in white.
 *
 * <h2>Why an icon is not drawn bare</h2>
 * <p>An icon is a silhouette assembled from separate pieces — a printer's body,
 * its paper, its tray — and over a floor plan those pieces read as scattered
 * marks rather than as one object. Set several next to each other on a busy
 * background and the eye cannot tell where one entity ends and the next begins.
 * Wrapping each icon in a filled shape with a white outline gives it a single
 * silhouette and a guaranteed edge against whatever is underneath, which is
 * exactly why every slippy map does it.</p>
 *
 * <p>It also inverts the colour relationship for the better: the layer's colour
 * becomes a solid field instead of thin ink, so it survives being shrunk, and
 * the icon reads as white against it at any size.</p>
 *
 * <h2>The geometry</h2>
 * <p>On the same 24&times;24 grid the icons use, so
 * {@link FloorMapIcon#transform} places the pair. A circular head plus a tail
 * made from the two <em>tangent</em> lines from the tip to that circle — derived
 * rather than drawn by eye, so the tail meets the head smoothly at any size
 * instead of showing a kink where a hand-fitted curve missed.</p>
 *
 * <p><strong>The marker is centred in the glyph box, not anchored by its
 * tip.</strong> Every other glyph the canvas draws — shapes, images, the
 * built-in {@link TypeStyle.Shape#PIN} — is centred on the entity's position,
 * and captions, count pills, selection frames and hit-testing are all measured
 * from that box. A tip-anchored marker would sit half a glyph higher than
 * everything around it on a map that mixes styles.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapMarker {

    /** The grid the marker is drawn on — the icons' grid, so the two compose. */
    public static final double GRID = FloorMapIcon.GRID;

    /** Centre of the marker's circular head. */
    public static final double HEAD_X = 12;
    public static final double HEAD_Y = 10.5;

    /**
     * Radius of the circular head.
     *
     * <p>Large relative to the grid on purpose: the marker is meant to read as a
     * <em>circle with a tail</em>, not as a teardrop. A bigger head also leaves
     * more room for the icon inside it, which is the part carrying the
     * meaning.</p>
     */
    public static final double HEAD_RADIUS = 9.3;

    /**
     * Where the tail's two edges converge — a <em>virtual</em> vertex, since
     * {@link #TIP_RADIUS} rounds the corner off before it gets there. The drawn
     * shape stops short of this, at {@link #bottomY()}.
     */
    public static final double TIP_Y = 22.6;

    /**
     * Radius of the fillet that rounds the tip off, in grid units.
     *
     * <p>A geometrically exact point is unpleasantly sharp at map sizes and picks
     * up an aliasing spike when it is scaled down. This rounds it into the tail
     * without costing the pin its direction — the edges still converge, they just
     * stop converging at the end. Chosen by rendering the alternatives: below
     * about 1 the change is invisible, and above about 3 the tail shortens into a
     * balloon and the shape stops reading as a pin.</p>
     */
    public static final double TIP_RADIUS = 2.5;

    /**
     * How far the drawn shape hangs below the head, in grid units.
     *
     * <p>Not a setting — derived from the constants above and asserted, because
     * it is the number that decides whether the marker reads as a pin or as a
     * plain circle. Around 4 gives a pronounced teardrop; around 1 the tail
     * disappears and the shape looks like a circle with a defect. This sits
     * between, which is the "circle with a subtle tail" the marker is meant to
     * be.</p>
     */
    public static double tailOverhang() {
        return bottomY() - (HEAD_Y + HEAD_RADIUS);
    }

    /**
     * How far the furthest icon's ink reaches from the centre of its own grid,
     * in grid units.
     *
     * <p>Measured, not assumed — by rasterising every icon and finding the most
     * distant opaque pixel (see {@code README-icons.md}; the current worst is
     * {@code BARRIER}, whose arm runs almost the full width). It is well short of
     * the grid's corner at {@code 16.97}, because no icon has ink in its corners,
     * and using the corner instead would shrink every icon by a fifth to make
     * room for empty space.</p>
     *
     * <p><strong>Re-measure when adding an icon.</strong> If a new one reaches
     * further than this, {@code TestFloorMapMarker} fails rather than letting it
     * silently spill over the marker's rim.</p>
     */
    public static final double MAX_ICON_INK_RADIUS = 14.7;

    /**
     * How much of the grid the icon inside the head takes up.
     *
     * <p>The largest scale at which {@link #MAX_ICON_INK_RADIUS} still clears the
     * <em>inner</em> edge of the white outline — the outline is centred on the
     * path, so it eats {@link #OUTLINE_WIDTH} / 2 of the head's radius — with a
     * little margin left over.</p>
     */
    public static final double ICON_SCALE = 0.57;

    /** Width of the white outline, in grid units. */
    public static final double OUTLINE_WIDTH = 1.0;

    /**
     * Width of the ring drawn round a selected or highlighted marker, in grid
     * units. Wider than {@link #OUTLINE_WIDTH} so it shows outside the white.
     */
    public static final double SELECTION_WIDTH = 3.6;

    private static final String PATH = buildPath();

    private FloorMapMarker() {
        // Utility class
    }

    /**
     * The SVG {@code d} attribute for the teardrop, on the {@value #GRID}-unit
     * grid. Fill it with the layer's colour and stroke it white.
     */
    public static String getPath() {
        return PATH;
    }

    /**
     * The SVG {@code transform} that places a {@link FloorMapIcon}'s path centred
     * in the marker's head, scaled by {@link #ICON_SCALE}. Applied <em>inside</em>
     * the group that already carries {@link FloorMapIcon#transform}.
     */
    public static String iconTransform() {
        final double offset = HEAD_X - ((GRID * ICON_SCALE) / 2);
        final double offsetY = HEAD_Y - ((GRID * ICON_SCALE) / 2);
        return "translate(" + offset + "," + offsetY + ") scale(" + ICON_SCALE + ")";
    }

    /**
     * The length of a tail edge: the tangent length from the virtual vertex to
     * the head, {@code sqrt(d² - r²)} by Pythagoras on the right triangle whose
     * corners are the head's centre, the tangent point and the vertex.
     */
    private static double tangentLength() {
        final double distance = TIP_Y - HEAD_Y;
        return Math.sqrt((distance * distance) - (HEAD_RADIUS * HEAD_RADIUS));
    }

    /**
     * How far down the drawn shape actually reaches — short of {@link #TIP_Y},
     * because the fillet cuts the corner off.
     *
     * <p>The fillet's centre sits on the axis at {@code TIP_RADIUS / sin(phi)}
     * above the virtual vertex, where {@code phi} is the tail's half-angle, and
     * the shape's lowest point is one radius below that. Since
     * {@code sin(phi) = r / d}, that distance is just
     * {@code TIP_RADIUS * d / r}.</p>
     */
    public static double bottomY() {
        final double distance = TIP_Y - HEAD_Y;
        return TIP_Y - ((TIP_RADIUS * distance) / HEAD_RADIUS) + TIP_RADIUS;
    }

    /**
     * Builds the marker: up one tail edge, the long way round the head, down the
     * other edge, and across the rounded tip.
     *
     * <p>Every point is computed rather than chosen, and — worth knowing before
     * anyone reaches for {@code Math.atan} to "simplify" this —
     * <strong>entirely without trigonometry</strong>. The angles are only ever
     * needed as ratios, and every ratio is available directly from the right
     * triangle formed by the head's centre, a tangent point and the vertex: with
     * {@code d} the vertex's distance from the centre, {@code r} the head's
     * radius and {@code t = sqrt(d² - r²)} the tangent length,
     * {@code sin(phi) = r/d}, {@code cos(phi) = t/d} and {@code tan(phi) = r/t}.
     * So the whole path needs one square root and no inverse trig, which is both
     * exact and cheap.</p>
     *
     * <p>The tangent points are where an edge leaves the circle exactly as it
     * stops cutting into it, so the tail flows out of the head with no visible
     * join. The fillet then meets each edge {@code TIP_RADIUS / tan(phi)} back
     * from the vertex, the distance at which a circle of that radius touches
     * both, so the rounding is smooth from whichever side it is followed.</p>
     */
    private static String buildPath() {
        final double distance = TIP_Y - HEAD_Y;
        final double tangent = tangentLength();

        // The tangent point, from the similar triangles: its offset from the
        // head's centre is r·(t/d) across and r·(r/d) down.
        final double dx = (HEAD_RADIUS * tangent) / distance;
        final double dy = (HEAD_RADIUS * HEAD_RADIUS) / distance;

        final double leftX = HEAD_X - dx;
        final double rightX = HEAD_X + dx;
        final double tangentY = HEAD_Y + dy;

        // Where the fillet meets each tail edge: TIP_RADIUS / tan(phi) back from
        // the vertex, stepped along the edge's own direction. The edge's length
        // is the tangent length, so no second square root is needed to normalise
        // it.
        final double along = (TIP_RADIUS * tangent) / HEAD_RADIUS;
        final double stepX = ((leftX - HEAD_X) / tangent) * along;
        final double stepY = ((tangentY - TIP_Y) / tangent) * along;

        final double filletLeftX = HEAD_X + stepX;
        final double filletRightX = HEAD_X - stepX;
        final double filletY = TIP_Y + stepY;

        // Head: large arc, swept clockwise — from the left tangent point up over
        // the top and down to the right one, the long way round. Tip: a minor arc
        // the same way round, closing the outline across the bottom.
        return "M" + filletLeftX + " " + filletY
                + "L" + leftX + " " + tangentY
                + "A" + HEAD_RADIUS + " " + HEAD_RADIUS + " 0 1 1 "
                + rightX + " " + tangentY
                + "L" + filletRightX + " " + filletY
                + "A" + TIP_RADIUS + " " + TIP_RADIUS + " 0 0 1 "
                + filletLeftX + " " + filletY
                + "Z";
    }
}
