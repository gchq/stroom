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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.stepping.client.presenter.SteppingPresenter.SteppingView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class SteppingViewImpl extends ViewImpl implements SteppingView, RequiresResize, ProvidesResize {

    private final Widget widget;
    @UiField
    ThinSplitLayoutPanel bottomLayout;
    @UiField
    FlowPanel left;
    @UiField
    FlowPanel right;
    @UiField
    ScrollPanel treeContainer;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public SteppingViewImpl(final Binder binder) {
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
    public void setTreeHeight(final int height) {
        bottomLayout.setWidgetSize(treeContainer, height);
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
    public void setTreeView(final View view) {
        treeContainer.setWidget(view.asWidget());
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    public interface Binder extends UiBinder<Widget, SteppingViewImpl> {

    }
}
