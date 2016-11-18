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

package stroom.dashboard.client.main;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class DashboardViewImpl extends ViewWithUiHandlers<DashboardUiHandlers> implements DashboardPresenter.DashboardView {
    private final Widget widget;
    @UiField
    FlowPanel left;
    @UiField
    FlowPanel right;
    @UiField
    TextBox params;
    @UiField
    ResizeSimplePanel content;

    @Inject
    public DashboardViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void addWidgetLeft(final Widget widget) {
        left.add(widget);
    }

    @Override
    public void addWidgetRight(final Widget widget) {
        right.add(widget);
    }

    @Override
    public String getParams() {
        return params.getText();
    }

    @Override
    public void setParams(final String params) {
        this.params.setText(params);
    }

    @Override
    public void setContent(final View view) {
        content.setWidget(view.asWidget());
    }

    @UiHandler("params")
    public void onParamsKeyDown(final KeyDownEvent event) {
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_ENTER:
                onParamsChanged();
                break;
            case KeyCodes.KEY_TAB:
                onParamsChanged();
                break;
            case KeyCodes.KEY_ESCAPE:
                onParamsChanged();
                break;
        }
    }

    @UiHandler("params")
    public void onParamsBlur(final BlurEvent event) {
        onParamsChanged();
    }

    private void onParamsChanged() {
        if (getUiHandlers() != null) {
            getUiHandlers().onParamsChanged(params.getText());
        }
    }

    public interface Binder extends UiBinder<Widget, DashboardViewImpl> {
    }
}
