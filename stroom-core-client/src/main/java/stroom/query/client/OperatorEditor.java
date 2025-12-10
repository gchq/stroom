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

import stroom.item.client.EventBinder;
import stroom.item.client.SelectionBox;
import stroom.query.api.ExpressionOperator.Op;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class OperatorEditor extends Composite {

    private final FlowPanel layout;
    private final SelectionBox<Op> listBox;
    private Operator operator;
    private boolean reading;
    private boolean editing;
    private ExpressionUiHandlers uiHandlers;
    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            super.onBind();
            registerHandler(listBox.addValueChangeHandler(event -> {
                if (!reading && operator != null) {
                    operator.setOp(event.getValue());
                    fireDirty();
                }
            }));
        }
    };

    public OperatorEditor() {
        listBox = new SelectionBox<>();
        listBox.addItems(Op.values());

        fixStyle(listBox, 55);

        final FlowPanel inner = new FlowPanel();
        inner.setStyleName("termEditor-inner");
        inner.add(listBox);

        layout = new FlowPanel();
        layout.add(inner);
        layout.setVisible(false);
        layout.setStyleName("termEditor-outer");

        initWidget(layout);
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        super.onUnload();
        eventBinder.unbind();
    }

    public void startEdit(final Operator operator) {
        if (!editing) {
            reading = true;

            this.operator = operator;

            // Select the current value.
            listBox.setValue(operator.getOp());

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
        widget.addStyleName("termEditor-item");
        widget.getElement().getStyle().setWidth(width, Unit.PX);
    }
}
