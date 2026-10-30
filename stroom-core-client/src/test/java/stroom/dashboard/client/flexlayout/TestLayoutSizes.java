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
import stroom.dashboard.client.model.MutableSize;
import stroom.dashboard.client.model.MutableSplitLayoutConfig;
import stroom.dashboard.client.model.MutableTabLayoutConfig;
import stroom.dashboard.shared.Dimension;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestLayoutSizes {

    @Test
    void getMinRequired_panel() {
        final MutableTabLayoutConfig panel = panel(200, 100);

        assertThat(LayoutSizes.getMinRequired(panel, Dimension.X)).isEqualTo(LayoutSizes.MIN_COMPONENT_WIDTH);
        assertThat(LayoutSizes.getMinRequired(panel, Dimension.Y)).isEqualTo(LayoutSizes.MIN_COMPONENT_WIDTH);
    }

    @Test
    void getMinRequired_split() {
        final MutableSplitLayoutConfig split = across(panel(200, 100), panel(300, 100), panel(100, 100));

        // Summed along the split, the largest across it
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X)).isEqualTo(150);
        assertThat(LayoutSizes.getMinRequired(split, Dimension.Y)).isEqualTo(50);
    }

    @Test
    void getMinRequired_someChildren() {
        final MutableSplitLayoutConfig split = across(panel(200, 100), panel(300, 100), panel(100, 100));

        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, 1, 1)).isEqualTo(100);
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, 2, 1)).isEqualTo(50);
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, 1, -1)).isEqualTo(100);
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, 0, -1)).isEqualTo(50);
        // None
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, 3, 1)).isZero();
        assertThat(LayoutSizes.getMinRequired(split, Dimension.X, -1, -1)).isZero();
    }

    @Test
    void getMaxRequired() {
        final MutableSplitLayoutConfig split = across(panel(200, 100), panel(300, 150));

        assertThat(LayoutSizes.getMaxRequired(split, Dimension.X)).isEqualTo(500);
        assertThat(LayoutSizes.getMaxRequired(split, Dimension.Y)).isEqualTo(150);
    }

    @Test
    void getMaxRequired_smallPanel() {
        // A panel is never less than the minimum, whatever it would prefer
        assertThat(LayoutSizes.getMaxRequired(panel(20, 0), Dimension.X))
                .isEqualTo(LayoutSizes.MIN_COMPONENT_WIDTH);
    }

    @Test
    void getRequired_null() {
        assertThat(LayoutSizes.getMinRequired(null, Dimension.X)).isZero();
        assertThat(LayoutSizes.getMaxRequired(null, Dimension.X)).isZero();
    }

    @Test
    void recalculate() {
        final MutableTabLayoutConfig first = panel(200, 100);
        final MutableTabLayoutConfig last = panel(300, 100);
        final MutableSplitLayoutConfig split = across(first, last);
        final Map<Object, PositionAndSize> map = new HashMap<>();

        new LayoutSizes(map).recalculate(split, 0, 0, 600, 300);

        assertPositionAndSize(map.get(split), 0, 0, 600, 300);
        // The first panel is the size it prefers, the last takes the rest
        assertPositionAndSize(map.get(first), 0, 0, 200, 300);
        assertPositionAndSize(map.get(last), 200, 0, 400, 300);
        // The splitter is at the end of the first panel, and runs the height of the split
        assertPositionAndSize(map.get(new SplitInfo(split, 0)),
                200 - LayoutSizes.SPLIT_OFFSET,
                0,
                LayoutSizes.SPLIT_SIZE,
                300 + LayoutSizes.SPLIT_SIZE);
    }

    @Test
    void recalculate_nested() {
        final MutableTabLayoutConfig top = panel(100, 100);
        final MutableTabLayoutConfig bottom = panel(100, 100);
        final MutableTabLayoutConfig right = panel(100, 100);
        final MutableSplitLayoutConfig left = down(top, bottom);
        final MutableSplitLayoutConfig split = across(left, right);
        left.getPreferredSize().setWidth(250);
        final Map<Object, PositionAndSize> map = new HashMap<>();

        new LayoutSizes(map).recalculate(split, 10, 20, 600, 400);

        assertPositionAndSize(map.get(left), 10, 20, 250, 400);
        assertPositionAndSize(map.get(top), 10, 20, 250, 100);
        assertPositionAndSize(map.get(bottom), 10, 120, 250, 300);
        assertPositionAndSize(map.get(right), 260, 20, 350, 400);
    }

    @Test
    void recalculate_tooSmall() {
        final MutableTabLayoutConfig first = panel(200, 100);
        final MutableTabLayoutConfig last = panel(300, 100);
        final MutableSplitLayoutConfig split = across(first, last);
        final Map<Object, PositionAndSize> map = new HashMap<>();

        new LayoutSizes(map).recalculate(split, 0, 0, 60, 20);

        // Every panel keeps its minimum size, so the layout is bigger than the space
        assertPositionAndSize(map.get(split), 0, 0, 100, 50);
        assertPositionAndSize(map.get(first), 0, 0, 50, 50);
        assertPositionAndSize(map.get(last), 50, 0, 50, 50);
    }

    @Test
    void recalculate_again() {
        final MutableTabLayoutConfig first = panel(200, 100);
        final MutableTabLayoutConfig last = panel(300, 100);
        final MutableSplitLayoutConfig split = across(first, last);
        final Map<Object, PositionAndSize> map = new HashMap<>();
        final LayoutSizes layoutSizes = new LayoutSizes(map);
        layoutSizes.recalculate(split, 0, 0, 600, 300);

        first.getPreferredSize().setWidth(100);
        layoutSizes.recalculate(split);

        // In the same space as before
        assertPositionAndSize(map.get(first), 0, 0, 100, 300);
        assertPositionAndSize(map.get(last), 100, 0, 500, 300);
    }

    private static MutableTabLayoutConfig panel(final int width, final int height) {
        return new MutableTabLayoutConfig(new MutableSize(width, height), 0);
    }

    private static MutableSplitLayoutConfig across(final MutableLayoutConfig... children) {
        return split(Dimension.X, children);
    }

    private static MutableSplitLayoutConfig down(final MutableLayoutConfig... children) {
        return split(Dimension.Y, children);
    }

    private static MutableSplitLayoutConfig split(final int dim, final MutableLayoutConfig... children) {
        final MutableSplitLayoutConfig split = new MutableSplitLayoutConfig(dim);
        for (final MutableLayoutConfig child : children) {
            split.add(child);
        }
        return split;
    }

    private static void assertPositionAndSize(final PositionAndSize positionAndSize,
                                              final double left,
                                              final double top,
                                              final double width,
                                              final double height) {
        assertThat(positionAndSize).isNotNull();
        assertThat(positionAndSize.getPos()).containsExactly(left, top);
        assertThat(positionAndSize.getSize()).containsExactly(width, height);
    }
}
