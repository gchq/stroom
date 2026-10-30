/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client.component;

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Ask Stroom AI's settings: a tab bar above the tab showing, then buttons to go back to the
/// defaults or to make the settings the defaults.
public class AskStroomAiConfigEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainer layerContainer;
    @UiField
    Button restoreFromDefaults;
    @UiField
    Button setDefaults;

    private Consumer<TabData> onTabSelected = tab -> {
    };
    private Runnable onRestoreFromDefaults = () -> {
    };
    private Runnable onSetDefaults = () -> {
    };

    /// Makes the dialog, with no tabs until [#addTab(TabData)] and Set As Default hidden.
    public AskStroomAiConfigEditor() {
        initWidget(BINDER.createAndBindUi(this));
        restoreFromDefaults.addClickHandler(this::onRestoreFromDefaultsClick);
        setDefaults.addClickHandler(this::onSetDefaultsClick);
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
    public void setSetDefaultsVisible(final boolean visible) {
        setDefaults.setVisible(visible);
    }

    /// @return Restore From Defaults, whose spinner shows while the defaults are fetched.
    public TaskMonitorFactory getRestoreFromDefaultsTaskMonitor() {
        return restoreFromDefaults;
    }

    /// @return Set As Default, whose spinner shows while the defaults are saved.
    public TaskMonitorFactory getSetDefaultsTaskMonitor() {
        return setDefaults;
    }

    /// @param onTabSelected Given the tab the user selects.
    public void setOnTabSelected(final Consumer<TabData> onTabSelected) {
        this.onTabSelected = onTabSelected;
    }

    /// @param onRestoreFromDefaults Called when Restore From Defaults is pressed.
    public void setOnRestoreFromDefaults(final Runnable onRestoreFromDefaults) {
        this.onRestoreFromDefaults = onRestoreFromDefaults;
    }

    /// @param onSetDefaults Called when Set As Default is pressed.
    public void setOnSetDefaults(final Runnable onSetDefaults) {
        this.onSetDefaults = onSetDefaults;
    }

    private void onRestoreFromDefaultsClick(final ClickEvent event) {
        onRestoreFromDefaults.run();
    }

    private void onSetDefaultsClick(final ClickEvent event) {
        onSetDefaults.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AskStroomAiConfigEditor> {

    }
}
