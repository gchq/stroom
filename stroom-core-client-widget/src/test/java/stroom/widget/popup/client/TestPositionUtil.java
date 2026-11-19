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

package stroom.widget.popup.client;

import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPositionUtil {

    // A 1000 x 800 window, not scrolled
    private static final Rect WINDOW = new Rect(0, 800, 0, 1000);

    @Test
    void rightWhereThereIsRoom() {
        // An item 200 to 400 across: a 150 wide popup to its right starts at its right edge
        final Position position = position(200, 400, PopupLocation.RIGHT, 150);
        assertThat(position.getLeft()).isEqualTo(400);
        assertThat(position.getTop()).isEqualTo(100);
    }

    @Test
    void rightWithNoRoomGoesToTheLeftOfTheItem() {
        // A submenu of a menu at the window's right edge (item 750 to 950): it goes to the item's
        // left, not over the menu (it was aligned with the item's right edge, 950 - 150 = 800)
        final Position position = position(750, 950, PopupLocation.RIGHT, 150);
        assertThat(position.getLeft()).isEqualTo(600);
    }

    @Test
    void rightWithNoRoomEitherSideAlignsWithTheItemsRightEdge() {
        // Too wide for either side of the item: over it, aligned with its right edge (as before)
        final Position position = position(100, 900, PopupLocation.RIGHT, 300);
        assertThat(position.getLeft()).isEqualTo(600);
    }

    @Test
    void leftWhereThereIsRoom() {
        final Position position = position(400, 600, PopupLocation.LEFT, 150);
        assertThat(position.getLeft()).isEqualTo(250);
    }

    @Test
    void leftWithNoRoomGoesToTheRightOfTheItem() {
        // An item near the window's left edge: the popup goes to its right, not over it (it was
        // pushed to the window's edge, 0)
        final Position position = position(50, 250, PopupLocation.LEFT, 150);
        assertThat(position.getLeft()).isEqualTo(250);
    }

    @Test
    void belowAlignsWithTheItemsLeftEdge() {
        final Position position = position(200, 400, PopupLocation.BELOW, 150);
        assertThat(position.getLeft()).isEqualTo(200);
        assertThat(position.getTop()).isEqualTo(120);
    }

    @Test
    void belowWithNoRoomOnTheRightAlignsWithTheItemsRightEdge() {
        // A drop-down near the window's right edge: aligned with the box's right edge, as before
        final Position position = position(850, 950, PopupLocation.BELOW, 300);
        assertThat(position.getLeft()).isEqualTo(650);
    }

    private static Position position(final double itemLeft,
                                     final double itemRight,
                                     final PopupLocation location,
                                     final int width) {
        // The item is 100 to 120 down
        final PopupPosition popupPosition = new PopupPosition(new Rect(100, 120, itemLeft, itemRight), location);
        return PositionUtil.getPosition(0, popupPosition, width, 50, WINDOW);
    }
}
