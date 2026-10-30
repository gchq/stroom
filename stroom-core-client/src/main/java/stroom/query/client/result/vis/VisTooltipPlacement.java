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

import stroom.widget.util.client.Rect;

import java.util.List;

/// Works out where a visualisation's tooltip goes: beside the element it describes, on the side
/// the visualisation asked for if there is room, else on the opposite side, else on the other
/// axis, and always within the browser window. All values are in window (viewport) pixels.
public final class VisTooltipPlacement {

    /// The space between the tooltip and the element it describes.
    static final double GAP = 8;
    /// The least space kept between the tooltip and the edges of the window.
    static final double MARGIN = 4;

    private VisTooltipPlacement() {
        // Static utility
    }

    /// Places a tooltip beside an element.
    ///
    /// @param anchor     The element the tooltip describes, in window pixels.
    /// @param tipWidth   The tooltip's width.
    /// @param tipHeight  The tooltip's height.
    /// @param viewWidth  The window's width.
    /// @param viewHeight The window's height.
    /// @param preferred  The side the visualisation would like the tooltip on.
    /// @return Where the tooltip goes, and on which side of the element it ended up.
    public static Placement place(final Rect anchor,
                                  final double tipWidth,
                                  final double tipHeight,
                                  final double viewWidth,
                                  final double viewHeight,
                                  final Direction preferred) {
        final Direction first = preferred != null
                ? preferred
                : Direction.EAST;

        Placement chosen = null;
        for (final Direction direction : candidates(first)) {
            final Placement placement = beside(anchor, tipWidth, tipHeight, direction);
            if (fits(placement, tipWidth, tipHeight, viewWidth, viewHeight)) {
                chosen = placement;
                break;
            }
        }
        if (chosen == null) {
            // Nowhere has room, so keep to the side asked for and let the clamp keep it on screen
            chosen = beside(anchor, tipWidth, tipHeight, first);
        }

        return new Placement(
                clamp(chosen.left(), tipWidth, viewWidth),
                clamp(chosen.top(), tipHeight, viewHeight),
                chosen.direction());
    }

    private static List<Direction> candidates(final Direction first) {
        return switch (first) {
            case NORTH -> List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
            case SOUTH -> List.of(Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST);
            case EAST -> List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
            case WEST -> List.of(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH);
        };
    }

    private static Placement beside(final Rect anchor,
                                    final double tipWidth,
                                    final double tipHeight,
                                    final Direction direction) {
        final double centreX = anchor.getLeft() + (anchor.getWidth() / 2);
        final double centreY = anchor.getTop() + (anchor.getHeight() / 2);
        return switch (direction) {
            case NORTH -> new Placement(
                    centreX - (tipWidth / 2),
                    anchor.getTop() - GAP - tipHeight,
                    direction);
            case SOUTH -> new Placement(
                    centreX - (tipWidth / 2),
                    anchor.getBottom() + GAP,
                    direction);
            case EAST -> new Placement(
                    anchor.getRight() + GAP,
                    centreY - (tipHeight / 2),
                    direction);
            case WEST -> new Placement(
                    anchor.getLeft() - GAP - tipWidth,
                    centreY - (tipHeight / 2),
                    direction);
        };
    }

    // Whether the tooltip is within the window along the axis that it was moved along. The other
    // axis is clamped afterwards, so it doesn't decide the side
    private static boolean fits(final Placement placement,
                                final double tipWidth,
                                final double tipHeight,
                                final double viewWidth,
                                final double viewHeight) {
        return switch (placement.direction()) {
            case NORTH -> placement.top() >= MARGIN;
            case SOUTH -> placement.top() + tipHeight <= viewHeight - MARGIN;
            case EAST -> placement.left() + tipWidth <= viewWidth - MARGIN;
            case WEST -> placement.left() >= MARGIN;
        };
    }

    // Keeps a tooltip of the given size within the window along one axis. One bigger than the
    // window is put against its start, so that the start of its content can be read
    private static double clamp(final double position, final double size, final double viewSize) {
        final double max = viewSize - size - MARGIN;
        return Math.max(MARGIN, Math.min(position, max));
    }


    // --------------------------------------------------------------------------------


    /// Which side of the element a tooltip is on.
    public enum Direction {
        NORTH,
        SOUTH,
        EAST,
        WEST;

        /// Reads a d3-tip direction (`n`, `s`, `e`, `w`, or a corner such as `ne`). A corner
        /// is taken as its first letter.
        ///
        /// @param code The direction, as d3-tip names it.
        /// @return The direction, or null if the code is empty or not one d3-tip uses.
        public static Direction fromCode(final String code) {
            if (code == null || code.isEmpty()) {
                return null;
            }
            return switch (Character.toLowerCase(code.charAt(0))) {
                case 'n' -> NORTH;
                case 's' -> SOUTH;
                case 'e' -> EAST;
                case 'w' -> WEST;
                default -> null;
            };
        }
    }


    // --------------------------------------------------------------------------------


    /// Where a tooltip goes.
    public static final class Placement {

        private final double left;
        private final double top;
        private final Direction direction;

        /// @param left      The left of the tooltip, in window pixels.
        /// @param top       The top of the tooltip, in window pixels.
        /// @param direction Which side of the element it is on.
        public Placement(final double left, final double top, final Direction direction) {
            this.left = left;
            this.top = top;
            this.direction = direction;
        }

        /// @return The left of the tooltip, in window pixels.
        public double left() {
            return left;
        }

        /// @return The top of the tooltip, in window pixels.
        public double top() {
            return top;
        }

        /// @return Which side of the element the tooltip is on.
        public Direction direction() {
            return direction;
        }

        @Override
        public String toString() {
            return "Placement{left=" + left + ", top=" + top + ", direction=" + direction + "}";
        }
    }
}
