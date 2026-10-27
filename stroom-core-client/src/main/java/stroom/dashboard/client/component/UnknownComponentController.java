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

import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.shared.ComponentConfig;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A dashboard pane of a type Stroom doesn't know ([UnknownComponent]), e.g. one a newer Stroom
/// added: it names the type, and keeps the pane's config as it was.
public class UnknownComponentController extends DashboardComponentController {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "Unknown-component";
    private static final ComponentType TYPE = new ComponentType(
            99,
            "Unknown",
            "Unknown",
            ComponentUse.PANEL);

    private final UnknownComponent view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public UnknownComponentController(final EventBus eventBus, final UnknownComponent view) {
        super(eventBus, null);
        this.view = view;
        initWidget(view);
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public void link() {
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);
        view.setType(componentConfig.getType());
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
