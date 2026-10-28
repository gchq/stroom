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

import stroom.data.client.view.ClassificationLabel;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Component;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.presenter.client.Layer;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.tab.client.LayerContainerImpl;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.util.client.DisabledState;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.UIObject;
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
    HTMLPanel rightPanel;
    @UiField
    SimplePanel navigatorContainer;
    @UiField
    DivElement sourceLink;
    @UiField
    SimplePanel progressBarPanel;
    @UiField
    LayerContainerImpl layerContainer;
    @UiField
    SpinnerLarge spinner;

    private boolean sourceLinkEnabled = true;
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
        rightPanel.addDomHandler(event -> {
            if (isSourceLink(event.getNativeEvent().getEventTarget().cast())) {
                activateSourceLink();
            }
        }, ClickEvent.getType());
        rightPanel.addDomHandler(event -> {
            final int keyCode = event.getNativeKeyCode();
            if ((keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE)
                && isSourceLink(event.getNativeEvent().getEventTarget().cast())) {
                event.preventDefault();
                activateSourceLink();
            }
        }, KeyDownEvent.getType());
    }

    private boolean isSourceLink(final Element target) {
        return target != null && sourceLink.isOrHasChild(target);
    }

    private void activateSourceLink() {
        if (sourceLinkEnabled) {
            onSourceLink.run();
        }
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
        UIObject.setVisible(sourceLink, visible);
        sourceLinkEnabled = enabled;
        if (enabled) {
            sourceLink.addClassName("enabled");
        } else {
            sourceLink.removeClassName("enabled");
        }
        DisabledState.set(sourceLink, !enabled);
    }

    /// @param navigator The navigator of the data's parts and records, or null for none.
    public void setNavigator(final IsWidget navigator) {
        if (navigator != null) {
            navigatorContainer.setWidget(navigator);
        } else {
            navigatorContainer.clear();
        }
    }

    /// @param progress How far through the data the content shown is, or null for none.
    public void setProgress(final IsWidget progress) {
        if (progress != null) {
            progressBarPanel.setWidget(progress);
        } else {
            progressBarPanel.clear();
        }
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
