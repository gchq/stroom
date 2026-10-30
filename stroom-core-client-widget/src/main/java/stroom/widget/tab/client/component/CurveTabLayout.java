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

package stroom.widget.tab.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.tab.client.CurveTabBar;
import stroom.widget.tab.client.CurveTabLayoutPanel;
import stroom.widget.tab.client.TabBar;
import stroom.widget.tab.client.event.ShowTabMenuEvent.Handler;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The document tabs' layout: the Hide Sidebar button, the curved tab bar beside it and, below them,
/// the selected tab's content.
public class CurveTabLayout extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String SHOW_SIDEBAR_TITLE = "Show Sidebar";
    private static final String HIDE_SIDEBAR_TITLE = "Hide Sidebar";

    @UiField
    CurveTabLayoutPanel layout;
    @UiField
    ToolbarButton menu;
    @UiField
    CurveTabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    private boolean menuVisible = true;
    private Runnable onToggleSidebar = () -> {
    };

    /// Makes the layout, with no tabs, the sidebar shown.
    public CurveTabLayout() {
        initWidget(BINDER.createAndBindUi(this));
        // Focusing the layout (as the main view does) focuses its first button
        layout.setOnFocus(() -> menu.setFocus(true));
        tabBar.setTabPanel(layerContainer.getElement());
        menu.setOnClick(this::onMenu);
    }

    /// @param role The layout's WAI-ARIA role, e.g. `main`, a landmark screen readers can go to.
    public void setRole(final String role) {
        getElement().setAttribute("role", role);
    }

    /// @param onToggleSidebar Run when the user hides or shows the sidebar.
    public void setOnToggleSidebar(final Runnable onToggleSidebar) {
        this.onToggleSidebar = Objects.requireNonNull(onToggleSidebar, "onToggleSidebar");
    }

    /// @return The tab bar.
    public TabBar getTabBar() {
        return tabBar;
    }

    /// @return Where the selected tab's content is shown.
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    /// @param handler Given each request for a tab's menu.
    /// @return Its registration.
    public HandlerRegistration addShowTabMenuHandler(final Handler handler) {
        return tabBar.addShowTabMenuHandler(handler);
    }

    private void onMenu() {
        // The button is named by its title, so it says what pressing it will do
        if (menuVisible) {
            menuVisible = false;
            menu.setSvg(SvgImage.SHOW_MENU);
            menu.setTitle(SHOW_SIDEBAR_TITLE);
        } else {
            menuVisible = true;
            menu.setSvg(SvgImage.HIDE_MENU);
            menu.setTitle(HIDE_SIDEBAR_TITLE);
        }
        onToggleSidebar.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CurveTabLayout> {

    }
}
