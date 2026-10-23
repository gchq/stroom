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

import java.util.ArrayList;
import java.util.List;

/// Each row's place among its siblings in a tree drawn as rows, and how many siblings it has, for a
/// tree item's `aria-posinset` and `aria-setsize`. The rows are in tree order (each row's children
/// follow it), each at a depth (0 for the top level); a row's siblings are the rows at its depth
/// with the same parent, or, at the top, every top-level row.
public final class TreePositions {

    private final int[] positions;
    private final int[] sizes;

    private TreePositions(final int[] positions, final int[] sizes) {
        this.positions = positions;
        this.sizes = sizes;
    }

    /// Works out the rows' places.
    ///
    /// @param depths Each row's depth, in tree order.
    /// @return The rows' places.
    public static TreePositions of(final int[] depths) {
        final int[] positions = new int[depths.length];
        final int[] sizes = new int[depths.length];
        // The runs of siblings still open as the rows are read, the top level's first
        final List<List<Integer>> open = new ArrayList<>();
        for (int i = 0; i < depths.length; i++) {
            final int depth = Math.max(0, depths[i]);
            while (open.size() > depth + 1) {
                close(open.remove(open.size() - 1), sizes);
            }
            while (open.size() < depth + 1) {
                open.add(new ArrayList<>());
            }
            final List<Integer> siblings = open.get(depth);
            siblings.add(i);
            positions[i] = siblings.size();
        }
        for (final List<Integer> siblings : open) {
            close(siblings, sizes);
        }
        return new TreePositions(positions, sizes);
    }

    // Each of a run of siblings is one of that many
    private static void close(final List<Integer> siblings, final int[] sizes) {
        for (final int row : siblings) {
            sizes[row] = siblings.size();
        }
        siblings.clear();
    }

    /// @param row A row's index.
    /// @return The row's place among its siblings, counting from 1, or 0 for no such row.
    public int getPosition(final int row) {
        return row >= 0 && row < positions.length
                ? positions[row]
                : 0;
    }

    /// @param row A row's index.
    /// @return How many siblings the row has, itself included, or 0 for no such row.
    public int getSize(final int row) {
        return row >= 0 && row < sizes.length
                ? sizes[row]
                : 0;
    }
}
