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

package stroom.dashboard.client.table;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.dashboard.client.table.ExpressionPresenter.ExpressionView;
import stroom.widget.button.client.ImageButton;

public class ExpressionViewImpl extends ViewWithUiHandlers<ExpressionUiHandlers>implements ExpressionView {
    public interface Resources extends ClientBundle {
        ImageResource expression();
    }

    public interface Binder extends UiBinder<Widget, ExpressionViewImpl> {
    }

    private final Widget widget;

    private static Resources resources;

    @UiField
    TextArea expression;
    @UiField
    ImageButton addFunction;

    @Inject
    public ExpressionViewImpl(final Binder binder) {
        if (resources == null) {
            resources = GWT.create(Resources.class);
        }

        widget = binder.createAndBindUi(this);

        addFunction.setEnabledImage(resources.expression());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getExpression() {
        return this.expression.getText();
    }

    @Override
    public void setExpression(final String expression) {
        this.expression.setText(expression);
    }

    @UiHandler("addFunction")
    public void onAddFunctionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAddFunction(event);
        }
    }

    @Override
    public void focus() {
        expression.setFocus(true);
    }

    @Override
    public int getCursorPos() {
        return expression.getCursorPos();
    }

    @Override
    public void setCursorPos(final int pos) {
        expression.setCursorPos(pos);
    }

    @Override
    public int getSelectionLength() {
        return expression.getSelectionLength();
    }
}
