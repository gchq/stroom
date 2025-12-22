/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.htree.client.treelayout;

import java.util.Map;

public interface TreeLayout<T_TREE_NODE> {

    /**
     * Calculate the layout for the tree model.
     */
    void layout();

    /**
     * Returns the bounds of the tree layout.
     * <p>
     * The bounds of a TreeLayout is the smallest rectangle containing the
     * bounds of all nodes in the layout. It always starts at (0,0).
     *
     * @return the bounds of the tree layout
     */
    Bounds getBounds();

    /**
     * Returns the layout of the tree nodes by mapping each node of the tree to
     * its bounds (position and size).
     * <p>
     * For each rectangle x and y will be >= 0. At least one rectangle will have
     * an x == 0 and at least one rectangle will have an y == 0.
     *
     * @return maps each node of the tree to its bounds (position and size).
     */
    Map<T_TREE_NODE, Bounds> getNodeBounds();

    /**
     * Returns the Tree the layout is created for.
     */
    TreeForTreeLayout<T_TREE_NODE> getTree();

    /**
     * Sets the Tree the layout will be created for.
     */
    void setTree(TreeForTreeLayout<T_TREE_NODE> tree);
}
