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

package stroom.receive.rules.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.tab.client.LayerContainerImpl;
import stroom.widget.tab.client.LinkTabBar;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Data Retention screen: a tab of the retention rules and a tab of their impact.
public class DataRetention extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainerImpl layerContainer;

    /// Makes the screen, with no content until it is given it.
    public DataRetention() {
        initWidget(BINDER.createAndBindUi(this));
        tabBar.setTabPanel(layerContainer.getElement());
    }

    /// @return The tab bar: Rules and Impact Summary.
    public LinkTabBar getTabBar() {
        return tabBar;
    }

    /// @return Where the selected tab is shown.
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    interface Binder extends UiBinder<Widget, DataRetention> {

    }
}
