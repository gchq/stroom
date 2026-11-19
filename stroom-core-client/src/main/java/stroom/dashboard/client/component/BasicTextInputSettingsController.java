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

/// A Text Input pane's Basic settings tab ([BasicTextInputSettings]): its name, its parameter's
/// key, and what its value must be. A pattern that isn't a regular expression is refused by the
/// settings dialog, as its field says why.
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
            getView().setFormat(settings.getFormat());
            getView().setPattern(settings.getPattern());
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
                .format(getView().getFormat())
                .pattern(getView().getPattern())
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TextInputComponentSettings oldSettings = (TextInputComponentSettings) componentConfig.getSettings();
        final TextInputComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getKey(), newSettings.getKey()) &&
                              Objects.equals(oldSettings.getFormat(), newSettings.getFormat()) &&
                              Objects.equals(oldSettings.getPattern(), newSettings.getPattern());

        return !equal;
    }
}
