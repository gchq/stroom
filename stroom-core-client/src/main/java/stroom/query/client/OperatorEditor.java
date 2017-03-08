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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import stroom.item.client.ItemListBox;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.client.TermEditor.Resources;

public class OperatorEditor extends Composite {
    private static Resources resources;
    private final FlowPanel layout;
    private final ItemListBox<Op> listBox;
    private ExpressionOperator operator;
    private boolean reading;
    private boolean editing;
    private ExpressionUiHandlers uiHandlers;

    public OperatorEditor() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        listBox = new ItemListBox<>();
        listBox.addItems(Op.values());

        fixStyle(listBox, 50);

        listBox.addSelectionHandler(event -> {
            if (!reading && operator != null) {
                operator.setOp(event.getSelectedItem());
                fireDirty();
            }
        });

        layout = new FlowPanel();
        layout.add(listBox);

        layout.setVisible(false);
        layout.setStyleName(resources.style().layout());
        initWidget(layout);
    }

    public void startEdit(final ExpressionOperator operator) {
        if (!editing) {
            reading = true;

            this.operator = operator;

            // Select the current value.
            listBox.setSelectedItem(operator.getOp());

            Scheduler.get().scheduleDeferred(() -> layout.setVisible(true));

            reading = false;
            editing = true;
        }
    }

    public void endEdit() {
        if (editing) {
            layout.setVisible(false);

            editing = false;
        }
    }

    private void fireDirty() {
        if (!reading) {
            if (uiHandlers != null) {
                uiHandlers.fireDirty();
            }
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    private void fixStyle(final Widget widget, final int width) {
        widget.addStyleName(resources.style().item());
        widget.getElement().getStyle().setWidth(width, Unit.PX);
    }
}
