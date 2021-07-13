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

import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.TabBar;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CurveTabLayoutViewImpl extends ViewWithUiHandlers<CurveTabLayoutUiHandlers> implements CurveTabLayoutView {

    private final Widget widget;

    @UiField
    Button menu;
    @UiField
    SimplePanel tabBarOuter;
    @UiField
    TabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public CurveTabLayoutViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        menu.getElement().setInnerHTML(Images.MENU);
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

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        getUiHandlers().maximise();
    }

    public interface Binder extends UiBinder<Widget, CurveTabLayoutViewImpl> {

    }
}
