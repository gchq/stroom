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

package stroom.widget.tab.client.view;

import stroom.svg.shared.SvgImage;
import stroom.widget.tab.client.event.ShowTabMenuEvent.Handler;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FocusFlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CurveTabLayoutViewImpl extends ViewWithUiHandlers<CurveTabLayoutUiHandlers> implements CurveTabLayoutView {

    private static final String SHOW_SIDEBAR_TITLE = "Show Sidebar";
    private static final String HIDE_SIDEBAR_TITLE = "Hide Sidebar";
    private final Widget widget;

    @UiField(provided = true)
    FocusFlowPanel layout;
    @UiField
    Button menu;
    @UiField
    SimplePanel tabBarOuter;
    @UiField
    CurveTabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    private boolean menuVisible;

    @Inject
    public CurveTabLayoutViewImpl(final Binder binder) {
        layout = new FocusFlowPanel() {
            @Override
            public void focus() {
                menu.setFocus(true);
            }
        };
        widget = binder.createAndBindUi(this);
        SvgImageUtil.setSvgAsInnerHtml(menu, HIDE_SIDEBAR_TITLE, SvgImage.HIDE_MENU);

        menuVisible = true;
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

    @Override
    public HandlerRegistration addShowTabMenuHandler(final Handler handler) {
        return tabBar.addShowTabMenuHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        tabBar.fireEvent(event);
    }

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        if (menuVisible) {
            menuVisible = false;
            SvgImageUtil.setSvgAsInnerHtml(menu, SHOW_SIDEBAR_TITLE, SvgImage.SHOW_MENU);
        } else {
            menuVisible = true;
            SvgImageUtil.setSvgAsInnerHtml(menu, HIDE_SIDEBAR_TITLE, SvgImage.HIDE_MENU);
        }
        getUiHandlers().maximise();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, CurveTabLayoutViewImpl> {

    }
}
