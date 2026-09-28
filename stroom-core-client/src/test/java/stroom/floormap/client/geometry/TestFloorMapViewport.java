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

import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link FloorMapViewport} — the pure pan/zoom/drag maths extracted
 * from the canvas presenter. Verifies coordinate conversions, the
 * zoom-toward-cursor invariant, scale clamping, and Edit-Mode drag deltas.
 */
class TestFloorMapViewport {

    private static final double TOLERANCE = 1e-9;

    // A non-trivial background matrix (scale + rotation + translation) so that a
    // wrong inverse produces a visibly wrong answer.
    private static final FloorMapTransformationMatrix BACKGROUND =
            new FloorMapTransformationMatrix(1.2, 0.9, -0.9, 1.2, 100, 50);

    // -----------------------------------------------------------------------
    // Round-trip: screenToMap and mapToScreen are exact inverses
    // -----------------------------------------------------------------------

    /**
     * For any viewport state and invertible background, mapping a screen point
     * to map space and back must recover the original screen point.
     */
    @TestFactory
    Stream<DynamicTest> testScreenToMapRoundTrip() {
        final double screenX = 640;
        final double screenY = 360;
        return TestUtil.buildDynamicTestStream()
                .withInputType(FloorMapViewport.class)
                .withOutputType(double[].class)
                .withTestFunction(testCase -> {
                    final FloorMapViewport viewport = testCase.getInput();
                    final double[] map = viewport.screenToMap(screenX, screenY, BACKGROUND);
                    return viewport.mapToScreen(map[0], map[1], BACKGROUND);
                })
                .withAssertions(outcome -> {
                    assertThat(outcome.getActualOutput()[0])
                            .isCloseTo(outcome.getExpectedOutput()[0], within(TOLERANCE));
                    assertThat(outcome.getActualOutput()[1])
                            .isCloseTo(outcome.getExpectedOutput()[1], within(TOLERANCE));
                })
                .addNamedCase("Default state",
                        new FloorMapViewport(), new double[]{screenX, screenY})
                .addNamedCase("Panned",
                        new FloorMapViewport(1.0, 200, -120), new double[]{screenX, screenY})
                .addNamedCase("Zoomed in",
                        new FloorMapViewport(3.5, 0, 0), new double[]{screenX, screenY})
                .addNamedCase("Zoomed out and panned",
                        new FloorMapViewport(0.25, -60, 40), new double[]{screenX, screenY})
                .build();
    }

