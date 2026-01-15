/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.docref.HasDisplayValue;
import stroom.widget.tab.client.presenter.TabData;

import com.gwtplatform.mvp.client.Layer;

public interface Component extends TabData, Layer, HasDisplayValue {

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

    TabConfig getTabConfig();

    void setTabConfig(TabConfig tabConfig);

    void onClose();

    void onRemove();

    String getId();

    void read(ComponentConfig componentConfig);

    ComponentConfig write();

    void setDesignMode(boolean designMode);

    default void onContentTabVisible(final boolean visible) {

    }
}
