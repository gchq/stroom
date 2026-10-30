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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.PaneDataModifier;
import stroom.widget.component.client.LayerController;

import com.google.web.bindery.event.shared.EventBus;

/// The base of the controller of a tab of a dashboard pane's settings dialog: it reads the pane's
/// config into its component, writes it back, and says whether it is valid and changed. It is given
/// the dashboard's context, e.g. to list the other panes.
public abstract class SettingsTabController extends LayerController implements PaneDataModifier {

    private DashboardContext dashboardContext;

    /// @param eventBus The application's event bus.
    protected SettingsTabController(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public DashboardContext getDashboardContext() {
        return dashboardContext;
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }
}
