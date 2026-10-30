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

package stroom.data.client.component;

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.progress.client.component.SourceProgressBar;
import stroom.widget.spinner.client.LargeBusySpinner;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A stream's data: its tabs (Info, Error, Data Preview, Meta and Context, added by its controller),
/// a link to its source and its navigator, above the content of the tab selected (layers shown by
/// its controller), the data's classification below it, and a spinner while it loads.
public class DataViewer extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ClassificationWrapper classificationWrapper;
    @UiField
    LinkTabBar tabBar;
    @UiField
    ItemNavigator navigator;
    @UiField
    LinkButton sourceLink;
    @UiField
    SourceProgressBar progressBar;
    @UiField
    LayerContainer layerContainer;
    @UiField
    LargeBusySpinner spinner;

    private Runnable onSourceLink = () -> {
    };
    private Consumer<TabData> onSelectTab = tab -> {
    };
    private Consumer<ShowMenuEvent> onShowMenu = event -> {
    };

    /// Makes the viewer, with no tabs or data.
    public DataViewer() {
        initWidget(BINDER.createAndBindUi(this));
        tabBar.setTabPanel(layerContainer.getElement());
        tabBar.setOnSelect(tab -> onSelectTab.accept(tab));
        tabBar.addShowMenuHandler(event -> onShowMenu.accept(event));
        // Disabled, the link does nothing when pressed (LinkButton)
        sourceLink.setOnClick(() -> onSourceLink.run());
    }

    /// Puts the focus on the tabs.
    public void focus() {
        tabBar.focus();
    }

    /// @param tab A tab to add, hidden until the data has it.
    public void addTab(final TabData tab) {
        tabBar.addTab(tab);
    }

    /// @param tab    A tab.
    /// @param hidden Whether it is hidden.
    public void setTabHidden(final TabData tab, final boolean hidden) {
        tabBar.setTabHidden(tab, hidden);
    }

    /// @param tab The tab to select.
    public void selectTab(final TabData tab) {
        tabBar.selectTab(tab);
    }

    /// @return The tab selected, if one is.
    public TabData getSelectedTab() {
        return tabBar.getSelectedTab();
    }

    /// @param content The content of the tab selected.
    public void showLayer(final Layer content) {
        layerContainer.show(content);
    }

    /// Shows no content.
    public void clearLayers() {
        layerContainer.clear();
    }

    /// @param visible Whether the View Source link shows.
    /// @param enabled Whether it can be pressed.
    public void setSourceLinkVisible(final boolean visible, final boolean enabled) {
        sourceLink.setVisible(visible);
        sourceLink.setAriaDisabled(!enabled);
    }

    /// @return The navigator of the data's parts and records, which its controller attaches the
    /// navigator's controller to.
    public ItemNavigator getNavigator() {
        return navigator;
    }

    /// @param visible Whether the navigator shows.
    public void setNavigatorVisible(final boolean visible) {
        navigator.setVisible(visible);
    }

    /// @return The bar showing how far through the data the content shown is.
    public SourceProgressBar getProgressBar() {
        return progressBar;
    }

    /// @param visible Whether the progress bar shows; its place is kept when it doesn't.
    public void setProgressVisible(final boolean visible) {
        progressBar.setVisible(visible);
    }

    /// @param classification The data's classification, or blank for none.
    public void setClassification(final String classification) {
        classificationWrapper.setClassification(classification);
    }

    /// @return The classification banner, for a controller to give its colours.
    public ClassificationLabel getClassificationLabel() {
        return classificationWrapper.getClassificationLabel();
    }

    /// @return The spinner shown while the data loads.
    public TaskMonitorFactory getSpinner() {
        return spinner;
    }

    /// @param onSourceLink What to run when the View Source link is pressed.
    public void setOnSourceLink(final Runnable onSourceLink) {
        this.onSourceLink = onSourceLink;
    }

    /// @param onSelectTab Given the tab the user selects.
    public void setOnSelectTab(final Consumer<TabData> onSelectTab) {
        this.onSelectTab = onSelectTab;
    }

    /// @param onShowMenu Shows a menu the tabs ask for, e.g. the list of tabs that don't fit.
    public void setOnShowMenu(final Consumer<ShowMenuEvent> onShowMenu) {
        this.onShowMenu = onShowMenu;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataViewer> {

    }
}
