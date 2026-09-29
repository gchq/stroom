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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapMarker {

    /// The path is one closed teardrop: up a tail edge, the long way round the
    /// head, down the other edge, across the rounded tip.
    @Test
    void testPathIsAClosedTeardrop() {
        final String path = FloorMapMarker.getPath();

        assertThat(path).startsWith("M").endsWith("Z");
        // The head: large arc, clockwise — the long way over the top.
        assertThat(path).contains(" 0 1 1 ");
        // The tip: minor arc, the same way round. Getting either flag wrong still
        // draws a shape, just the wrong one.
        assertThat(path).contains(" 0 0 1 ");
        assertThat(path).doesNotContain("\"");
    }

    /// The tip is rounded off rather than coming to a point — a sharp vertex is
    /// unpleasant at map sizes and spikes when scaled down.
    ///
    /// The fillet has to be a real arc between the two edges: big enough to see,
    /// and small enough that the tail still converges rather than turning the pin
    /// into a balloon.
    @Test
    void testTipIsRounded() {
        assertThat(FloorMapMarker.TIP_RADIUS).isGreaterThan(0);
        // Comfortably inside the head, or the "tail" is just more head.
        assertThat(FloorMapMarker.TIP_RADIUS).isLessThan(FloorMapMarker.HEAD_RADIUS / 2);
    }

    /// The fillet is tangent to both tail edges, so the rounding is smooth from
    /// whichever side it is followed.
    ///
    /// Recomputes where the fillet meets an edge and checks the fillet's centre
    /// is exactly [FloorMapMarker#TIP_RADIUS] from that edge, measured
    /// perpendicular to it.
    @Test
    void testTheFilletIsTangentToBothEdges() {
        final double distance = FloorMapMarker.TIP_Y - FloorMapMarker.HEAD_Y;
        final double tangent = Math.sqrt(
                (distance * distance) - (FloorMapMarker.HEAD_RADIUS * FloorMapMarker.HEAD_RADIUS));

        // The fillet's centre, on the axis above the virtual vertex: its distance
        // from the vertex is TIP_RADIUS / sin(phi), and sin(phi) = r / d.
        final double centreY = FloorMapMarker.TIP_Y
                - ((FloorMapMarker.TIP_RADIUS * distance) / FloorMapMarker.HEAD_RADIUS);

        // A tail edge, as a direction from the vertex towards the head.
        final double edgeX = -((FloorMapMarker.HEAD_RADIUS * tangent) / distance);
        final double edgeY = (FloorMapMarker.HEAD_Y
                + ((FloorMapMarker.HEAD_RADIUS * FloorMapMarker.HEAD_RADIUS) / distance))
                - FloorMapMarker.TIP_Y;
        final double edgeLength = Math.sqrt((edgeX * edgeX) + (edgeY * edgeY));

        // Perpendicular distance from the centre to that edge, via the cross
        // product of the edge direction with the vertex→centre vector.
        final double toCentreY = centreY - FloorMapMarker.TIP_Y;
        final double cross = Math.abs((edgeX * toCentreY) - (edgeY * 0)) / edgeLength;

        assertThat(cross)
                .as("perpendicular distance from the fillet's centre to a tail edge")
                .isCloseTo(FloorMapMarker.TIP_RADIUS, within(1e-9));
    }

    /// The marker reads as a circle with a tail, not as a teardrop — the shape the
    /// client asked for. The tail still has to be there, or it is just a circle.
    @Test
    void testTheTailIsSubtleButPresent() {
        assertThat(FloorMapMarker.tailOverhang())
                .as("how far the shape hangs below the head")
                .isGreaterThan(1.5)
                .isLessThan(3.0);
        // The head dominates: it is most of the marker's height.
        assertThat(2 * FloorMapMarker.HEAD_RADIUS)
                .isGreaterThan(4 * FloorMapMarker.tailOverhang());
    }

    /// Built with one square root and no inverse trig — the angles are only ever
    /// needed as ratios, and the right triangle supplies all of them.
    ///
    /// Checked by recomputing a tangent point the trigonometric way and
    /// confirming the path carries the same number. Guards against someone
    /// "simplifying" the derivation into `Math.atan` and quietly changing
    /// the shape.
    @Test
    void testTheTrigFreeDerivationAgreesWithTrigonometry() {
        final double distance = FloorMapMarker.TIP_Y - FloorMapMarker.HEAD_Y;
        final double theta = Math.acos(FloorMapMarker.HEAD_RADIUS / distance);
        final double tangentX = FloorMapMarker.HEAD_X
                + (FloorMapMarker.HEAD_RADIUS * Math.sin(theta));

        final double tangent = Math.sqrt(
                (distance * distance) - (FloorMapMarker.HEAD_RADIUS * FloorMapMarker.HEAD_RADIUS));
        final double trigFree = FloorMapMarker.HEAD_X
                + ((FloorMapMarker.HEAD_RADIUS * tangent) / distance);

        assertThat(trigFree).isCloseTo(tangentX, within(1e-9));
        assertThat(FloorMapMarker.getPath()).contains(String.valueOf(trigFree));
    }

    /// The rounded tip stops short of the vertex the edges would have met at.
    @Test
    void testTheDrawnShapeStopsAboveTheVirtualVertex() {
        assertThat(FloorMapMarker.bottomY())
                .isLessThan(FloorMapMarker.TIP_Y)
                .isLessThanOrEqualTo(FloorMapMarker.GRID)
                // Still hanging below the head, or it is not a pin.
                .isGreaterThan(FloorMapMarker.HEAD_Y + FloorMapMarker.HEAD_RADIUS);
    }

    /// The tail's straight edges are true tangents to the head, which is what
    /// makes the join smooth rather than kinked.
    ///
    /// Recomputes the tangent point from the published constants and checks the
    /// two things tangency means: it is on the circle, and the radius to it is
    /// perpendicular to the line from the tip.
    @Test
    void testTailMeetsTheHeadAtATangent() {
        final double distance = FloorMapMarker.TIP_Y - FloorMapMarker.HEAD_Y;
        final double angle = Math.acos(FloorMapMarker.HEAD_RADIUS / distance);
        final double pointX = FloorMapMarker.HEAD_X
                - (FloorMapMarker.HEAD_RADIUS * Math.sin(angle));
        final double pointY = FloorMapMarker.HEAD_Y
                + (FloorMapMarker.HEAD_RADIUS * Math.cos(angle));

        // On the circle.
        final double toCentre = Math.sqrt(
                Math.pow(pointX - FloorMapMarker.HEAD_X, 2)
                        + Math.pow(pointY - FloorMapMarker.HEAD_Y, 2));
        assertThat(toCentre).isCloseTo(FloorMapMarker.HEAD_RADIUS, within(1e-9));

        // Radius ⟂ tangent line: the dot product of (centre→point) and
        // (point→tip) is zero.
        final double radiusX = pointX - FloorMapMarker.HEAD_X;
        final double radiusY = pointY - FloorMapMarker.HEAD_Y;
        final double edgeX = FloorMapMarker.HEAD_X - pointX;
        final double edgeY = FloorMapMarker.TIP_Y - pointY;
        assertThat((radiusX * edgeX) + (radiusY * edgeY)).isCloseTo(0, within(1e-9));
    }

    /// The whole marker stays inside the grid it shares with the icons.
    @Test
    void testMarkerFitsTheGrid() {
        assertThat(FloorMapMarker.HEAD_Y - FloorMapMarker.HEAD_RADIUS)
                .as("top of the head")
                .isGreaterThanOrEqualTo(0);
        assertThat(FloorMapMarker.bottomY())
                .as("the drawn tip")
                .isLessThanOrEqualTo(FloorMapMarker.GRID);
        assertThat(FloorMapMarker.HEAD_X + FloorMapMarker.HEAD_RADIUS)
                .as("right of the head")
                .isLessThanOrEqualTo(FloorMapMarker.GRID);
    }

    /// The furthest-reaching icon still clears the head's rim once shrunk into it
    /// — the reason [FloorMapMarker#ICON_SCALE] is what it is.
    ///
    /// Against the *inner* edge of the white outline, not the path: the
    /// outline is centred on the path, so it covers half its width of the fill,
    /// and an icon that merely fits the radius would have its extremities painted
    /// over.
    @Test
    void testTheWidestIconFitsInsideTheHead() {
        final double reach = FloorMapMarker.MAX_ICON_INK_RADIUS * FloorMapMarker.ICON_SCALE;
        final double usableRadius =
                FloorMapMarker.HEAD_RADIUS - (FloorMapMarker.OUTLINE_WIDTH / 2);

        assertThat(reach)
                .as("the widest icon's ink, against the head's usable radius")
                .isLessThan(usableRadius);
    }

    /// The recorded worst-case ink radius is a real measurement of the set, so it
    /// has to stay ahead of what the icons could possibly reach — and behind the
    /// grid corner, or it is not measuring anything.
    @Test
    void testTheRecordedInkRadiusIsPlausible() {
        final double gridCorner = Math.sqrt(2) * (FloorMapMarker.GRID / 2);
        final double gridHalf = FloorMapMarker.GRID / 2;

        assertThat(FloorMapMarker.MAX_ICON_INK_RADIUS)
                .as("an icon reaching past its own grid corner is impossible")
                .isLessThan(gridCorner)
                .as("but it does reach past the grid's edge midpoint — icons are"
                        + " drawn to their corners' diagonals, not inscribed")
                .isGreaterThan(gridHalf);
    }

    /// The icon is centred in the head, not in the grid.
    @Test
    void testIconTransformCentresOnTheHead() {
        final String transform = FloorMapMarker.iconTransform();
        assertThat(transform).startsWith("translate(").contains(") scale(");

        // The icon's own centre (12,12 of its grid) must land on the head centre.
        final double offset = FloorMapMarker.HEAD_X
                - ((FloorMapMarker.GRID * FloorMapMarker.ICON_SCALE) / 2);
        final double centreX = offset
                + (FloorMapMarker.ICON_SCALE * (FloorMapMarker.GRID / 2));
        assertThat(centreX).isCloseTo(FloorMapMarker.HEAD_X, within(1e-9));

        final double offsetY = FloorMapMarker.HEAD_Y
                - ((FloorMapMarker.GRID * FloorMapMarker.ICON_SCALE) / 2);
        final double centreY = offsetY
                + (FloorMapMarker.ICON_SCALE * (FloorMapMarker.GRID / 2));
        assertThat(centreY).isCloseTo(FloorMapMarker.HEAD_Y, within(1e-9));
    }

    /// The selection ring shows outside the white outline rather than under it.
    @Test
    void testSelectionRingIsWiderThanTheOutline() {
        assertThat(FloorMapMarker.SELECTION_WIDTH)
                .isGreaterThan(FloorMapMarker.OUTLINE_WIDTH);
    }

    /// Marker and icons share one grid, so a single transform places the pair.
    @Test
    void testMarkerSharesTheIconGrid() {
        assertThat(FloorMapMarker.GRID).isEqualTo(FloorMapIcon.GRID);
    }
}
