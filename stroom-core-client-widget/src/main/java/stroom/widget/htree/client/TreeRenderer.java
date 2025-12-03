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
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.view.client.SelectionModel;
import com.google.gwt.view.client.SetSelectionModel;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeRenderer<T> {

    public static final String SHADOW_LAYER = "shadow";
    public static final String ITEM_LAYER = "item";
    public static final String ARROW_LAYER = "arrow";

    private static final double PADDING = 5;

    private final LayeredCanvas canvas;
    private final CellRenderer<T> cellRenderer;
    private final ConnectorRenderer<T> connectorRenderer;
    private TreeLayout<T> treeLayout;
    private SelectionModel<T> selectionModel;

    private Set<HandlerRegistration> reg;
    private Set<T> selectedSet;
    private T lastItemUnderCursor;

    public TreeRenderer(final LayeredCanvas canvas, final CellRenderer<T> cellRenderer,
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
            final Bounds paddedBounds = new Bounds(bounds.getX(), bounds.getY(), bounds.getWidth() + (2 * PADDING),
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

    private void drawItem(final TreeLayout<T> treeLayout, final T item) {
        Bounds bounds = treeLayout.getNodeBounds().get(item);
        bounds = pad(bounds);

        drawItem(treeLayout, bounds, item);
    }

    private void drawItem(final TreeLayout<T> treeLayout, final Bounds bounds, final T item) {
        final boolean selected = selectionModel != null && selectionModel.isSelected(item);
        final boolean mouseOver = item.equals(lastItemUnderCursor);

        cellRenderer.render(bounds, item, mouseOver, selected);

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

    public void setSelectionModel(final SelectionModel<T> selectionModel) {
        this.selectionModel = selectionModel;
        unbind();
        bind();
    }

    private void unbind() {
        if (reg != null) {
            for (final HandlerRegistration handlerRegistration : reg) {
                handlerRegistration.removeHandler();
            }
            reg.clear();
        }
    }

    private void bind() {
        if (selectionModel != null) {
            final SetSelectionModel<T> setSelectionModel = (SetSelectionModel<T>) selectionModel;
            selectedSet = new HashSet<>(setSelectionModel.getSelectedSet());

            final Canvas topCanvas = canvas.getLayer(TreeRenderer.ARROW_LAYER);
            registerHandler(topCanvas.addClickHandler(event -> {
                final double x = event.getX() - PADDING;
                final double y = event.getY() - PADDING;
                final TreeForTreeLayout<T> tree = treeLayout.getTree();
                if (tree != null) {
                    final T root = tree.getRoot();
                    if (root != null) {
                        final T item = getItemAtPos(x, y, root);
                        if (item != null) {
                            selectionModel.setSelected(item, true);

                        } else if (selectionModel instanceof MySingleSelectionModel<?>) {
                            final MySingleSelectionModel<T> singleSelectionModel =
                                    (MySingleSelectionModel<T>) setSelectionModel;
                            final T lastSelection = singleSelectionModel.getSelectedObject();
                            if (lastSelection != null) {
                                selectionModel.setSelected(lastSelection, false);
                            }
                        }
                    }
                }
            }));
            registerHandler(topCanvas.addMouseMoveHandler(event -> {
                final double x = event.getX() - PADDING;
                final double y = event.getY() - PADDING;
                final TreeForTreeLayout<T> tree = treeLayout.getTree();
                if (tree != null) {
                    final T root = tree.getRoot();
                    if (root != null) {
                        final T item = getItemAtPos(x, y, root);
                        // Remove highlight from previous item.
                        if (lastItemUnderCursor != null && !lastItemUnderCursor.equals(item)) {
                            final boolean selected = setSelectionModel.getSelectedSet()
                                    .contains(lastItemUnderCursor);
                            final boolean mouseOver = false;
                            Bounds bounds = treeLayout.getNodeBounds().get(lastItemUnderCursor);
                            // The last item might have been removed from
                            // the
                            // tree.
                            if (bounds != null) {
                                bounds = pad(bounds);
                                cellRenderer.render(bounds, lastItemUnderCursor, mouseOver, selected);
                            }
                        }

                        // Highlight new item.
                        if (item != null && !item.equals(lastItemUnderCursor)) {
                            final boolean selected = setSelectionModel.getSelectedSet().contains(item);
                            final boolean mouseOver = true;
                            Bounds bounds = treeLayout.getNodeBounds().get(item);
                            bounds = pad(bounds);
                            cellRenderer.render(bounds, item, mouseOver, selected);
                        }

                        // Set new item.
                        lastItemUnderCursor = item;

                        // Set the cursor.
                        if (item != null) {
                            topCanvas.getElement().getStyle().setCursor(Cursor.POINTER);
                        } else {
                            topCanvas.getElement().getStyle().setCursor(Cursor.DEFAULT);
                        }
                    }
                }
            }));
            registerHandler(selectionModel.addSelectionChangeHandler(event -> {
                // Select items.
                for (final T selected : setSelectionModel.getSelectedSet()) {
                    final boolean alreadySelected = selectedSet.remove(selected);
                    if (!alreadySelected) {
                        Bounds bounds = treeLayout.getNodeBounds().get(selected);
                        bounds = pad(bounds);
                        cellRenderer.render(bounds, selected, selected.equals(lastItemUnderCursor), true);
                    }
                }

                // Deselect everything else from previous selection.
                for (final T selected : selectedSet) {
                    final Bounds bounds = treeLayout.getNodeBounds().get(selected);
                    if (bounds != null) {
                        final Bounds paddedBounds = pad(bounds);
                        cellRenderer.render(paddedBounds, selected, selected.equals(lastItemUnderCursor), false);
                    }
                }

                // Save current selection.
                selectedSet = new HashSet<>(setSelectionModel.getSelectedSet());
            }));
        }
    }

    private void registerHandler(final HandlerRegistration handlerRegistration) {
        if (reg == null) {
            reg = new HashSet<>();
        }
        reg.add(handlerRegistration);
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
