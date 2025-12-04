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

package stroom.dashboard.client.unknown;

import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.shared.ComponentConfig;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class UnknownComponentPresenter extends AbstractComponentPresenter<HTMLView> {

    public static final String TAB_TYPE = "Unknown-component";
    private static final ComponentType TYPE = new ComponentType(
            99,
            "Unknown",
            "Unknown",
            ComponentUse.PANEL);

    @Inject
    public UnknownComponentPresenter(final EventBus eventBus, final HTMLView view) {
        super(eventBus, view, null);
        view.setHTML("<div style=\"padding:5px\">Unknown component</div>");
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
        getView().setHTML("<div style=\"padding:5px\">Unknown component type: " + componentConfig.getType() + "</div>");
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
