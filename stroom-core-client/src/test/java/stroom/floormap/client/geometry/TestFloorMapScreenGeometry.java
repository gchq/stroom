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

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapScreenGeometry {

    private static final double IMAGE_DISPLAY_WIDTH = 1000;
    private static final double OBJECT_SIZE = 60;
    private static final double TOL = 1e-6;

    /// No aspect ratios known (square fallback).
    private static final FloorMapScreenGeometry.AspectRatioSource NO_AR = ignored -> null;

    private FloorMapScreenGeometry geometry(final double scale, final double ox, final double oy) {
        return new FloorMapScreenGeometry(scale, ox, oy, IMAGE_DISPLAY_WIDTH, OBJECT_SIZE, NO_AR, null);
    }

    /// Geometry for a map whose `type` layer draws `graphicUrl` at the
    /// given aspect ratio.
    private FloorMapScreenGeometry geometryWithLayerGraphic() {
        return new FloorMapScreenGeometry(1, 0, 0, IMAGE_DISPLAY_WIDTH, OBJECT_SIZE,
                url -> "/assets/x/wide.png".equals(url) ? 4.0
                        : null,
                List.of(new TypeStyle("t", null, "#111111", "/assets/x/wide.png")));
    }

    private static Fact pointFact(final String key, final double x, final double y) {
        return new Fact(key, "t", null, FloorMapTransformationMatrix.identity(),
                new double[]{x, y});
    }

    private static Fact areaFact(@SuppressWarnings("SameParameterValue") final String key,
                                 final double[][] verts) {
        return new Fact(key, "area", null, FloorMapTransformationMatrix.identity(),
                null, verts, null, null);
    }

    // -----------------------------------------------------------------------

    /// A point glyph is a fixed OBJECT_SIZE box around its projected anchor (Y-flip).
    @Test
    void testPointGlyphBounds() {
        // scale 2, offset (100, 200); map (10, 5) → screen (100+2*10, 200-2*5) = (120, 190).
        final double[] b = geometry(2, 100, 200).factScreenBounds(pointFact("p", 10, 5));
        assertThat(b).isNotNull();
        assertThat(b[0]).isCloseTo(120 - 30, within(TOL));
        assertThat(b[1]).isCloseTo(190 - 30, within(TOL));
        assertThat(b[2]).isCloseTo(120 + 30, within(TOL));
        assertThat(b[3]).isCloseTo(190 + 30, within(TOL));
    }

    /// An area's screen bounds are the AABB of its projected vertices.
    @Test
    void testAreaBounds() {
        final Fact area = areaFact("a", new double[][]{{0, 0}, {10, 0}, {10, 10}});
        final double[] b = geometry(1, 0, 0).factScreenBounds(area);
        // Y-flip: map y=0 → screen 0, map y=10 → screen -10.
        assertThat(b).isNotNull();
        assertThat(b[0]).isCloseTo(0, within(TOL));    // minX
        assertThat(b[1]).isCloseTo(-10, within(TOL));  // minY (from y=10)
        assertThat(b[2]).isCloseTo(10, within(TOL));   // maxX
        assertThat(b[3]).isCloseTo(0, within(TOL));    // maxY (from y=0)
    }

    /// contentMapBounds is scale/pan independent and covers all facts.
    @Test
    void testContentMapBounds() {
        final double[] b = geometry(7, 3, 9).contentMapBounds(List.of(
                pointFact("p1", -5, -5), pointFact("p2", 20, 30)));
        assertThat(b).containsExactly(-5, -5, 20, 30);
    }

    @Test
    void testContentMapBounds_emptyIsNull() {
        assertThat(geometry(1, 0, 0).contentMapBounds(List.of())).isNull();
    }

    /// Marquee hit test returns facts whose screen AABB intersects the rect.
    @Test
    void testHitTestRect() {
        final List<Fact> facts = List.of(pointFact("in", 0, 0), pointFact("out", 1000, 1000));
        final Set<String> hits = geometry(1, 0, 0)
                .hitTestRect(facts, new double[]{-40, -40, 40, 40});
        assertThat(hits).containsExactly("in");
    }

    /// A single tiny selection is padded out to the minimum frame size.
    @Test
    void testSelectionFrame_padsToMinimum() {
        final double[] f = geometry(1, 0, 0).selectionFrame(
                List.of(pointFact("p", 0, 0)), Set.of("p"), 200);
        // Point box is 60px; padded to 200 about the centre (0,0).
        assertThat(f[2] - f[0]).isCloseTo(200, within(TOL));
        assertThat(f[3] - f[1]).isCloseTo(200, within(TOL));
    }

    @Test
    void testSelectionFrame_noSelectionIsNull() {
        assertThat(geometry(1, 0, 0).selectionFrame(
                List.of(pointFact("p", 0, 0)), Set.of(), 24)).isNull();
    }

    /// Image bounds use the render-wrapper transform; square fallback when aspect unknown.
    @Test
    void testImageBounds_squareFallback() {
        final Fact img = new Fact("bg", "background", "asset://x.png",
                FloorMapTransformationMatrix.identity(), null);
        final double[] b = geometry(1, 0, 0).factScreenBounds(img);
        // Wrapper places the image in the first quadrant up-and-right of origin;
        // width = 1000, height = 1000/1 = 1000. Screen Y-flip maps the top to -1000.
        assertThat(b).isNotNull();
        assertThat(b[0]).isCloseTo(0, within(TOL));
        assertThat(b[2]).isCloseTo(1000, within(TOL));
        assertThat(b[3] - b[1]).isCloseTo(1000, within(TOL));
    }

    // -----------------------------------------------------------------------
    // Layer graphics — the drawn box and the measured box must agree
    // -----------------------------------------------------------------------

    /// A square graphic occupies exactly the shape glyph's box.
    @Test
    void testGraphicBox_squareMatchesTheGlyphExactly() {
        final double[] box = FloorMapScreenGeometry.graphicBox(OBJECT_SIZE, 1.0);
        assertThat(box[0]).isCloseTo(OBJECT_SIZE, within(TOL));
        assertThat(box[1]).isCloseTo(OBJECT_SIZE, within(TOL));
    }

    /// A non-square graphic keeps the glyph's AREA, so it reads at the same size.
    @Test
    void testGraphicBox_matchesAreaNotBounds() {
        final double[] box = FloorMapScreenGeometry.graphicBox(OBJECT_SIZE, 4.0);
        // r=4 -> (S*2, S/2) = 120 x 30; area 3600 = 60*60.
        assertThat(box[0]).isCloseTo(120, within(TOL));
        assertThat(box[1]).isCloseTo(30, within(TOL));
        assertThat(box[0] * box[1]).isCloseTo(OBJECT_SIZE * OBJECT_SIZE, within(1e-9));
        // And it preserves the image's own ratio, so nothing is distorted.
        assertThat(box[0] / box[1]).isCloseTo(4.0, within(TOL));
    }

    /// An extreme ratio is capped so a banner cannot become an unreadable sliver.
    @Test
    void testGraphicBox_capsTheLongestEdge() {
        final double[] box = FloorMapScreenGeometry.graphicBox(OBJECT_SIZE, 100.0);
        final double cap = OBJECT_SIZE * FloorMapScreenGeometry.MAX_GRAPHIC_EDGE_RATIO;
        assertThat(Math.max(box[0], box[1])).isCloseTo(cap, within(TOL));
        // Capping scales both edges, so the aspect ratio still holds.
        assertThat(box[0] / box[1]).isCloseTo(100.0, within(1e-6));
    }

    /// An unknown or nonsense ratio falls back to square, matching the renderer.
    @Test
    void testGraphicBox_fallsBackToSquareWhenRatioUnusable() {
        for (final Double bad : new Double[]{null, 0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            final double[] box = FloorMapScreenGeometry.graphicBox(OBJECT_SIZE, bad);
            assertThat(box[0]).isCloseTo(OBJECT_SIZE, within(TOL));
            assertThat(box[1]).isCloseTo(OBJECT_SIZE, within(TOL));
        }
    }

    /// The regression this fixes: a fact on a layer that draws a wide image must
    /// measure as that wide box, not as a square, or a marquee over its outer edges
    /// misses it and the selection frame is drawn inside the glyph.
    @Test
    void testFactScreenBounds_usesTheLayerGraphicBox() {
        final Fact fact = pointFact("f", 0, 0);
        final double[] b = geometryWithLayerGraphic()
                .factScreenBounds(fact);
        // 120 x 30 centred on the origin, rather than 60 x 60.
        assertThat(b).isNotNull();
        assertThat(b[2] - b[0]).isCloseTo(120, within(TOL));
        assertThat(b[3] - b[1]).isCloseTo(30, within(TOL));
    }

    /// A layer with no graphic still measures as a square glyph.
    @Test
    void testFactScreenBounds_squareWhenLayerHasNoGraphic() {
        final FloorMapScreenGeometry g = new FloorMapScreenGeometry(
                1, 0, 0, IMAGE_DISPLAY_WIDTH, OBJECT_SIZE, NO_AR,
                List.of(new TypeStyle("t", TypeStyle.Shape.CIRCLE, "#111111")));
        final double[] b = g.factScreenBounds(pointFact("f", 0, 0));
        assertThat(b).isNotNull();
        assertThat(b[2] - b[0]).isCloseTo(OBJECT_SIZE, within(TOL));
        assertThat(b[3] - b[1]).isCloseTo(OBJECT_SIZE, within(TOL));
    }

    /// The projection flips Y, and the exposed method agrees with the bounds computed
    /// internally.
    ///
    /// The last pair of assertions is the point of extracting this: the same formula
    /// previously existed as three hand-written copies, one of them in the view where nothing
    /// could test it. Tying the public method to `factScreenBounds` means a change to
    /// one that does not match the other fails here.
    @Test
    void testMapToScreen_flipsYAndMatchesTheInternalProjection() {
        final FloorMapScreenGeometry g = geometry(2, 100, 50);

        // x: 100 + 2*10 = 120.  y: 50 - 2*10 = 30 (map space is Y-up, screen is Y-down).
        assertThat(g.mapToScreen(10, 10)).containsExactly(120.0, 30.0);
        assertThat(g.mapToScreenX(10)).isEqualTo(120.0);
        assertThat(g.mapToScreenY(10)).isEqualTo(30.0);

        // The static form with an explicit viewport must agree.
        assertThat(FloorMapScreenGeometry.mapToScreen(10, 10, 2, 100, 50))
                .containsExactly(120.0, 30.0);

        // And it must agree with the bounds the class computes for a point fact there:
        // the AABB is centred on the projected anchor.
        final double[] bounds = g.factScreenBounds(pointFact("f", 10, 10));
        assertThat((bounds[0] + bounds[2]) / 2).isCloseTo(120.0, within(TOL));
        assertThat((bounds[1] + bounds[3]) / 2).isCloseTo(30.0, within(TOL));
    }

    /// A fact the renderer will not draw is not selectable by marquee either.
    ///
    /// Its screen bounds are a zero-size box at the origin, so before this it was caught
    /// by almost any marquee — the user could rubber-band an object they could not see, then
    /// drag it. Drawing and hit-testing now agree, via [Fact#hasUsablePlacement()].
    @Test
    void testHitTestRect_ignoresFactsWithNoUsablePlacement() {
        final Fact placeable = pointFact("visible", 0, 0);
        final Fact singular = new Fact("invisible", "t", null,
                new FloorMapTransformationMatrix(0, 0, 0, 0, 0, 0),
                new double[]{0, 0});

        assertThat(geometry(1, 0, 0)
                .hitTestRect(List.of(placeable, singular), new double[]{-40, -40, 40, 40}))
                .containsExactly("visible");
    }

    /// A wide graphic is caught by a marquee that overlaps only its outer edge.
    @Test
    void testHitTestRect_catchesTheWideGraphicsEdge() {
        final Fact fact = pointFact("f", 0, 0);
        final FloorMapScreenGeometry g =
                geometryWithLayerGraphic();
        // A thin band from x=+40..+55: outside a 60x60 square (half-width 30) but
        // inside the 120-wide graphic box (half-width 60).
        assertThat(g.hitTestRect(List.of(fact), new double[]{40, -5, 55, 5}))
                .containsExactly("f");
    }
}
