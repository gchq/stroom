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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.BasicTextInputSettingsPresenter.BasicTextInputSettingsView;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextInputComponentSettings;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

public class BasicTextInputSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTextInputSettingsView>
        implements Focus {

    @Inject
    public BasicTextInputSettingsPresenter(final EventBus eventBus,
                                           final BasicTextInputSettingsView view) {
        super(eventBus, view);
    }

    @Override
    public void focus() {
        getView().focus();
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

        final boolean equal = Objects.equals(oldSettings.getKey(), newSettings.getKey());

        return !equal;
    }

    public interface BasicTextInputSettingsView extends BasicSettingsView {

        String getKey();

        void setKey(String key);
    }
}
