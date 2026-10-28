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

package stroom.floormap.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.tab.client.TabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.LinkedHashMap;
import java.util.Map;

/// A reusable, tabbed dock ([FloorMapDock]) hosted on the right-hand edge of the Floor Map
/// canvas in both the Map and Editor tabs. It holds control panels as curve tabs (the
/// same chrome as the document tab bar) and shows the active tab's content in a
/// [LayerContainer].
///
/// Panels are registered with [#addTab(String, IsWidget)]; the
/// first tab added becomes the active tab. This single call is the integration
/// point for further panels — nothing else in the dock needs to
/// change to host a new tab.
///
/// Dock chrome state (which tab is active) is held transiently here; the host
/// views own visibility and width (via their split panels).
public class FloorMapDockController extends Controller {

    private final FloorMapDock view;

    /// Tab → its content's layer, in insertion (display) order.
    private final Map<TabData, Layer> tabContent = new LinkedHashMap<>();

    private TabData selectedTab;

    /// Made by GIN, with its dock.
    ///
    /// @param eventBus The application's event bus, which the tab bar's overflow menu is shown on.
    /// @param view     The dock.
    @Inject
    public FloorMapDockController(final EventBus eventBus,
                                  final FloorMapDock view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(view.getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
        // The bar's overflow menu (tabs that don't fit) is shown by whoever handles the event
        own(view.getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    /// Adds a tab showing the given content; its owner keeps owning it.
    ///
    /// @param label   the tab label
    /// @param content the panel shown when the tab is active, e.g. a presenter or component
    /// @return the [TabData] handle for the new tab
    public TabData addTab(final String label, final IsWidget content) {
        final TabData tab = new TabDataImpl(label);
        addTab(tab, content);
        return tab;
    }

    /// Adds a tab showing the given content, with full control over the
    /// tab's presentation (icon/tooltip/closeable) via the supplied [TabData].
    ///
    /// @param tab     the tab descriptor
    /// @param content the panel shown when the tab is active, e.g. a presenter or component
    public void addTab(final TabData tab, final IsWidget content) {
        tabContent.put(tab, content instanceof final Layer layer
                ? layer
                : new ControllerLayer(content));
        view.getTabBar().addTab(tab);
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
        final Layer content = tabContent.get(tab);
        if (content == null) {
            return;
        }
        view.getLayerContainer().show(content);
        view.getTabBar().selectTab(tab);
        selectedTab = tab;
    }

    /// @return the currently active tab, or `null` if the dock has no tabs.
    public TabData getSelectedTab() {
        return selectedTab;
    }

    /// @return the tab bar, for callers that need to hide/show or query tabs.
    public TabBar getTabBar() {
        return view.getTabBar();
    }
}
