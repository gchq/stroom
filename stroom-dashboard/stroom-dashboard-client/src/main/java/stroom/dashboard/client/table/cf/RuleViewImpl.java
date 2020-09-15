/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client.table.cf;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.widget.tickbox.client.view.TickBox;

public class RuleViewImpl extends ViewImpl implements RulePresenter.RuleView {
    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    TickBox hide;
    @UiField
    TextBox backgroundColor;
    @UiField
    TextBox textColor;
    @UiField
    TickBox enabled;

    @Inject
    public RuleViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setExpressionView(final View view) {
        this.expression.setWidget(view.asWidget());
    }

    @Override
    public boolean isHide() {
        return this.hide.getBooleanValue();
    }

    @Override
    public void setHide(final boolean hide) {
        this.hide.setBooleanValue(hide);
    }

    @Override
    public String getBackgroundColor() {
        return this.backgroundColor.getText();
    }

    @Override
    public void setBackgroundColor(final String backgroundColor) {
        this.backgroundColor.setText(backgroundColor);
    }

    @Override
    public String getTextColor() {
        return this.textColor.getText();
    }

    @Override
    public void setTextColor(final String textColor) {
        this.textColor.setText(textColor);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getBooleanValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setBooleanValue(enabled);
    }

    public interface Binder extends UiBinder<Widget, RuleViewImpl> {
    }
}
