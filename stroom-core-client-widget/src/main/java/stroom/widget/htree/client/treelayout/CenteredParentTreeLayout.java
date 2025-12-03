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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CenteredParentTreeLayout<T_TREE_NODE> implements TreeLayout<T_TREE_NODE> {

    private final NodeExtentProvider<T_TREE_NODE> nodeExtentProvider;
    private final Configuration<T_TREE_NODE> configuration;
    private TreeForTreeLayout<T_TREE_NODE> tree;
    private Map<T_TREE_NODE, Bounds> boundsMap;
    private double boundsX;
    private double boundsY;
    private double boundsWidth;
    private double boundsHeight;

    public CenteredParentTreeLayout(final NodeExtentProvider<T_TREE_NODE> nodeExtentProvider,
                                    final Configuration<T_TREE_NODE> configuration) {
        this.nodeExtentProvider = nodeExtentProvider;
        this.configuration = configuration;
    }

    @Override
    public void layout() {
        boundsMap = new HashMap<>();
        boundsX = 0;
        boundsY = 0;
        boundsWidth = 0;
        boundsHeight = 0;
        if (tree != null && tree.getRoot() != null) {
            calculate(tree.getRoot(), 0, 0, 0);
        }
    }

    private Bounds calculate(final T_TREE_NODE node, final double x, final double y, final int depth) {
        final Dimension dimension = nodeExtentProvider.getExtents(node);
        final double width = dimension.getWidth();
        final double height = dimension.getHeight();

        final double outerMinY = y;
        double outerMaxY = y + height;

        final List<T_TREE_NODE> children = tree.getChildren(node);
        if (children != null && children.size() > 0) {
            final double childX = x + width + configuration.getGapBetweenLevels(depth);
            double childY = y;

            double innerMinY = y;
            double innerMaxY = y + height;

            for (int i = 0; i < children.size(); i++) {
                final T_TREE_NODE child = children.get(i);

                if (i > 0) {
                    final double space = configuration.getGapBetweenNodes(child, children.get(i - 1));
                    childY += space;
                }

                final Bounds childBounds = calculate(child, childX, childY, depth + 1);

                if (i == 0) {
                    innerMinY = childY + (childBounds.getHeight() / 2) - 10;
                }
                if (i == children.size() - 1) {
                    innerMaxY = childY + (childBounds.getHeight() / 2) + 10;
                    outerMaxY = childY + childBounds.getHeight();
                }

                childY += childBounds.getHeight();
            }

            final Bounds bounds = new Bounds(x, innerMinY, width, innerMaxY - innerMinY);
            boundsMap.put(node, bounds);

        } else {
            final Bounds bounds = new Bounds(x, y, width, height);
            boundsMap.put(node, bounds);
        }

        // Increase outer bounds for whole tree if necessary.
        final double maxX = x + width;
        if (boundsWidth < maxX) {
            boundsWidth = maxX;
        }
        final double maxY = outerMaxY;
        if (boundsHeight < maxY) {
            boundsHeight = maxY;
        }

        return new Bounds(x, outerMinY, width, outerMaxY - outerMinY);
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(boundsX, boundsY, boundsWidth, boundsHeight);
    }

    @Override
    public Map<T_TREE_NODE, Bounds> getNodeBounds() {
        return boundsMap;
    }

    @Override
    public TreeForTreeLayout<T_TREE_NODE> getTree() {
        return tree;
    }

    @Override
    public void setTree(final TreeForTreeLayout<T_TREE_NODE> tree) {
        this.tree = tree;
    }
}
