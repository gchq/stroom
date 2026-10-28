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

import stroom.widget.component.client.Component;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.tab.client.TabBar;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The floor map's dock, which its [FloorMapDockController] fills: a curve-tab bar over a layer
/// container. Structurally the same as `LinkTabPanel`.
public class FloorMapDock extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    /// Makes the dock, with no tabs.
    public FloorMapDock() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The tab bar.
    public TabBar getTabBar() {
        return tabBar;
    }

    /// @return The container showing the active tab's content.
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapDock> {

    }
}
