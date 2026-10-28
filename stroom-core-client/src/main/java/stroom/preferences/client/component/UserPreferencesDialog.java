/*
 * Copyright 2018 Crown Copyright
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

package stroom.preferences.client.component;

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.LayerContainerImpl;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The user's preferences: a tab bar above the tab showing, then buttons to make the preferences
/// every user's default or to go back to the default.
public class UserPreferencesDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainerImpl layerContainer;
    @UiField
    Button setAsDefault;
    @UiField
    Button revertToDefault;

    private Consumer<TabData> onTabSelected = tab -> {
    };
    private Runnable onSetAsDefault = () -> {
    };
    private Runnable onRevertToDefault = () -> {
    };

    /// Makes the dialog, with no tabs until [#addTab(TabData)].
    public UserPreferencesDialog() {
        initWidget(BINDER.createAndBindUi(this));
        setAsDefault.setOnClick(this::onClickSetAsDefault);
        revertToDefault.setOnClick(this::onClickRevertToDefault);
        tabBar.setTabPanel(layerContainer.getElement());
        tabBar.addSelectionHandler(event -> onTabSelected.accept(event.getSelectedItem()));
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

    /// @param visible Whether Set As Default shows, for a user who may change the defaults.
    public void setSetAsDefaultVisible(final boolean visible) {
        setAsDefault.setVisible(visible);
    }

    /// @param onTabSelected Given the tab the user selects.
    public void setOnTabSelected(final Consumer<TabData> onTabSelected) {
        this.onTabSelected = onTabSelected;
    }

    /// @param onSetAsDefault Called when Set As Default is pressed.
    public void setOnSetAsDefault(final Runnable onSetAsDefault) {
        this.onSetAsDefault = onSetAsDefault;
    }

    /// @param onRevertToDefault Called when Revert To Default is pressed.
    public void setOnRevertToDefault(final Runnable onRevertToDefault) {
        this.onRevertToDefault = onRevertToDefault;
    }

    private void onClickSetAsDefault() {
        onSetAsDefault.run();
    }

    private void onClickRevertToDefault() {
        onRevertToDefault.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserPreferencesDialog> {

    }
}
