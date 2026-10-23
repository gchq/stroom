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

import stroom.data.grid.client.ColSettings;

import java.util.Objects;

/// The sizing and interaction settings for one grid column.
///
/// Instances are immutable. Use the static factory methods to create one and the `with...` methods
/// to derive a modified copy.
public final class ColumnSizing {

    /// The smallest width, in pixels, that a user can resize a column to unless the column sets its
    /// own minimum.
    public static final int DEFAULT_MIN_WIDTH = 30;

    /// The default width, in pixels, of a column added without a width.
    public static final int DEFAULT_WIDTH = 200;

    /// The default fill weight of a fill column.
    public static final int DEFAULT_FILL_WEIGHT = 100;

    private final ColumnWidthType type;
    private final int width;
    private final double percent;
    private final int fillWeight;
    private final int minWidth;
    private final boolean resizable;
    private final boolean movable;

    private ColumnSizing(final ColumnWidthType type,
                         final int width,
                         final double percent,
                         final int fillWeight,
                         final int minWidth,
                         final boolean resizable,
                         final boolean movable) {
        this.type = Objects.requireNonNull(type);
        this.width = Math.max(0, width);
        this.percent = Math.max(0, percent);
        this.fillWeight = Math.max(0, fillWeight);
        this.minWidth = Math.max(1, minWidth);
        this.resizable = resizable;
        this.movable = movable;
    }

    /// A fixed-width column that the user can neither resize nor move.
    ///
    /// @param width The width in pixels.
    /// @return The new sizing.
    public static ColumnSizing fixed(final int width) {
        return new ColumnSizing(ColumnWidthType.FIXED, width, 0, 0, DEFAULT_MIN_WIDTH, false, false);
    }

    /// A fixed-width column that the user can resize and move.
    ///
    /// @param width The starting width in pixels.
    /// @return The new sizing.
    public static ColumnSizing resizable(final int width) {
        return new ColumnSizing(ColumnWidthType.FIXED, width, 0, 0, DEFAULT_MIN_WIDTH, true, true);
    }

    /// A column whose starting width is a percentage of the grid's initial visible width. The user
    /// can resize and move it.
    ///
    /// @param percent The percentage, e.g. `25` for a quarter of the width.
    /// @return The new sizing.
    public static ColumnSizing percent(final double percent) {
        return new ColumnSizing(ColumnWidthType.PERCENT, 0, percent, 0, DEFAULT_MIN_WIDTH, true, true);
    }

    /// A column that grows to take a share of the space left over on the first layout. The user can
    /// resize and move it.
    ///
    /// @param fillWeight The column's share of the spare space relative to other fill columns.
    /// @param floorWidth The declared width in pixels. The column never starts narrower than this.
    /// @return The new sizing.
    public static ColumnSizing fill(final int fillWeight, final int floorWidth) {
        return new ColumnSizing(ColumnWidthType.FILL,
                floorWidth,
                0,
                fillWeight,
                DEFAULT_MIN_WIDTH,
                true,
                true);
    }

    /// A fill column with the default weight.
    ///
    /// @param floorWidth The declared width in pixels. The column never starts narrower than this.
    /// @return The new sizing.
    public static ColumnSizing fill(final int floorWidth) {
        return fill(DEFAULT_FILL_WEIGHT, floorWidth);
    }

    /// The sizing for a column `MyDataGrid` would add from a `ColSpec`: a fill column if its
    /// settings say so (the width is then its floor), otherwise a fixed width, resizable and movable
    /// as they say. `MyDataGrid` never reads the settings' minimum width, so neither does this.
    ///
    /// @param settings The column's settings.
    /// @param width    The column's width in pixels.
    /// @return The new sizing.
    public static ColumnSizing fromColSettings(final ColSettings settings, final int width) {
        final ColumnSizing sizing = settings.isFill()
                ? fill((int) Math.round(settings.getFillWeight()), width)
                : fixed(width);
        return sizing
                .withResizable(settings.isResizable())
                .withMovable(settings.isMovable());
    }

    /// Derive a copy with a different minimum width.
    ///
    /// @param minWidth The smallest width in pixels the user can resize this column to.
    /// @return A copy of this sizing with the minimum width changed.
    public ColumnSizing withMinWidth(final int minWidth) {
        return new ColumnSizing(type, width, percent, fillWeight, minWidth, resizable, movable);
    }

    /// Derive a copy with a different resizable flag.
    ///
    /// @param resizable Whether the user can resize this column.
    /// @return A copy of this sizing with the resizable flag changed.
    public ColumnSizing withResizable(final boolean resizable) {
        return new ColumnSizing(type, width, percent, fillWeight, minWidth, resizable, movable);
    }

    /// Derive a copy with a different movable flag.
    ///
    /// @param movable Whether the user can move this column.
    /// @return A copy of this sizing with the movable flag changed.
    public ColumnSizing withMovable(final boolean movable) {
        return new ColumnSizing(type, width, percent, fillWeight, minWidth, resizable, movable);
    }

    /// @return How the starting width is worked out.
    public ColumnWidthType getType() {
        return type;
    }

    /// @return The declared width in pixels. For a fill column this is the floor. Zero for a
    /// percent column.
    public int getWidth() {
        return width;
    }

    /// @return The percentage of the initial visible width. Zero unless this is a percent column.
    public double getPercent() {
        return percent;
    }

    /// @return The fill weight. Zero unless this is a fill column.
    public int getFillWeight() {
        return fillWeight;
    }

    /// @return The smallest width in pixels the user can resize this column to.
    public int getMinWidth() {
        return minWidth;
    }

    /// @return Whether the user can resize this column.
    public boolean isResizable() {
        return resizable;
    }

    /// @return Whether the user can move this column.
    public boolean isMovable() {
        return movable;
    }

    /// @return Whether this column takes a share of the spare space on the first layout.
    public boolean isFill() {
        return type == ColumnWidthType.FILL;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnSizing that = (ColumnSizing) o;
        return width == that.width
               && Double.compare(percent, that.percent) == 0
               && fillWeight == that.fillWeight
               && minWidth == that.minWidth
               && resizable == that.resizable
               && movable == that.movable
               && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, width, percent, fillWeight, minWidth, resizable, movable);
    }

    @Override
    public String toString() {
        return "ColumnSizing{" +
               "type=" + type +
               ", width=" + width +
               ", percent=" + percent +
               ", fillWeight=" + fillWeight +
               ", minWidth=" + minWidth +
               ", resizable=" + resizable +
               ", movable=" + movable +
               '}';
    }
}
