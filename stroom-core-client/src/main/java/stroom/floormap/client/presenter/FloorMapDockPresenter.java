/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.presenter;

import stroom.floormap.client.presenter.FloorMapDockPresenter.FloorMapDockView;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.LinkedHashMap;
import java.util.Map;

/// A reusable, tabbed dock hosted on the right-hand edge of the Floor Map canvas
/// in both the Map and Editor tabs. It holds control panels as curve tabs (the
/// same chrome as the document tab bar) and shows the active tab's content in a
/// [LayerContainer].
///
/// Panels are registered with [#addTab(String, PresenterWidget)]; the
/// first tab added becomes the active tab. This single call is the integration
/// point for further panels — nothing else in the dock needs to
/// change to host a new tab.
///
/// Dock chrome state (which tab is active) is held transiently here; the host
/// views own visibility and width (via their split panels).
public class FloorMapDockPresenter extends MyPresenterWidget<FloorMapDockView> {

    /// Tab → its content presenter, in insertion (display) order.
    private final Map<TabData, PresenterWidget<?>> tabContent = new LinkedHashMap<>();

    private TabData selectedTab;

    @Inject
    public FloorMapDockPresenter(final EventBus eventBus,
                                 final FloorMapDockView view) {
        super(eventBus, view);
        registerHandler(getView().getTabBar()
                .addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    /// Adds a tab hosting the given content presenter.
    ///
    /// @param label   the tab label
    /// @param content the presenter shown when the tab is active
    /// @return the [TabData] handle for the new tab
    public TabData addTab(final String label, final PresenterWidget<?> content) {
        final TabData tab = new TabDataImpl(label);
        addTab(tab, content);
        return tab;
    }

    /// Adds a tab hosting the given content presenter, with full control over the
    /// tab's presentation (icon/tooltip/closeable) via the supplied [TabData].
    ///
    /// @param tab     the tab descriptor
    /// @param content the presenter shown when the tab is active
    public void addTab(final TabData tab, final PresenterWidget<?> content) {
        tabContent.put(tab, content);
        getView().getTabBar().addTab(tab);
        // The first tab added becomes the active tab.
        if (selectedTab == null) {
            selectTab(tab);
        }
    }

    /// Makes the given tab active, showing its content.
    ///
    /// @param tab the tab to select; ignored if `null` or unknown
    public void selectTab(final TabData tab) {
        if (tab == null) {
            return;
        }
        final PresenterWidget<?> content = tabContent.get(tab);
        if (content == null) {
            return;
        }
        getView().getLayerContainer().show((Layer) content);
        getView().getTabBar().selectTab(tab);
        selectedTab = tab;
    }

    /// @return the currently active tab, or `null` if the dock has no tabs.
    public TabData getSelectedTab() {
        return selectedTab;
    }

    /// @return the tab bar, for callers that need to hide/show or query tabs.
    public TabBar getTabBar() {
        return getView().getTabBar();
    }

    /// View contract for the dock: a tab bar plus a container for the active
    /// tab's content.
    public interface FloorMapDockView extends View {

        TabBar getTabBar();

        LayerContainer getLayerContainer();
    }
}
