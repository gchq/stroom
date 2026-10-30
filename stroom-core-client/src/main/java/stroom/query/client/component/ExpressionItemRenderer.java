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

package stroom.query.client.component;

import stroom.query.client.Item;
import stroom.query.client.Operator;
import stroom.query.client.Term;
import stroom.widget.htree.client.CellRenderer;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.SingleItemSelectionModel;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionItemRenderer implements CellRenderer<Item>, NodeExtentProvider<Item> {

    private final FlowPanel panel;
    private final OperatorEditor operatorEditor;
    private final TermEditor termEditor;
    private final List<ExpressionItemBox> boxes = new ArrayList<>();
    private SelectionModel<Item> selectionModel;
    // The item opened for editing (e.g. by a click), which shows its editor while it is selected;
    // others are shown as their labels, selected or not
    private Item editingItem;

    public ExpressionItemRenderer(final FlowPanel panel,
                                  final OperatorEditor operatorEditor,
                                  final TermEditor termEditor) {
        this.panel = panel;

        this.operatorEditor = operatorEditor;
        this.termEditor = termEditor;
    }

    @Override
    public void render(final TreeLayout<Item> treeLayout, final Bounds bounds, final Item item) {
        final boolean selected = selectionModel != null && selectionModel.isSelected(item);
        Item selectedItem = null;
        if (selectionModel != null && selectionModel instanceof SingleItemSelectionModel<?>) {
            selectedItem = ((SingleItemSelectionModel<Item>) selectionModel).getSelectedObject();
        }
        final Item editing = selectedItem != null && selectedItem.equals(editingItem)
                ? selectedItem
                : null;

        final double height = 25;
        final double x = bounds.getX();
        final double y = bounds.getY() + ((bounds.getHeight() - height) / 2);

        final ExpressionItemBox box = new ExpressionItemBox(treeLayout, item, selectionModel != null);
        // Placed by custom properties its class uses (expression-item.css)
        CssProperties.set(box.getElement(), "--expression-item__left", x + "px");
        CssProperties.set(box.getElement(), "--expression-item__top", y + "px");

        panel.add(box);
        boxes.add(box);

        if (editing != null && editing.equals(item) && item instanceof final Operator operator) {
            operatorEditor.startEdit(operator);
            box.setEditor(operatorEditor, operatorEditor.getRow());
        } else if (editing != null && editing.equals(item) && item instanceof final Term term) {
            termEditor.startEdit(term);
            box.setEditor(termEditor, termEditor.getRow());
        } else {
            if (editing == null) {
                operatorEditor.removeFromParent();
                termEditor.removeFromParent();
            }
            box.setLabel(item.toString());
        }

        box.setSelected(selected);
        setTreeItem(box, (DefaultTreeForTreeLayout<Item>) treeLayout.getTree(), item, selected, selectedItem);
    }

    // The box's place in the tree, its name, and whether it is the tree's tab stop: the selected
    // item, else the root when nothing is selected (or the tree can't be selected)
    private void setTreeItem(final ExpressionItemBox box,
                             final DefaultTreeForTreeLayout<Item> tree,
                             final Item item,
                             final boolean selected,
                             final Item selectedItem) {
        int level = 1;
        Item parent = tree.getParent(item);
        final List<Item> siblings = parent == null
                ? List.of(item)
                : tree.getChildrenList(parent);
        while (parent != null) {
            level++;
            parent = tree.getParent(parent);
        }
        final boolean tabStop = selected || (selectedItem == null && tree.getParent(item) == null);
        box.setTreeItem(level,
                siblings.indexOf(item) + 1,
                siblings.size(),
                !tree.getChildrenList(item).isEmpty(),
                tabStop,
                getName(tree, item) + (box.isEnabled()
                        ? ""
                        : ", disabled"));
    }

    /// Gets an item's name, e.g. for assistive technology: a term's text ("StreamId > 1000"), or an
    /// operator's and how many items it has ("OR, 2 items").
    ///
    /// @param tree The tree.
    /// @param item The item.
    /// @return Its name.
    public static String getName(final DefaultTreeForTreeLayout<Item> tree, final Item item) {
        if (item instanceof Operator) {
            final int count = tree == null
                    ? 0
                    : tree.getChildrenList(item).size();
            return item + ", " + count + (count == 1
                    ? " item"
                    : " items");
        }
        final String text = item.toString().trim();
        return text.isEmpty()
                ? "New term"
                : text;
    }

    /// Sets the item opened for editing, which shows its editor while it is selected, or none.
    ///
    /// @param item The item, or null for none.
    public void setEditingItem(final Item item) {
        this.editingItem = item;
    }

    /// @param item An item.
    /// @return Whether it is the item opened for editing (see [#setEditingItem]).
    public boolean isEditingItem(final Item item) {
        return item != null && item.equals(editingItem);
    }

    @Override
    public Dimension getExtents(final Item item) {
        if (item instanceof Operator) {
            return new Dimension(54, 25);
        }

        return new Dimension(400, 25);
    }

    public void clear() {
        boxes.clear();
    }

    public List<ExpressionItemBox> getBoxes() {
        return boxes;
    }

    public void setSelectionModel(final SelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
    }
}
