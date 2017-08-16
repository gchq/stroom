/*
 * Copyright 2016 Crown Copyright
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

public class CenteredParentTreeLayout<TreeNode> implements TreeLayout<TreeNode> {
    private final NodeExtentProvider<TreeNode> nodeExtentProvider;
    private final Configuration<TreeNode> configuration;
    private TreeForTreeLayout<TreeNode> tree;
    private Map<TreeNode, Bounds> boundsMap;
    private double boundsX;
    private double boundsY;
    private double boundsWidth;
    private double boundsHeight;

    public CenteredParentTreeLayout(final NodeExtentProvider<TreeNode> nodeExtentProvider,
                                    final Configuration<TreeNode> configuration) {
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

    private Bounds calculate(final TreeNode node, final double x, final double y, final int depth) {
        final Dimension dimension = nodeExtentProvider.getExtents(node);
        final double width = dimension.getWidth();
        final double height = dimension.getHeight();

        final double outerMinY = y;
        double outerMaxY = y + height;

        final List<TreeNode> children = tree.getChildren(node);
        if (children != null && children.size() > 0) {
            final double childX = x + width + configuration.getGapBetweenLevels(depth);
            double childY = y;

            double innerMinY = y;
            double innerMaxY = y + height;

            for (int i = 0; i < children.size(); i++) {
                final TreeNode child = children.get(i);

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
    public Map<TreeNode, Bounds> getNodeBounds() {
        return boundsMap;
    }

    @Override
    public TreeForTreeLayout<TreeNode> getTree() {
        return tree;
    }

    @Override
    public void setTree(final TreeForTreeLayout<TreeNode> tree) {
        this.tree = tree;
    }
}
