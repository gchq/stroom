/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.client;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionModel;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.widget.htree.client.CellRenderer2;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionItemRenderer implements CellRenderer2<ExpressionItem>, NodeExtentProvider<ExpressionItem> {
    private final FlowPanel panel;
    private final OperatorEditor operatorEditor;
    private final TermEditor termEditor;
    private final List<ExpressionItemBox> boxes = new ArrayList<>();
    private ExpressionItem editingItem;
    private SelectionModel<ExpressionItem> selectionModel;

    public ExpressionItemRenderer(final FlowPanel panel, final OperatorEditor operatorEditor,
                                  final TermEditor termEditor) {
        this.panel = panel;

        this.operatorEditor = operatorEditor;
        this.termEditor = termEditor;
    }

    @Override
    public void render(final TreeLayout<ExpressionItem> treeLayout, final Bounds bounds, final ExpressionItem item) {
        final boolean selected = selectionModel != null && selectionModel.isSelected(item);
        ExpressionItem editing = null;
        if (selectionModel != null && selectionModel instanceof MySingleSelectionModel<?>) {
            editing = ((MySingleSelectionModel<ExpressionItem>) selectionModel).getSelectedObject();
        }
        if (editing == null || !editing.equals(editingItem)) {
            // Stop editing previous item.
            stopEditing();
        }

        final double height = 20;
        final double x = bounds.getX();
        final double y = bounds.getY() + ((bounds.getHeight() - height) / 2);

        final String text = getText(item);

        final ExpressionItemBox box = new ExpressionItemBox(treeLayout, item, selectionModel != null);
        final Style style = box.getElement().getStyle();
        style.setLeft(x, Unit.PX);
        style.setTop(y, Unit.PX);

        panel.add(box);
        boxes.add(box);

        Widget widget = null;

        if (editing != null) {
            if (editing.equals(item)) {
                if (editingItem == null) {
                    if (item instanceof ExpressionOperator) {
                        final ExpressionOperator operator = (ExpressionOperator) item;
                        operatorEditor.startEdit(operator);
                    } else if (item instanceof ExpressionTerm) {
                        final ExpressionTerm term = (ExpressionTerm) item;
                        termEditor.startEdit(term);
                    }
                    editingItem = editing;
                }

                if (item instanceof ExpressionOperator) {
                    widget = operatorEditor;
                } else if (item instanceof ExpressionTerm) {
                    widget = termEditor;
                }
            }
        } else {
            stopEditing();
        }

        if (widget == null) {
            widget = new Label(text, false);
        }

        box.setInnerWidget(widget);
        box.setSelected(selected);
    }

    private void stopEditing() {
        if (editingItem != null) {
            if (editingItem instanceof ExpressionOperator) {
                operatorEditor.endEdit();
                operatorEditor.removeFromParent();
            } else if (editingItem instanceof ExpressionTerm) {
                termEditor.endEdit();
                termEditor.removeFromParent();
            }
        }
        editingItem = null;
    }

    @Override
    public Dimension getExtents(final ExpressionItem item) {
        if (item instanceof ExpressionOperator) {
            return new Dimension(54, 20);
        }

        return new Dimension(400, 20);
    }

    public String getText(final ExpressionItem item) {
        if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;
            return operator.getOp().getDisplayValue();
        } else if (item instanceof ExpressionTerm) {
            final ExpressionTerm term = (ExpressionTerm) item;
            final StringBuilder sb = new StringBuilder();
            if (term.getField() != null) {
                sb.append(term.getField());
                sb.append(" ");
            }
            if (term.getCondition() != null) {
                sb.append(term.getCondition().getDisplayValue());
                sb.append(" ");
            }
            if (term.getValue() != null) {
                sb.append(term.getValue());
            }
            return sb.toString();
        }
        return null;
    }

    public void clear() {
        boxes.clear();
    }

    public List<ExpressionItemBox> getBoxes() {
        return boxes;
    }

    public void setSelectionModel(final SelectionModel<ExpressionItem> selectionModel) {
        this.selectionModel = selectionModel;
    }
}
