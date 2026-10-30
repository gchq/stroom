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

package stroom.query.client.result.vis;

import stroom.query.client.result.vis.VisTooltipPlacement.Direction;
import stroom.query.client.result.vis.VisTooltipPlacement.Placement;
import stroom.widget.util.client.Rect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisTooltipPlacement {

    private static final double VIEW_WIDTH = 1000;
    private static final double VIEW_HEIGHT = 800;
    private static final double TIP_WIDTH = 100;
    private static final double TIP_HEIGHT = 40;
    private static final double GAP = VisTooltipPlacement.GAP;
    private static final double MARGIN = VisTooltipPlacement.MARGIN;

    // A 20 x 20 element in the middle of the window, with room for the tooltip on every side
    private static final Rect MIDDLE = rect(400, 300, 20, 20);

    @Test
    void testEastWhenThereIsRoom() {
        final Placement placement = place(MIDDLE, Direction.EAST);

        assertThat(placement.direction()).isEqualTo(Direction.EAST);
        assertThat(placement.left()).isEqualTo(420 + GAP);
        // Centred on the element's middle
        assertThat(placement.top()).isEqualTo(310 - (TIP_HEIGHT / 2));
    }

    @Test
    void testWestWhenThereIsRoom() {
        final Placement placement = place(MIDDLE, Direction.WEST);

        assertThat(placement.direction()).isEqualTo(Direction.WEST);
        assertThat(placement.left()).isEqualTo(400 - GAP - TIP_WIDTH);
        assertThat(placement.top()).isEqualTo(310 - (TIP_HEIGHT / 2));
    }

    @Test
    void testNorthWhenThereIsRoom() {
        final Placement placement = place(MIDDLE, Direction.NORTH);

        assertThat(placement.direction()).isEqualTo(Direction.NORTH);
        assertThat(placement.left()).isEqualTo(410 - (TIP_WIDTH / 2));
        assertThat(placement.top()).isEqualTo(300 - GAP - TIP_HEIGHT);
    }

    @Test
    void testSouthWhenThereIsRoom() {
        final Placement placement = place(MIDDLE, Direction.SOUTH);

        assertThat(placement.direction()).isEqualTo(Direction.SOUTH);
        assertThat(placement.left()).isEqualTo(410 - (TIP_WIDTH / 2));
        assertThat(placement.top()).isEqualTo(320 + GAP);
    }

    @Test
    void testNoPreferenceIsEast() {
        assertThat(place(MIDDLE, null).direction()).isEqualTo(Direction.EAST);
    }

    @Test
    void testFlipsWestAtTheRightEdge() {
        final Rect nearRight = rect(950, 300, 20, 20);

        final Placement placement = place(nearRight, Direction.EAST);

        assertThat(placement.direction()).isEqualTo(Direction.WEST);
        assertThat(placement.left()).isEqualTo(950 - GAP - TIP_WIDTH);
    }

    @Test
    void testFlipsEastAtTheLeftEdge() {
        final Rect nearLeft = rect(10, 300, 20, 20);

        assertThat(place(nearLeft, Direction.WEST).direction()).isEqualTo(Direction.EAST);
    }

    @Test
    void testFlipsSouthAtTheTop() {
        final Rect nearTop = rect(400, 10, 20, 20);

        final Placement placement = place(nearTop, Direction.NORTH);

        assertThat(placement.direction()).isEqualTo(Direction.SOUTH);
        assertThat(placement.top()).isEqualTo(30 + GAP);
    }

    @Test
    void testFlipsNorthAtTheBottom() {
        final Rect nearBottom = rect(400, 770, 20, 20);

        assertThat(place(nearBottom, Direction.SOUTH).direction()).isEqualTo(Direction.NORTH);
    }

    @Test
    void testMovesToTheOtherAxisWhenNeitherSideFits() {
        // As wide as the window, so neither east nor west has room, but there is room below
        final Rect fullWidth = rect(0, 300, VIEW_WIDTH, 20);

        final Placement placement = place(fullWidth, Direction.EAST);

        assertThat(placement.direction()).isEqualTo(Direction.SOUTH);
        assertThat(placement.top()).isEqualTo(320 + GAP);
    }

    @Test
    void testClampsTheCrossAxisIntoTheWindow() {
        // East has room, but centring on an element at the very top would put the tooltip
        // above the window
        final Rect topCorner = rect(400, 0, 20, 10);

        final Placement placement = place(topCorner, Direction.EAST);

        assertThat(placement.direction()).isEqualTo(Direction.EAST);
        assertThat(placement.top()).isEqualTo(MARGIN);
    }

    @Test
    void testKeepsThePreferredSideAndClampsWhenNothingFits() {
        // Covers the whole window, so there is no room on any side
        final Rect wholeWindow = rect(0, 0, VIEW_WIDTH, VIEW_HEIGHT);

        final Placement placement = place(wholeWindow, Direction.EAST);

        assertThat(placement.direction()).isEqualTo(Direction.EAST);
        assertThat(placement.left()).isEqualTo(VIEW_WIDTH - TIP_WIDTH - MARGIN);
        assertThat(placement.top()).isEqualTo((VIEW_HEIGHT / 2) - (TIP_HEIGHT / 2));
    }

    @Test
    void testTooltipBiggerThanTheWindowIsPutAgainstItsStart() {
        final Placement placement = VisTooltipPlacement.place(
                MIDDLE,
                VIEW_WIDTH * 2,
                VIEW_HEIGHT * 2,
                VIEW_WIDTH,
                VIEW_HEIGHT,
                Direction.EAST);

        assertThat(placement.left()).isEqualTo(MARGIN);
        assertThat(placement.top()).isEqualTo(MARGIN);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "n, NORTH",
            "s, SOUTH",
            "e, EAST",
            "w, WEST",
            "N, NORTH",
            "ne, NORTH",
            "sw, SOUTH",
            "x, null",
            "'', null",
            "null, null"
    }, nullValues = "null")
    void testDirectionFromCode(final String code, final Direction expected) {
        assertThat(Direction.fromCode(code)).isEqualTo(expected);
    }

    private static Placement place(final Rect anchor, final Direction direction) {
        return VisTooltipPlacement.place(anchor, TIP_WIDTH, TIP_HEIGHT, VIEW_WIDTH, VIEW_HEIGHT, direction);
    }

    private static Rect rect(final double left, final double top, final double width, final double height) {
        return new Rect(top, top + height, left, left + width);
    }
}
