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

package stroom.widget.datagrid.client.model;

/// The size and position of an overlay scrollbar thumb along one axis (design section 4.2).
///
/// The scroller is a native one with its own scrollbar hidden. This works out where to draw the
/// overlay thumb, and the reverse: which scroll position a dragged thumb corresponds to.
public final class ScrollThumb {

    /// The smallest thumb length in pixels, so a very long list still has a usable thumb.
    public static final int DEFAULT_MIN_LENGTH = 20;

    private final boolean visible;
    private final double length;
    private final double position;
    private final double travel;
    private final double maxScroll;

    private ScrollThumb(final boolean visible,
                        final double length,
                        final double position,
                        final double travel,
                        final double maxScroll) {
        this.visible = visible;
        this.length = length;
        this.position = position;
        this.travel = travel;
        this.maxScroll = maxScroll;
    }

    /// Work out the thumb for one axis.
    ///
    /// @param scroll        The current scroll offset in pixels.
    /// @param viewport      The visible length of the content in pixels.
    /// @param content       The total length of the content in pixels.
    /// @param track         The length of the track the thumb moves along in pixels.
    /// @param minLength     The smallest thumb length in pixels.
    /// @return The thumb.
    public static ScrollThumb compute(final double scroll,
                                      final double viewport,
                                      final double content,
                                      final double track,
                                      final double minLength) {
        if (content <= viewport || viewport <= 0 || track <= 0) {
            return new ScrollThumb(false, 0, 0, 0, 0);
        }

        final double length = Math.max(Math.min(minLength, track), track * viewport / content);
        final double travel = Math.max(0, track - length);
        final double maxScroll = content - viewport;
        final double clamped = Math.max(0, Math.min(scroll, maxScroll));
        final double position = travel * clamped / maxScroll;
        return new ScrollThumb(true, length, position, travel, maxScroll);
    }

    /// The scroll offset that puts the thumb at a given position, used while dragging the thumb.
    ///
    /// @param thumbPosition The thumb's distance in pixels from the start of the track.
    /// @return The scroll offset in pixels, clamped to the scroll range.
    public double scrollForPosition(final double thumbPosition) {
        if (!visible || travel <= 0) {
            return 0;
        }
        final double clamped = Math.max(0, Math.min(thumbPosition, travel));
        return maxScroll * clamped / travel;
    }

    /// @return Whether the content overflows, so a thumb should be shown.
    public boolean isVisible() {
        return visible;
    }

    /// @return The thumb length in pixels.
    public double getLength() {
        return length;
    }

    /// @return The thumb's distance in pixels from the start of the track.
    public double getPosition() {
        return position;
    }

    /// @return How far in pixels the thumb can move along the track.
    public double getTravel() {
        return travel;
    }
}
