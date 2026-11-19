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

/// Which overlay scrollbar tracks will show, so the grid can leave room (a gutter) for each.
///
/// The horizontal gutter is room under the rows, so it adds to the height scrolled; the vertical
/// gutter is room at the end of each row's last cell, so it adds nothing to the width. The
/// horizontal track therefore shows whenever the rows are wider than the scroller, and the vertical
/// one when the rows, with the horizontal gutter if there is one, are taller. The rows' height is
/// taken without any gutter there now, so a gutter never keeps itself.
///
/// @param horizontal Whether the horizontal track shows, with its gutter under the rows.
/// @param vertical   Whether the vertical track shows, with its gutter in the last cells.
public record ScrollGutters(boolean horizontal, boolean vertical) {

    /// Work out the gutters from the scroller's sizes.
    ///
    /// @param scrollWidth             The scroller's scroll width in pixels.
    /// @param clientWidth             The scroller's visible width in pixels.
    /// @param scrollHeight            The scroller's scroll height in pixels, with any horizontal
    ///                                gutter it has now.
    /// @param clientHeight            The scroller's visible height in pixels.
    /// @param horizontalGutterNow     Whether the scroller has the horizontal gutter now.
    /// @param gutter                  A gutter's size in pixels (a track's width).
    /// @return The gutters.
    public static ScrollGutters compute(final int scrollWidth,
                                        final int clientWidth,
                                        final int scrollHeight,
                                        final int clientHeight,
                                        final boolean horizontalGutterNow,
                                        final int gutter) {
        final boolean horizontal = scrollWidth > clientWidth;
        final int rowsHeight = scrollHeight - (horizontalGutterNow
                ? gutter
                : 0);
        final boolean vertical = rowsHeight + (horizontal
                ? gutter
                : 0) > clientHeight;
        return new ScrollGutters(horizontal, vertical);
    }
}
