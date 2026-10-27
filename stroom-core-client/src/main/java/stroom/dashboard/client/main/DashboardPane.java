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

package stroom.dashboard.client.main;

import stroom.dashboard.client.flexlayout.MutableTabConfig;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.shared.ComponentConfig;
import stroom.docref.HasDisplayValue;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.user.client.ui.IsWidget;

/// A pane of a dashboard (a table, query, visualisation, text or input), shown in a tab of the
/// dashboard's layout. Named apart from `stroom.widget.component.client.Component`, which the panes
/// become.
public interface DashboardPane extends TabData, Layer, HasDisplayValue, IsWidget {

    DashboardContext getDashboardContext();

    void setDashboardContext(DashboardContext dashboardContext);

    ComponentType getComponentType();

    ComponentConfig getComponentConfig();

    void setComponentName(String name);

    /**
     * Link components together.
     */
    void link();

    /**
     * Called when a component is requested that it show it's settings.
     */
    void showSettings();

    /**
     * Set the associated tab layout for this component.
     *
     * @param tabLayout The tab layout to associate with this component.
     */
    void setTabLayout(TabLayout tabLayout);

    MutableTabConfig getTabConfig();

    void setTabConfig(MutableTabConfig tabConfig);

    void onClose();

    void onRemove();

    String getId();

    void read(ComponentConfig componentConfig);

    ComponentConfig write();

    void setDesignMode(boolean designMode);

    /// @param onChange What to run when the user changes the pane's settings or value, e.g. to mark
    ///                 the dashboard changed.
    void setOnChange(Runnable onChange);

    default void onContentTabVisible(final boolean visible) {

    }
}
