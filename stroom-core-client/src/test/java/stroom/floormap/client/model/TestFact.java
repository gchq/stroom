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

package stroom.floormap.client.model;

import stroom.floormap.shared.FloorMapGeometry;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFact {

    private static final double IMAGE_DISPLAY_WIDTH = 1000;

    // -----------------------------------------------------------------------
    // mapAnchor — the camera-centre point for a tracked fact
    // -----------------------------------------------------------------------

    /**
     * A point fact anchors at its position pushed through the placement
     * matrix.
     */
    @Test
    void testMapAnchorPointFact() {
        final Fact fact = new Fact("gate-1", "gate", null,
                FloorMapTransformationMatrix.translate(1, 2),
                new double[]{5, 7});

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, null))
                .containsExactly(6, 9);
    }

    /**
     * A point fact without a position anchors at the placement matrix's
     * origin.
     */
    @Test
    void testMapAnchorPositionlessFact() {
        final Fact fact = new Fact("gate-1", "gate", null,
                FloorMapTransformationMatrix.translate(3, 4),
                null);

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, null))
                .containsExactly(3, 4);
    }

    /**
     * An area fact anchors at its local vertex centroid pushed through the
     * placement matrix. (Area vertices are stored centroid-local, so the
     * centroid of a symmetric polygon is the local origin.)
     */
    @Test
    void testMapAnchorAreaFact() {
        final Fact fact = new Fact("zone-a", "area", null,
                FloorMapTransformationMatrix.translate(50, 60),
                null,
                new double[][]{{-10, -10}, {10, -10}, {10, 10}, {-10, 10}},
                null, null);

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, null))
                .containsExactly(50, 60);
    }

    /**
     * An off-centre polygon anchors at its true centroid, not the local
     * origin.
     */
    @Test
    void testMapAnchorAreaFactOffCentre() {
        final Fact fact = new Fact("zone-b", "area", null,
                FloorMapTransformationMatrix.identity(),
                null,
                new double[][]{{0, 0}, {12, 0}, {12, 6}, {0, 6}},
                null, null);

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, null))
                .containsExactly(6, 3);
    }

    /**
     * An image fact anchors at the centre of the placed image rectangle:
     * width is the fixed display width, height follows the aspect ratio, and
     * the render wrapper transform reduces to {@code worldToMap · (w/2, h/2)}.
     */
    @Test
    void testMapAnchorImageFact() {
        final Fact fact = new Fact("background", "background", "img.png",
                FloorMapTransformationMatrix.translate(100, 200),
                null);

        // Aspect 2.0 → h = 500 → local centre (500, 250).
        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, 2.0))
                .containsExactly(600, 450);
    }

    /**
     * An image whose aspect ratio is not yet known is treated as square,
     * matching the renderer's pre-load fallback.
     */
    @Test
    void testMapAnchorImageFactDefaultAspect() {
        final Fact fact = new Fact("background", "background", "img.png",
                FloorMapTransformationMatrix.translate(100, 200),
                null);

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, null))
                .containsExactly(600, 700);
    }

    /**
     * Dispatch matches the renderer: a fact carrying both an image and
     * vertices anchors as an image.
     */
    @Test
    void testMapAnchorImageWinsOverVertices() {
        final Fact fact = new Fact("hybrid", "object", "img.png",
                FloorMapTransformationMatrix.identity(),
                null,
                new double[][]{{-10, -10}, {10, -10}, {10, 10}},
                null, null);

        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, 1.0))
                .containsExactly(500, 500);
    }

    // -----------------------------------------------------------------------
    // label
    // -----------------------------------------------------------------------

    /** The narrower constructors leave the label unset. */
    @Test
    void testLabelDefaultsToNull() {
        assertThat(new Fact("k", "gate", null,
                FloorMapTransformationMatrix.identity(), null).getLabel()).isNull();
        assertThat(new Fact("k", "gate", null,
                FloorMapTransformationMatrix.identity(), null,
                null, null, null).getLabel()).isNull();
    }

    @Test
    void testLabelRoundTrips() {
        final Fact fact = new Fact("k", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                null, null, null, "Loading Bay");

        assertThat(fact.getLabel()).isEqualTo("Loading Bay");
        assertThat(fact.getLabelOrNull()).isEqualTo("Loading Bay");
    }

    /** A blank label is treated as absent, so callers fall back to the key. */
    @Test
    void testGetLabelOrNullTreatsBlankAsAbsent() {
        assertThat(new Fact("k", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                null, null, null, "   ").getLabelOrNull()).isNull();
        assertThat(new Fact("k", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                null, null, null, "").getLabelOrNull()).isNull();
    }

    /**
     * The transform/vertex-preview copies carry the label — a live drag must not
     * silently rename an area in the tracking panel.
     */
    @Test
    void testLabelSurvivesCopies() {
        final Fact fact = new Fact("k", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{-5, -5}, {5, -5}, {5, 5}}, null, null, "Loading Bay");

        assertThat(fact.withWorldToMap(FloorMapTransformationMatrix.translate(10, 10))
                .getLabel()).isEqualTo("Loading Bay");
        assertThat(fact.withVertices(new double[][]{{-1, -1}, {1, -1}, {1, 1}})
                .getLabel()).isEqualTo("Loading Bay");
    }

    /**
     * A singular placement matrix is reported as unusable, so the renderer and hit-testing
     * both refuse the fact instead of disagreeing about it.
     */
    @Test
    void testHasUsablePlacement() {
        assertThat(new Fact("ok", "t", null, FloorMapTransformationMatrix.identity(),
                new double[]{1, 2}).hasUsablePlacement()).isTrue();

        // The all-zero matrix: an image emits matrix(0,0,0,0,0,0) and is not drawn by the
        // browser, while a point transforms to (0,0) whatever its own coords say.
        assertThat(new Fact("zero", "t", null,
                new FloorMapTransformationMatrix(0, 0, 0, 0, 0, 0),
                new double[]{1, 2}).hasUsablePlacement()).isFalse();

        // Collapsed on one axis only - still singular, still cannot place a 2D shape.
        assertThat(new Fact("flat", "t", null,
                new FloorMapTransformationMatrix(1, 0, 0, 0, 0, 0),
                new double[]{1, 2}).hasUsablePlacement()).isFalse();

        // A translation-only matrix is invertible, so it stays usable.
        assertThat(new Fact("moved", "t", null,
                new FloorMapTransformationMatrix(1, 0, 0, 1, 50, 50),
                new double[]{1, 2}).hasUsablePlacement()).isTrue();
    }

    // -----------------------------------------------------------------------
    // vertex validation
    // -----------------------------------------------------------------------

    /** Well-formed vertices are copied defensively, not shared with the caller. */
    @Test
    void testVerticesAreDefensivelyCopied() {
        final double[][] source = new double[][]{{-5, -5}, {5, -5}, {5, 5}};
        final Fact fact = new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                source, null, null);

        source[0][0] = 999;
        assertThat(fact.getVertices()[0]).containsExactly(-5, -5);

        final double[][] returned = fact.getVertices();
        returned[1][1] = 999;
        assertThat(fact.getVertices()[1]).containsExactly(5, -5);
    }

    /** A null vertex is a caller bug, not geometry, so construction fails loudly. */
    @Test
    void testNullVertexIsRejected() {
        assertThatThrownBy(() -> new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{-5, -5}, null, {5, 5}}, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex 1")
                .hasMessageContaining("null");
    }

    /** A vertex holding anything other than an [x, y] pair is rejected. */
    @Test
    void testMalformedVertexIsRejected() {
        assertThatThrownBy(() -> new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{-5, -5}, {5}, {5, 5}}, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex 1")
                .hasMessageContaining("1 element");

        assertThatThrownBy(() -> new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{-5, -5, 0}, {5, -5}, {5, 5}}, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex 0")
                .hasMessageContaining("3 element");

        assertThatThrownBy(() -> new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{}, {5, -5}, {5, 5}}, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex 0");
    }

    /** The vertex-preview copy validates its replacement geometry too. */
    @Test
    void testWithVerticesRejectsMalformedVertices() {
        final Fact fact = new Fact("zone", "area", null,
                FloorMapTransformationMatrix.identity(), null,
                new double[][]{{-5, -5}, {5, -5}, {5, 5}}, null, null);

        assertThatThrownBy(() -> fact.withVertices(new double[][]{{-1, -1}, {1}, {1, 1}}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex 1");
    }

    /** No vertices at all remains legitimate — a point fact simply has none. */
    @Test
    void testNullVerticesAreAccepted() {
        final Fact fact = new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{1, 2},
                null, null, null);

        assertThat(fact.getVertices()).isNull();
        assertThat(fact.hasVertices()).isFalse();
    }

    /// A fact with vertices and no image is an area.
    @Test
    void testIsArea_verticesWithoutImage() {
        final Fact fact = new Fact("zone", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.identity(), new double[]{0, 0},
                new double[][]{{-5, -5}, {5, -5}, {5, 5}}, null, null);

        assertThat(fact.isArea()).isTrue();
    }

    /// A fact carrying its own image renders as that image, so is not an area.
    @Test
    void testIsArea_imageWithVertices() {
        final Fact fact = new Fact("odd", FloorMapJsonKeys.AREA, "/assets/x.png",
                FloorMapTransformationMatrix.identity(), new double[]{0, 0},
                new double[][]{{-5, -5}, {5, -5}, {5, 5}}, null, null);

        assertThat(fact.isArea()).isFalse();
    }

    /// A point fact has no vertices, so is not an area.
    @Test
    void testIsArea_noVertices() {
        final Fact fact = new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{1, 2},
                null, null, null);

        assertThat(fact.isArea()).isFalse();
    }

    // -----------------------------------------------------------------------
    // position validation
    // -----------------------------------------------------------------------

    /** A position is copied defensively, not shared with the caller. */
    @Test
    void testPositionIsDefensivelyCopied() {
        final double[] source = new double[]{5, 7};
        final Fact fact = new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), source);

        source[0] = 999;
        assertThat(fact.getPosition()).containsExactly(5, 7);

        fact.getPosition()[1] = 999;
        assertThat(fact.getPosition()).containsExactly(5, 7);
    }

    /** A position that is not an [x, y] pair is a caller bug, so it is rejected. */
    @Test
    void testMalformedPositionIsRejected() {
        assertThatThrownBy(() -> new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{5}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position")
                .hasMessageContaining("1 element");

        assertThatThrownBy(() -> new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{5, 7, 9}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position")
                .hasMessageContaining("3 element");

        assertThatThrownBy(() -> new Fact("gate", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{},
                null, null, null, "Gate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position");
    }

    /** No position at all remains legitimate — an area or background has none. */
    @Test
    void testNullPositionIsAccepted() {
        final Fact fact = new Fact("bg", "background", "img.png",
                FloorMapTransformationMatrix.identity(), null);

        assertThat(fact.getPosition()).isNull();
    }

    // -----------------------------------------------------------------------
    // toMapVertices — the load-bearing local-frame → map-space step
    // -----------------------------------------------------------------------

    /**
     * Area vertices are stored centred on the centroid in a local frame and
     * placed by the matrix, so an untransformed comparison is meaningless: the
     * same local square must test as containing quite different map points once
     * translated.
     */
    @Test
    void testToMapVerticesTranslated() {
        final double[][] local = new double[][]{{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        final Fact area = new Fact("a1", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(100, 200),
                new double[]{0, 0}, local, null, null);

        final double[][] mapVertices = area.toMapVertices();

        assertThat(FloorMapGeometry.aabb(mapVertices)).containsExactly(95, 195, 105, 205);
        assertThat(FloorMapGeometry.contains(mapVertices, 100, 200)).isTrue();
        // The local-frame origin is NOT inside the placed polygon.
        assertThat(FloorMapGeometry.contains(mapVertices, 0, 0)).isFalse();
    }

    @Test
    void testToMapVerticesScaled() {
        final double[][] local = new double[][]{{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        final Fact area = new Fact("a1", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.scale(2, 2),
                new double[]{0, 0}, local, null, null);

        final double[][] mapVertices = area.toMapVertices();

        assertThat(FloorMapGeometry.area(mapVertices)).isEqualTo(400.0);
        assertThat(FloorMapGeometry.contains(mapVertices, 9, 9)).isTrue();
        assertThat(FloorMapGeometry.contains(mapVertices, 11, 11)).isFalse();
    }

    @Test
    void testToMapVerticesNonArea() {
        final Fact point = new Fact("p1", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{1, 2});
        assertThat(point.toMapVertices()).isNull();
    }

    // -----------------------------------------------------------------------
    // mapTestPoint
    // -----------------------------------------------------------------------

    /** A point fact is located by its position through its own matrix. */
    @Test
    void testMapTestPointPositionFact() {
        final Fact gate = new Fact("g1", "gate", null,
                FloorMapTransformationMatrix.translate(10, 20), new double[]{3, 4});
        assertThat(gate.mapTestPoint()).containsExactly(13, 24);
    }

    /** An area is located by its local centroid through its matrix. */
    @Test
    void testMapTestPointArea() {
        final double[][] local = new double[][]{{-2, -2}, {2, -2}, {2, 2}, {-2, 2}};
        final Fact area = new Fact("a1", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(50, 60),
                new double[]{0, 0}, local, null, null);
        assertThat(area.mapTestPoint()).containsExactly(50, 60);
    }

    /** A fact with no position falls back to its matrix origin. */
    @Test
    void testMapTestPointNoPosition() {
        final Fact fact = new Fact("f1", "gate", null,
                FloorMapTransformationMatrix.translate(7, 8), null);
        assertThat(fact.mapTestPoint()).containsExactly(7, 8);
    }

    /**
     * An image fact is tested at its placement, not the centre of the drawn image:
     * containment must not depend on the view's image width. This is where
     * {@code mapTestPoint} and {@code mapAnchor} deliberately differ.
     */
    @Test
    void testMapTestPointImageFactIgnoresImageSize() {
        final Fact fact = new Fact("plan", "background", "plan.png",
                FloorMapTransformationMatrix.translate(7, 8), new double[]{1, 2});
        assertThat(fact.mapTestPoint()).containsExactly(8, 10);
        assertThat(fact.mapAnchor(IMAGE_DISPLAY_WIDTH, 2.0)).containsExactly(507, 258);
    }
}
