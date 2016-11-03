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

package stroom.widget.tab.client.view;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.widget.layout.client.view.ResizeSimplePanel;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.LayerContainer;
import stroom.widget.tab.client.presenter.TabBar;

public class CurveTabLayoutViewImpl extends ViewImpl implements CurveTabLayoutView {
    public interface Binder extends UiBinder<Widget, CurveTabLayoutViewImpl> {
    }

    private final Widget widget;

    @UiField
    ResizeSimplePanel tabBarOuter;
    @UiField
    TabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public CurveTabLayoutViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    @Override
    public void setRightIndent(final int indent) {
        tabBarOuter.getElement().getStyle().setRight(indent, Unit.PX);
    }
}
