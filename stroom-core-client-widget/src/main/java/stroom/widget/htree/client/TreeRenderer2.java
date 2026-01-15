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

package stroom.widget.htree.client;

import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.TreeForTreeLayout;
import stroom.widget.htree.client.treelayout.TreeLayout;

import java.util.List;

public class TreeRenderer2<T> {

    public static final String SHADOW_LAYER = "shadow";
    public static final String ITEM_LAYER = "item";
    public static final String ARROW_LAYER = "arrow";

    private static final double PADDING = 5;

    private final LayeredCanvas canvas;
    private final CellRenderer2<T> cellRenderer;
    private final ConnectorRenderer<T> connectorRenderer;
    private TreeLayout<T> treeLayout;
    private Bounds paddedBounds;

    public TreeRenderer2(final LayeredCanvas canvas,
                         final CellRenderer2<T> cellRenderer,
                         final ConnectorRenderer<T> connectorRenderer) {
        this.canvas = canvas;
        this.cellRenderer = cellRenderer;
        this.connectorRenderer = connectorRenderer;
    }

    public void setTreeLayout(final TreeLayout<T> treeLayout) {
        this.treeLayout = treeLayout;
    }

    public void draw() {
        if (treeLayout != null) {
            treeLayout.layout();

            final Bounds bounds = treeLayout.getBounds();
            paddedBounds = new Bounds(bounds.getX(), bounds.getY(), bounds.getWidth() + (2 * PADDING),
                    bounds.getHeight() + (2 * PADDING));
            canvas.setSize((int) paddedBounds.getWidth(), (int) paddedBounds.getHeight());
            canvas.clear();

            // Draw the tree.
            final TreeForTreeLayout<T> tree = treeLayout.getTree();
            if (tree != null) {
                final T root = tree.getRoot();
                if (root != null) {
                    drawItem(treeLayout, root);
                }
            }
        }
    }

    public Bounds getBounds() {
        return paddedBounds;
    }

    private void drawItem(final TreeLayout<T> treeLayout, final T item) {
        Bounds bounds = treeLayout.getNodeBounds().get(item);
        bounds = pad(bounds);

        drawItem(treeLayout, bounds, item);
    }

    private void drawItem(final TreeLayout<T> treeLayout, final Bounds bounds, final T item) {
        cellRenderer.render(treeLayout, bounds, item);

        final List<T> children = treeLayout.getTree().getChildren(item);
        if (children != null && children.size() > 0) {
            final T first = treeLayout.getTree().getFirstChild(item);
            final T last = treeLayout.getTree().getLastChild(item);

            for (final T child : children) {
                Bounds childBounds = treeLayout.getNodeBounds().get(child);
                childBounds = pad(childBounds);

                drawItem(treeLayout, childBounds, child);

                connectorRenderer.render(treeLayout, bounds.getMaxX(), bounds.getCenterY(), childBounds.getX(),
                        childBounds.getCenterY(), child.equals(first), child.equals(last));
            }
        }
    }

    private Bounds pad(final Bounds bounds) {
        return new Bounds(bounds.getX() + PADDING, bounds.getY() + PADDING, bounds.getWidth(), bounds.getHeight());
    }

    public T getItemAtPos(final double x, final double y) {
        final TreeForTreeLayout<T> tree = treeLayout.getTree();
        if (tree != null) {
            final T root = tree.getRoot();
            if (root != null) {
                final T item = getItemAtPos(x - PADDING, y - PADDING, root);
                return item;
            }
        }
        return null;
    }

    private T getItemAtPos(final double x, final double y, final T item) {
        T found = null;
        final Bounds bounds = treeLayout.getNodeBounds().get(item);
        if (x >= bounds.getX() && x <= bounds.getMaxX() && y >= bounds.getY() && y <= bounds.getMaxY()) {
            found = item;
        } else {
            final Iterable<T> children = treeLayout.getTree().getChildren(item);
            if (children != null) {
                for (final T child : children) {
                    found = getItemAtPos(x, y, child);
                    if (found != null) {
                        break;
                    }
                }
            }
        }

        return found;
    }
}
