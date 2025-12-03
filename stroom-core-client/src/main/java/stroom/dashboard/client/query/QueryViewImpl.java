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

package stroom.dashboard.client.query;

import stroom.dashboard.client.query.QueryPresenter.QueryView;
import stroom.query.client.view.QueryButtons;
import stroom.svg.client.Preset;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class QueryViewImpl extends ViewImpl implements QueryView, RequiresResize, ProvidesResize {

    private final Widget widget;

    @UiField
    FlowPanel expressionTree;
    @UiField
    ButtonPanel buttonPanelLeft;
    @UiField
    ButtonPanel buttonPanelRight;
    @UiField
    QueryButtons queryButtons;

    @Inject
    public QueryViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onResize() {
        ((RequiresResize) widget).onResize();
    }

    @Override
    public ButtonView addButtonLeft(final Preset preset) {
        return buttonPanelLeft.addButton(preset);
    }

    @Override
    public ButtonView addButtonRight(final Preset preset) {
        return buttonPanelRight.addButton(preset);
    }

    @Override
    public void addButtonRight(final ButtonView button) {
        buttonPanelRight.addButton(button);
    }

    @Override
    public void setExpressionView(final View view) {
        expressionTree.add(view.asWidget());
    }

    @Override
    public void onSearching(final boolean searching) {
        queryButtons.onSearching(searching);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        queryButtons.setEnabled(enabled);
    }

    @Override
    public QueryButtons getQueryButtons() {
        return queryButtons;
    }

    public interface Binder extends UiBinder<Widget, QueryViewImpl> {

    }
}
