/*
 * Copyright 2026 Crown Copyright
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

package stroom.credentials.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LayerContainerImpl;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A credential's tabs: a tab bar above the tab showing.
public class CredentialEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainerImpl layerContainer;

    private Consumer<TabData> onTabSelected = tab -> {
    };
    private Consumer<ShowMenuEvent> onShowMenu = event -> {
    };

    /// Makes the tabs, with none until [#addTab(TabData)].
    public CredentialEdit() {
        initWidget(BINDER.createAndBindUi(this));
        tabBar.addSelectionHandler(event -> onTabSelected.accept(event.getSelectedItem()));
        tabBar.addShowMenuHandler(event -> onShowMenu.accept(event));
    }

    /// @param tab A tab to add to the tab bar.
    public void addTab(final TabData tab) {
        tabBar.addTab(tab);
    }

    /// Shows a tab as selected, with its content.
    ///
    /// @param tab   The tab.
    /// @param layer Its content.
    public void showTab(final TabData tab, final Layer layer) {
        tabBar.selectTab(tab);
        layerContainer.show(layer);
    }

    /// @param onTabSelected Given the tab the user selects.
    public void setOnTabSelected(final Consumer<TabData> onTabSelected) {
        this.onTabSelected = onTabSelected;
    }

    /// @param onShowMenu Given the tab bar's request for a menu.
    public void setOnShowMenu(final Consumer<ShowMenuEvent> onShowMenu) {
        this.onShowMenu = onShowMenu;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CredentialEdit> {

    }
}
