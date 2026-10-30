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

import stroom.dashboard.shared.ComponentConfig;
import stroom.widget.component.client.Component;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The base of the controller of a pane's Basic settings tab: it shows the pane's id and name, and
/// writes the name back. A subclass reads and writes the pane's own settings too.
///
/// @param <C> The tab's component.
public abstract class BasicSettingsTabController<C extends Component & BasicSettingsFields>
        extends SettingsTabController
        implements Focus {

    private final C view;

    /// @param eventBus The application's event bus.
    /// @param view     The tab's content.
    protected BasicSettingsTabController(final EventBus eventBus, final C view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// @return The tab's content.
    protected C getView() {
        return view;
    }

    @Override
    public void focus() {
        view.focus();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        view.setId(componentConfig.getId());
        view.setName(componentConfig.getName());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        return componentConfig
                .copy()
                .name(view.getName())
                .build();
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        final ComponentConfig newComponentConfig = write(componentConfig);
        return !Objects.equals(componentConfig.getName(), newComponentConfig.getName());
    }
}