    @Test
    void testScreenToMap_nullBackgroundTreatedAsIdentity() {
        final FloorMapViewport viewport = new FloorMapViewport(2.0, 100, 50);

        // With identity background, screenToMap is just the inverse zoom/pan.
        final double[] map = viewport.screenToMap(300, 250, null);

        assertThat(map[0]).isCloseTo((300 - 100) / 2.0, within(TOLERANCE));
        assertThat(map[1]).isCloseTo((250 - 50) / 2.0, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Zoom toward cursor
    // -----------------------------------------------------------------------

    /**
     * The defining property of zoom-toward-cursor: the map point directly under
     * the cursor must not move on screen when zooming.
     */
    @Test
    void testZoom_keepsMapPointUnderCursorFixed() {
        final FloorMapViewport viewport = new FloorMapViewport(1.0, 30, 70);
        final double cursorX = 400;
        final double cursorY = 300;

        final double[] before = viewport.screenToMap(cursorX, cursorY, BACKGROUND);
        viewport.zoom(cursorX, cursorY, true);
        final double[] after = viewport.screenToMap(cursorX, cursorY, BACKGROUND);

        assertThat(viewport.getScale()).isCloseTo(FloorMapViewport.ZOOM_STEP, within(TOLERANCE));
        assertThat(after[0]).isCloseTo(before[0], within(TOLERANCE));
        assertThat(after[1]).isCloseTo(before[1], within(TOLERANCE));
    }

    @Test
    void testZoom_inThenOutRestoresState() {
        final FloorMapViewport viewport = new FloorMapViewport(1.0, 30, 70);

        viewport.zoom(400, 300, true);
        viewport.zoom(400, 300, false);

        assertThat(viewport.getScale()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(viewport.getOffsetX()).isCloseTo(30, within(TOLERANCE));
        assertThat(viewport.getOffsetY()).isCloseTo(70, within(TOLERANCE));
    }

    @Test
    void testZoom_clampsAtMaxScale() {
        final FloorMapViewport viewport = new FloorMapViewport(
                FloorMapViewport.MAX_SCALE, 0, 0);
        viewport.zoom(0, 0, true);
        assertThat(viewport.getScale()).isEqualTo(FloorMapViewport.MAX_SCALE);
    }

    @Test
    void testZoom_clampsAtMinScale() {
        final FloorMapViewport viewport = new FloorMapViewport(
                FloorMapViewport.MIN_SCALE, 0, 0);
        viewport.zoom(0, 0, false);
        assertThat(viewport.getScale()).isEqualTo(FloorMapViewport.MIN_SCALE);
    }

    /**
     * At the scale clamp the point under the cursor must still stay fixed — the
     * offset shift uses the actually-applied ratio, so a wheel tick at the limit
     * doesn't drift the view.
     */
    @Test
    void testZoom_atClampKeepsCursorFixed() {
        final FloorMapViewport viewport = new FloorMapViewport(
                FloorMapViewport.MAX_SCALE, 30, 70);
        final double cursorX = 400;
        final double cursorY = 300;

        final double[] before = viewport.screenToMap(cursorX, cursorY, BACKGROUND);
        viewport.zoom(cursorX, cursorY, true); // already at max — clamp bites
        final double[] after = viewport.screenToMap(cursorX, cursorY, BACKGROUND);

        assertThat(viewport.getScale()).isEqualTo(FloorMapViewport.MAX_SCALE);
        assertThat(after[0]).isCloseTo(before[0], within(TOLERANCE));
        assertThat(after[1]).isCloseTo(before[1], within(TOLERANCE));
        // Offset unchanged because no scale change was actually applied.
        assertThat(viewport.getOffsetX()).isCloseTo(30, within(TOLERANCE));
        assertThat(viewport.getOffsetY()).isCloseTo(70, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Pan
    // -----------------------------------------------------------------------

    @Test
    void testPan_accumulatesOffset() {
        final FloorMapViewport viewport = new FloorMapViewport(2.0, 10, 20);
        viewport.pan(5, -8);
        viewport.pan(3, 4);

        // Pan is a raw screen delta and is independent of scale.
        assertThat(viewport.getOffsetX()).isEqualTo(18);
        assertThat(viewport.getOffsetY()).isEqualTo(16);
        assertThat(viewport.getScale()).isEqualTo(2.0);
    }

    // -----------------------------------------------------------------------
    // Item drag
    // -----------------------------------------------------------------------

    /**
     * Dragging a plotted item: a screen delta must become the map-space delta
     * that, added to the item's map coordinates, lands it exactly under the
     * cursor. We verify this against the round-trip: the map position of the
     * cursor start and end points should differ by exactly the returned delta.
     */
    @Test
    void testDragItemMapDelta_matchesScreenToMapDifference() {
        final FloorMapViewport viewport = new FloorMapViewport(1.5, 40, -30);
        final double startX = 200;
        final double startY = 150;
        final double screenDeltaX = 60;
        final double screenDeltaY = -25;

        final double[] mapStart = viewport.screenToMap(startX, startY, BACKGROUND);
        final double[] mapEnd = viewport.screenToMap(
                startX + screenDeltaX, startY + screenDeltaY, BACKGROUND);
        final double[] delta = viewport.dragItemMapDelta(BACKGROUND, screenDeltaX, screenDeltaY);

        assertThat(delta[0]).isCloseTo(mapEnd[0] - mapStart[0], within(TOLERANCE));
        assertThat(delta[1]).isCloseTo(mapEnd[1] - mapStart[1], within(TOLERANCE));
    }

    @Test
    void testDragItemMapDelta_identityBackgroundIsJustUnzoomedDelta() {
        final FloorMapViewport viewport = new FloorMapViewport(4.0, 0, 0);
        final double[] delta = viewport.dragItemMapDelta(null, 80, -40);

        assertThat(delta[0]).isCloseTo(80 / 4.0, within(TOLERANCE));
        assertThat(delta[1]).isCloseTo(-40 / 4.0, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Follow (dead-zone camera)
    // -----------------------------------------------------------------------

    private static final double VIEW_WIDTH = 800;
    private static final double VIEW_HEIGHT = 600;
    private static final double MARGIN = 0.2;

    // -----------------------------------------------------------------------
    // followDelta (static, screen-space — used by the canvas's Y-up pipeline)
    // -----------------------------------------------------------------------

    /**
     * A screen point inside the central dead zone needs no pan.
     */
    @Test
    void testFollowDelta_insideDeadZoneIsZero() {
        assertThat(FloorMapViewport.followDelta(
                VIEW_WIDTH / 2, VIEW_HEIGHT / 2, VIEW_WIDTH, VIEW_HEIGHT, MARGIN))
                .containsExactly(0.0, 0.0);
        // Exactly on the margin boundary counts as inside.
        assertThat(FloorMapViewport.followDelta(
                VIEW_WIDTH * MARGIN, VIEW_HEIGHT * MARGIN, VIEW_WIDTH, VIEW_HEIGHT, MARGIN))
                .containsExactly(0.0, 0.0);
    }

    /**
     * A point past a margin yields the minimal delta that lands it exactly on
     * the margin boundary — per axis, independently.
     */
    @Test
    void testFollowDelta_minimalDeltaPerAxis() {
        // Past the right edge only.
        final double[] right = FloorMapViewport.followDelta(
                VIEW_WIDTH - 10, VIEW_HEIGHT / 2, VIEW_WIDTH, VIEW_HEIGHT, MARGIN);
        assertThat(right[0]).isCloseTo((VIEW_WIDTH * (1 - MARGIN)) - (VIEW_WIDTH - 10),
                within(TOLERANCE));
        assertThat(right[1]).isEqualTo(0.0);

        // Above the top edge only.
        final double[] top = FloorMapViewport.followDelta(
                VIEW_WIDTH / 2, -40, VIEW_WIDTH, VIEW_HEIGHT, MARGIN);
        assertThat(top[0]).isEqualTo(0.0);
        assertThat(top[1]).isCloseTo(VIEW_HEIGHT * MARGIN + 40, within(TOLERANCE));
    }

    /**
     * The zero-size guard applies to the static form too.
     */
    @Test
    void testFollowDelta_zeroViewSizeIsZero() {
        assertThat(FloorMapViewport.followDelta(5000, 5000, 0, VIEW_HEIGHT, MARGIN))
                .containsExactly(0.0, 0.0);
        assertThat(FloorMapViewport.followDelta(5000, 5000, VIEW_WIDTH, 0, MARGIN))
                .containsExactly(0.0, 0.0);
    }

    // -----------------------------------------------------------------------
    // dampingFactor (damped camera-follow steps)
    // -----------------------------------------------------------------------

    /**
     * One time constant of elapsed time covers ~63% of the outstanding
     * correction (1 - 1/e), the defining property of exponential damping.
     */
    @Test
    void testDampingFactor_oneTimeConstant() {
        assertThat(FloorMapViewport.dampingFactor(300, 300))
                .isCloseTo(1 - Math.exp(-1), within(TOLERANCE));
    }

    /**
     * Repeated small steps converge on the same total coverage as one large
     * step of the same elapsed time — damping is frame-rate independent.
     */
    @Test
    void testDampingFactor_frameRateIndependent() {
        // Two 150ms steps: remaining fraction after each is exp(-150/300).
        final double perStep = FloorMapViewport.dampingFactor(150, 300);
        final double afterTwoSteps = 1 - (1 - perStep) * (1 - perStep);

        assertThat(afterTwoSteps)
                .isCloseTo(FloorMapViewport.dampingFactor(300, 300), within(TOLERANCE));
    }

    /**
     * The factor is a valid fraction: 0 for no elapsed time (no movement),
     * approaching 1 for long gaps, and full (1) for a degenerate non-positive
     * time constant.
     */
    @Test
    void testDampingFactor_bounds() {
        assertThat(FloorMapViewport.dampingFactor(0, 300)).isEqualTo(0);
        assertThat(FloorMapViewport.dampingFactor(-5, 300)).isEqualTo(0);
        assertThat(FloorMapViewport.dampingFactor(1_000_000, 300))
                .isCloseTo(1.0, within(1e-6));
        assertThat(FloorMapViewport.dampingFactor(16, 0)).isEqualTo(1);
    }
}
