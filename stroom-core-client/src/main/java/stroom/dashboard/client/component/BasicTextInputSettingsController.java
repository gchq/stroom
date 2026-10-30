/*
 * Copyright 2022 Crown Copyright
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
import stroom.dashboard.shared.TextInputComponentSettings;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// A Text Input pane's Basic settings tab ([BasicTextInputSettings]): its name and its parameter's
/// key.
public class BasicTextInputSettingsController extends BasicSettingsTabController<BasicTextInputSettings> {

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public BasicTextInputSettingsController(final EventBus eventBus,
                                            final BasicTextInputSettings view) {
        super(eventBus, view);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final TextInputComponentSettings settings = (TextInputComponentSettings) componentConfig.getSettings();
        if (settings != null) {
            getView().setKey(settings.getKey());
        }
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final TextInputComponentSettings oldSettings = (TextInputComponentSettings) result.getSettings();
        final TextInputComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private TextInputComponentSettings writeSettings(final TextInputComponentSettings settings) {
        return settings
                .copy()
                .key(getView().getKey())
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TextInputComponentSettings oldSettings = (TextInputComponentSettings) componentConfig.getSettings();
        final TextInputComponentSettings newSettings = writeSettings(oldSettings);

        return !Objects.equals(oldSettings.getKey(), newSettings.getKey());
    }
}
