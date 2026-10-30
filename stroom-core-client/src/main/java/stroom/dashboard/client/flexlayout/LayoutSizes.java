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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.flexlayout.Splitter.SplitInfo;
import stroom.dashboard.client.model.MutableLayoutConfig;
import stroom.dashboard.client.model.MutableSplitLayoutConfig;
import stroom.dashboard.client.model.MutableTabLayoutConfig;
import stroom.dashboard.shared.Dimension;

import java.util.Map;

/// Works out where the panels and splitters of a [FlexLayout] go, and how big they are, from the
/// layout's preferred sizes and the space it has, keeping every panel at least
/// [#MIN_COMPONENT_WIDTH] in each dimension.
final class LayoutSizes {

    /// The least a panel can be in either dimension.
    static final double MIN_COMPONENT_WIDTH = 50;
    /// How thick a splitter is.
    static final double SPLIT_SIZE = 1;
    /// How far a splitter is put before the end of the panel before it.
    static final double SPLIT_OFFSET = 0;

    private final Map<Object, PositionAndSize> positionAndSizeMap;

    /// @param positionAndSizeMap Where the positions and sizes worked out are put, by layout (and
    ///                           by [SplitInfo] for splitters).
    LayoutSizes(final Map<Object, PositionAndSize> positionAndSizeMap) {
        this.positionAndSizeMap = positionAndSizeMap;
    }

    /// Works out the positions and sizes of everything in a layout again, in the space it has now.
    ///
    /// @param layoutConfig The layout, which must already have a position and size.
    void recalculate(final MutableLayoutConfig layoutConfig) {
        final PositionAndSize positionAndSize = positionAndSizeMap.get(layoutConfig);
        recalculate(layoutConfig, positionAndSize.getLeft(), positionAndSize.getTop(), positionAndSize.getWidth(),
                positionAndSize.getHeight());
    }

    /// Works out the positions and sizes of a layout and everything in it, in the space given.
    ///
    /// @param layoutConfig The layout.
    /// @param left         Where the space starts across.
    /// @param top          Where the space starts down.
    /// @param width        How wide the space is.
    /// @param height       How high the space is.
    void recalculate(final MutableLayoutConfig layoutConfig,
                     final double left,
                     final double top,
                     final double width,
                     final double height) {
        recalculateDimension(layoutConfig, Dimension.X, left, width, true);
        recalculateDimension(layoutConfig, Dimension.Y, top, height, true);
    }

    private double recalculateDimension(final MutableLayoutConfig layoutConfig,
                                        final int dim,
                                        double pos,
                                        final double size,
                                        final boolean useRemaining) {
        double containerSize = 0;

        if (layoutConfig != null) {
            // Get the minimum size in this dimension that this splitter can be
            // to fit its contents.
            final double minSize = getMinRequired(layoutConfig, dim);
            if (useRemaining) {
                containerSize = size;
                containerSize = Math.max(containerSize, minSize);
            } else {
                final double preferredSize = layoutConfig.getPreferredSize().get(dim);
                containerSize = Math.min(preferredSize, size);
                containerSize = Math.max(containerSize, minSize);
            }

            PositionAndSize positionAndSize =
                    positionAndSizeMap.computeIfAbsent(layoutConfig, k -> new PositionAndSize());
            positionAndSize.setPos(dim, pos);
            positionAndSize.setSize(dim, containerSize);

            // Now deal with children if this is split layout data.
            if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
                double remainingSize = containerSize;

                for (int i = 0; i < splitLayoutConfig.count(); i++) {
                    final MutableLayoutConfig child = splitLayoutConfig.get(i);

                    // See if this is the last child.
                    if (i < splitLayoutConfig.count() - 1) {
                        // Find out what the minimum space is that could be
                        // taken up by layouts after this one.
                        double minRequired = 0;
                        if (splitLayoutConfig.getDimension() == dim) {
                            minRequired = getMinRequired(splitLayoutConfig, dim, i + 1, 1);
                        }
                        final double available = remainingSize - minRequired;

                        final boolean useRest = splitLayoutConfig.getDimension() != dim;
                        final double childSize = recalculateDimension(child, dim, pos, available, useRest);
                        if (splitLayoutConfig.getDimension() == dim) {
                            // Move position for next child and reduce available
                            // size.
                            pos += childSize;
                            remainingSize -= childSize;
                        }

                        // Add data to position a split during layout.
                        final SplitInfo splitInfo = new SplitInfo(splitLayoutConfig, i);
                        positionAndSize = positionAndSizeMap.computeIfAbsent(splitInfo, k -> new PositionAndSize());

                        if (dim == splitLayoutConfig.getDimension()) {
                            positionAndSize.setPos(dim, pos - SPLIT_OFFSET);
                            positionAndSize.setSize(dim, SPLIT_SIZE);
                        } else {
                            positionAndSize.setPos(dim, pos);
                            positionAndSize.setSize(dim, containerSize + SPLIT_SIZE);
                        }

                    } else {
                        // This is the last child so assume rest of available
                        // space.
                        recalculateDimension(child, dim, pos, remainingSize, true);
                    }
                }
            }
        }

