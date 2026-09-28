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

import stroom.floormap.shared.TypeStyle.Shape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapShapes {

    @Test
    void testTriangle_pointsUp() {
        // apex at top (0,-h), base corners bottom-right and bottom-left.
        assertThat(FloorMapShapes.polygonPoints(Shape.TRIANGLE, 10))
                .isEqualTo("0.0,-10.0 10.0,10.0 -10.0,10.0");
    }

    @Test
    void testDiamond_fourPoints() {
        assertThat(FloorMapShapes.polygonPoints(Shape.DIAMOND, 10))
                .isEqualTo("0.0,-10.0 10.0,0.0 0.0,10.0 -10.0,0.0");
    }

    @Test
    void testNativeShapes_haveNoPolygon() {
        assertThat(FloorMapShapes.polygonPoints(Shape.CIRCLE, 10)).isNull();
        assertThat(FloorMapShapes.polygonPoints(Shape.SQUARE, 10)).isNull();
        assertThat(FloorMapShapes.polygonPoints(Shape.PIN, 10)).isNull();
        assertThat(FloorMapShapes.polygonPoints(null, 10)).isNull();
    }

    @Test
    void testPin_isAClosedTeardropPointingDown() {
        // Two mirrored cubics from the tip, back to the tip, then closed.
        assertThat(FloorMapShapes.pinPath(10))
                .isEqualTo("M0.0,7.5"
                           + " C-5.0,1.25 -4.75,-5.625 0.0,-5.625"
                           + " C4.75,-5.625 5.0,1.25 0.0,7.5"
                           + " Z");
    }

    @Test
    void testPin_scalesWithHalfSize() {
        // The tip sits at +0.75 * halfSize in the y-down glyph frame, so the pin
        // grows proportionally rather than at a fixed pixel size.
        assertThat(FloorMapShapes.pinPath(20)).startsWith("M0.0,15.0");
        assertThat(FloorMapShapes.pinPath(10)).startsWith("M0.0,7.5");
    }

    @Test
    void testPin_holeSitsInsideTheBulb() {
        final double halfSize = 10;
        final double centreY = halfSize * FloorMapShapes.PIN_HOLE_CENTRE_Y_RATIO;
        final double radius = halfSize * FloorMapShapes.PIN_HOLE_RADIUS_RATIO;

        // Above centre (negative y is up), and wholly within the bulb, whose top
        // edge is at -0.5625 * halfSize.
        assertThat(centreY).isLessThan(0);
        assertThat(radius).isGreaterThan(0);
        assertThat(centreY - radius).isGreaterThan(-0.5625 * halfSize);
        assertThat(centreY + radius).isLessThan(0.75 * halfSize);
    }
}
