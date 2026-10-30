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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.widget.htree.client.CellRenderer;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.SingleItemSelectionModel;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class PipelineElementRenderer
        implements CellRenderer<PipelineElement>, NodeExtentProvider<PipelineElement> {

    private final PipelineElementBoxFactory pipelineElementBoxFactory;
    private final FlowPanel panel;
    // Where a faded element's box goes, and which are faded
    private final FlowPanel fadedPanel;
    private final Predicate<PipelineElement> faded;
    private final List<PipelineElementBox> boxes = new ArrayList<>();

    // Cache the PipelineElementBox objects so we don't need to keep creating them.
    // This avoids flickr as we can just change their style rather than creating new and adding style.
    private final Map<String, PipelineElementBox> elementIdToBoxMap = new HashMap<>();
    // The width of each element's box: the widest in its column (its depth in the tree), so that the
    // boxes of a column line up on both sides and no connector crosses into a box. Worked out again
    // before each layout, as names (and so widths) can change.
    private final Map<PipelineElement, Double> columnWidths = new HashMap<>();
    private DefaultTreeForTreeLayout<PipelineElement> tree;
    private SelectionModel<PipelineElement> selectionModel;

    /// @param panel                     Where the boxes go.
    /// @param fadedPanel                Where the boxes of faded elements go instead.
    /// @param faded                     Whether an element is faded.
    /// @param pipelineElementBoxFactory Makes the boxes.
    public PipelineElementRenderer(final FlowPanel panel,
                                   final FlowPanel fadedPanel,
                                   final Predicate<PipelineElement> faded,
                                   final PipelineElementBoxFactory pipelineElementBoxFactory) {
        this.panel = panel;
        this.fadedPanel = fadedPanel;
        this.faded = faded;
        this.pipelineElementBoxFactory = pipelineElementBoxFactory;
    }

    @Override
    public void render(final TreeLayout<PipelineElement> treeLayout,
                       final Bounds bounds,
                       final PipelineElement pipelineElement) {
        final boolean selected = selectionModel != null && selectionModel.isSelected(pipelineElement);
        final PipelineElementBox pipelineElementBox = createBox(pipelineElement);
        pipelineElementBox.setSelected(selected);
        final List<PipelineElement> children = treeLayout.getTree().getChildren(pipelineElement);
        final boolean hasChildren = children != null && !children.isEmpty();
        pipelineElementBox.setPorts(hasChildren);
        setTreeItem(pipelineElementBox, pipelineElement, hasChildren, selected);

        // Placed by pipeline-element-box.css's rule, which reads these
        final Element boxElement = pipelineElementBox.getElement();
        CssProperties.set(boxElement, "--pipeline-element__left", bounds.getX() + "px");
        CssProperties.set(boxElement, "--pipeline-element__top", bounds.getY() + "px");
        CssProperties.set(boxElement, "--pipeline-element__width", bounds.getWidth() + "px");

        if (faded.test(pipelineElement)) {
            fadedPanel.add(pipelineElementBox);
        } else {
            panel.add(pipelineElementBox);
        }
        boxes.add(pipelineElementBox);
    }

    // The box's place in the tree, for assistive technology, and whether it is the tree's tab stop:
    // the selected element, else the source when nothing is selected
    private void setTreeItem(final PipelineElementBox pipelineElementBox,
                             final PipelineElement pipelineElement,
                             final boolean hasChildren,
                             final boolean selected) {
        int level = 1;
        PipelineElement parent = tree == null
                ? null
                : tree.getParent(pipelineElement);
        final List<PipelineElement> siblings = parent == null
                ? List.of(pipelineElement)
                : tree.getChildrenList(parent);
        while (parent != null) {
            level++;
            parent = tree.getParent(parent);
        }
        final boolean nothingSelected =
                !(selectionModel instanceof final SingleItemSelectionModel<PipelineElement> model)
                || model.getSelectedObject() == null;
        final boolean tabStop = selected || (nothingSelected && level == 1);
        pipelineElementBox.setTreeItem(level,
                siblings.indexOf(pipelineElement) + 1,
                siblings.size(),
                hasChildren,
                tabStop);
    }

    @Override
    public Dimension getExtents(final PipelineElement pipelineElement) {
        final Dimension size = measure(pipelineElement);
        if (columnWidths.isEmpty()) {
            setColumnWidths();
        }
        final Double columnWidth = columnWidths.get(pipelineElement);
        return new Dimension(columnWidth != null
                ? columnWidth
                : size.getWidth(), size.getHeight());
    }

    /// Sets the tree whose elements are drawn, so that each column can be as wide as its widest
    /// element.
    ///
    /// @param tree The tree. Can be null.
    public void setTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        this.tree = tree;
        columnWidths.clear();
    }

    // The width of each element: the widest at its depth in the tree
    private void setColumnWidths() {
        if (tree == null || tree.getRoot() == null) {
            return;
        }
        final Map<PipelineElement, Integer> depths = new HashMap<>();
        addDepths(tree.getRoot(), 0, depths);
        final Map<Integer, Double> widestAtDepth = new HashMap<>();
        depths.forEach((element, depth) ->
                widestAtDepth.merge(depth, measure(element).getWidth(), Math::max));
        depths.forEach((element, depth) -> columnWidths.put(element, widestAtDepth.get(depth)));
    }

    private void addDepths(final PipelineElement element,
                           final int depth,
                           final Map<PipelineElement, Integer> depths) {
        depths.put(element, depth);
        for (final PipelineElement child : tree.getChildrenList(element)) {
            addDepths(child, depth + 1, depths);
        }
    }

    // An element's box's own size, at the width its content needs
    private Dimension measure(final PipelineElement pipelineElement) {
        final PipelineElementBox pipelineElementBox = createBox(pipelineElement);
        CssProperties.remove(pipelineElementBox.getElement(), "--pipeline-element__width");
        RootPanel.get().add(pipelineElementBox);
        // Its own size, not its scroll size, which would count the port dots over its borders
        final double width = pipelineElementBox.getElement().getOffsetWidth();
        final double height = pipelineElementBox.getElement().getOffsetHeight();
        RootPanel.get().remove(pipelineElementBox);
        return new Dimension(width, height);
    }

    private PipelineElementBox createBox(final PipelineElement pipelineElement) {
        if (pipelineElement == null) {
            return null;
        } else {
            final PipelineElementBox pipelineElementBox = elementIdToBoxMap.computeIfAbsent(
                    pipelineElement.getId(),
                    k -> pipelineElementBoxFactory.create(pipelineElement));
            // Refresh any styling on the box based on the PipelineElement state
            pipelineElementBox.refresh(pipelineElement);
            return pipelineElementBox;
        }
    }

    public void clear() {
        boxes.clear();
        columnWidths.clear();
    }

    public List<PipelineElementBox> getBoxes() {
        return boxes;
    }

    public void setSelectionModel(final SelectionModel<PipelineElement> selectionModel) {
        this.selectionModel = selectionModel;
    }
}