        return containerSize;
    }

    /// @param layoutConfig The layout.
    /// @param dim          The [Dimension].
    /// @return How big a layout is in a dimension when every panel in it is its preferred size
    /// (or at least [#MIN_COMPONENT_WIDTH]).
    static double getMaxRequired(final MutableLayoutConfig layoutConfig,
                                 final int dim) {
        return getRequiredSize(layoutConfig, dim, 0, 1, false);
    }

    /// @param layoutConfig The layout.
    /// @param dim          The [Dimension].
    /// @return The least a layout can be in a dimension, with every panel in it
    /// [#MIN_COMPONENT_WIDTH].
    static double getMinRequired(final MutableLayoutConfig layoutConfig,
                                 final int dim) {
        return getRequiredSize(layoutConfig, dim, 0, 1, true);
    }

    /// @param layoutConfig The split layout.
    /// @param dim          The [Dimension].
    /// @param index        The first child to measure.
    /// @param step         1 to measure the children from it to the last, -1 to measure the
    ///                     children from it to the first.
    /// @return The least some of a split layout's children can be in a dimension, with every panel
    /// in them [#MIN_COMPONENT_WIDTH].
    static double getMinRequired(final MutableLayoutConfig layoutConfig,
                                 final int dim,
                                 final int index,
                                 final int step) {
        return getRequiredSize(layoutConfig, dim, index, step, true);
    }

    private static double getRequiredSize(final MutableLayoutConfig layoutConfig,
                                          final int dim,
                                          final int index,
                                          final int step,
                                          final boolean min) {
        double totalSize = 0;
        if (layoutConfig instanceof final MutableTabLayoutConfig tabLayoutConfig) {
            if (min) {
                totalSize = MIN_COMPONENT_WIDTH;
            } else {
                totalSize = tabLayoutConfig.getPreferredSize().get(dim);
                totalSize = Math.max(MIN_COMPONENT_WIDTH, totalSize);
            }

        } else if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
            for (int i = index; i >= 0 && i < splitLayoutConfig.count(); i += step) {
                final MutableLayoutConfig child = splitLayoutConfig.get(i);
                final double childSize = getRequiredSize(child, dim, 0, 1, min);

                if (splitLayoutConfig.getDimension() == dim) {
                    // It this split layout is in the same direction as the
                    // dimension we are measuring then we need to sum the sizes.
                    totalSize += childSize;
                } else if (totalSize < childSize) {
                    // If this split layout is in a different direction to the
                    // dimension we are measuring then just remember the largest
                    // size.
                    totalSize = childSize;
                }
            }
        }

        return totalSize;
    }
}
