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

package stroom.query.client;

import stroom.widget.htree.client.CellRenderer2;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionItemRenderer implements CellRenderer2<Item>, NodeExtentProvider<Item> {

    private final FlowPanel panel;
    private final OperatorEditor operatorEditor;
    private final TermEditor termEditor;
    private final List<ExpressionItemBox> boxes = new ArrayList<>();
    private Item editingItem;
    private SelectionModel<Item> selectionModel;

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
        Item editing = null;
        if (selectionModel != null && selectionModel instanceof MySingleSelectionModel<?>) {
            editing = ((MySingleSelectionModel<Item>) selectionModel).getSelectedObject();
        }
        if (editing == null || !editing.equals(editingItem)) {
            // Stop editing previous item.
            stopEditing();
        }

        final double height = 25;
        final double x = bounds.getX();
        final double y = bounds.getY() + ((bounds.getHeight() - height) / 2);

        final ExpressionItemBox box = new ExpressionItemBox(treeLayout, item, selectionModel != null);
        final Style style = box.getElement().getStyle();
        style.setLeft(x, Unit.PX);
        style.setTop(y, Unit.PX);

        panel.add(box);
        boxes.add(box);

        Widget widget = null;

        if (editing != null) {
            if (editing.equals(item)) {
                if (item instanceof final Operator operator) {
                    if (editingItem == null) {
                        operatorEditor.startEdit(operator);
                    }
                } else if (item instanceof final Term term) {
                    if (editingItem == null) {
                        termEditor.startEdit(term);
                    } else {
                        termEditor.update(term);
                    }
                }
                editingItem = editing;

                if (item instanceof Operator) {
                    widget = operatorEditor;
                } else if (item instanceof Term) {
                    widget = termEditor;
                }
            }
        } else {
            stopEditing();
        }

        if (widget == null) {
            final Label label = new Label(item.toString(), false);
            label.addStyleName("expressionItemBox-label");

            final FlowPanel inner = new FlowPanel();
            inner.setStyleName("termEditor-inner");
            inner.add(label);

            final FlowPanel layout = new FlowPanel();
            layout.add(inner);
            layout.setStyleName("termEditor-outer");
            widget = layout;
        }

        box.setInnerWidget(widget);
        box.setSelected(selected);
    }

    private void stopEditing() {
        if (editingItem != null) {
            if (editingItem instanceof Operator) {
                operatorEditor.endEdit();
                operatorEditor.removeFromParent();
            } else if (editingItem instanceof Term) {
                termEditor.endEdit();
                termEditor.removeFromParent();
            }
        }
        editingItem = null;
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
