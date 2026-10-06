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

package stroom.floormap.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapGeometry {

    /// A 10x10 square with its lower-left corner at the origin.
    private static double[][] square() {
        return new double[][]{{0, 0}, {10, 0}, {10, 10}, {0, 10}};
    }

    /// A concave "C" shape opening to the right, spanning 0..10 in both axes.
    private static double[][] concave() {
        return new double[][]{
                {0, 0}, {10, 0}, {10, 3}, {3, 3}, {3, 7}, {10, 7}, {10, 10}, {0, 10}};
    }

    // -----------------------------------------------------------------------
    // contains
    // -----------------------------------------------------------------------

    @Test
    void testContainsCentre() {
        assertThat(FloorMapGeometry.contains(square(), 5, 5)).isTrue();
    }

    @Test
    void testContainsOutside() {
        assertThat(FloorMapGeometry.contains(square(), 15, 5)).isFalse();
        assertThat(FloorMapGeometry.contains(square(), 5, -1)).isFalse();
        assertThat(FloorMapGeometry.contains(square(), -0.001, 5)).isFalse();
    }

    /// The AABB prefilter must reject a far-away point without consulting the
    /// edges — same answer, and the path most calls take.
    @Test
    void testContainsFarAway() {
        assertThat(FloorMapGeometry.contains(square(), 1000, 1000)).isFalse();
    }

    /// A concave polygon's notch is outside even though it is inside the
    /// bounding box — the case an AABB-only test would get wrong.
    @Test
    void testContainsConcaveNotch() {
        assertThat(FloorMapGeometry.contains(concave(), 6, 5)).isFalse();
        // ...while the arms of the "C" either side of the notch are inside.
        assertThat(FloorMapGeometry.contains(concave(), 6, 1.5)).isTrue();
        assertThat(FloorMapGeometry.contains(concave(), 6, 8.5)).isTrue();
        assertThat(FloorMapGeometry.contains(concave(), 1.5, 5)).isTrue();
    }

    /// Winding direction must not change the answer — the editor can produce
    /// either depending on which way the user drew the polygon.
    @Test
    void testContainsIgnoresWindingDirection() {
        final double[][] clockwise = new double[][]{{0, 0}, {0, 10}, {10, 10}, {10, 0}};
        assertThat(FloorMapGeometry.contains(clockwise, 5, 5)).isTrue();
        assertThat(FloorMapGeometry.contains(clockwise, 15, 5)).isFalse();
    }

    @Test
    void testContainsDegenerate() {
        assertThat(FloorMapGeometry.contains(null, 5, 5)).isFalse();
        assertThat(FloorMapGeometry.contains(new double[][]{}, 5, 5)).isFalse();
        assertThat(FloorMapGeometry.contains(new double[][]{{0, 0}, {10, 10}}, 5, 5)).isFalse();
    }

    // -----------------------------------------------------------------------
    // aabb / area
    // -----------------------------------------------------------------------

    @Test
    void testAabb() {
        assertThat(FloorMapGeometry.aabb(square())).containsExactly(0, 0, 10, 10);
    }

    @Test
    void testAabbEmpty() {
        assertThat(FloorMapGeometry.aabb(null)).isNull();
        assertThat(FloorMapGeometry.aabb(new double[][]{})).isNull();
    }

    @Test
    void testArea() {
        assertThat(FloorMapGeometry.area(square())).isEqualTo(100.0);
        // Winding direction must not flip the sign — the value is unsigned.
        final double[][] clockwise = new double[][]{{0, 0}, {0, 10}, {10, 10}, {10, 0}};
        assertThat(FloorMapGeometry.area(clockwise)).isEqualTo(100.0);
    }

    @Test
    void testAreaTriangle() {
        assertThat(FloorMapGeometry.area(new double[][]{{0, 0}, {4, 0}, {0, 3}}))
                .isEqualTo(6.0);
    }

    @Test
    void testAreaDegenerate() {
        assertThat(FloorMapGeometry.area(null)).isEqualTo(0.0);
        assertThat(FloorMapGeometry.area(new double[][]{{0, 0}, {1, 1}})).isEqualTo(0.0);
    }

    // -----------------------------------------------------------------------
    // centroid
    // -----------------------------------------------------------------------

    @Test
    void testCentroidSquare() {
        assertThat(FloorMapGeometry.centroid(square())).containsExactly(5, 5);
    }

    /// The vertex mean, not the area-weighted centroid: extra vertices pull it.
    @Test
    void testCentroidIsVertexMean() {
        final double[][] polygon = new double[][]{{0, 0}, {3, 0}, {6, 0}, {0, 6}};
        assertThat(FloorMapGeometry.centroid(polygon)).containsExactly(2.25, 1.5);
    }

    @Test
    void testCentroidSkipsUnusableRows() {
        final double[][] polygon = new double[][]{{0, 0}, null, {4, 0}, {9}, {4, 4}};
        assertThat(FloorMapGeometry.centroid(polygon)).containsExactly(8.0 / 3, 4.0 / 3);
    }

    @Test
    void testCentroidNoUsableVertices() {
        //noinspection ConstantValue
        assertThat(FloorMapGeometry.centroid(null)).isNull();
        assertThat(FloorMapGeometry.centroid(new double[0][])).isNull();
        assertThat(FloorMapGeometry.centroid(new double[][]{null, {1}})).isNull();
    }

    // -----------------------------------------------------------------------
    // distance
    // -----------------------------------------------------------------------

    @Test
    void testDistance() {
        assertThat(FloorMapGeometry.distance(1, 2, 4, 6)).isEqualTo(5.0);
        assertThat(FloorMapGeometry.distance(4, 6, 1, 2)).isEqualTo(5.0);
        assertThat(FloorMapGeometry.distance(3, 3, 3, 3)).isEqualTo(0.0);
    }

    @Test
    void testDistanceSquared() {
        assertThat(FloorMapGeometry.distanceSquared(1, 2, 4, 6)).isEqualTo(25.0);
        assertThat(FloorMapGeometry.distanceSquared(-1, -1, 1, 1)).isEqualTo(8.0);
    }
}
