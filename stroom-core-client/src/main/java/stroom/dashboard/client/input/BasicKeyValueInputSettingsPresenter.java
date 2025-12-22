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

import stroom.dashboard.client.input.BasicKeyValueInputSettingsPresenter.BasicKeyValueInputSettingsView;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.KeyValueInputComponentSettings;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class BasicKeyValueInputSettingsPresenter
        extends BasicSettingsTabPresenter<BasicKeyValueInputSettingsView>
        implements Focus {

    @Inject
    public BasicKeyValueInputSettingsPresenter(final EventBus eventBus,
                                               final BasicKeyValueInputSettingsView view) {
        super(eventBus, view);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);


//        final KeyValueInputComponentSettings settings = (KeyValueInputComponentSettings) componentData.getSettings();
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final KeyValueInputComponentSettings oldSettings = (KeyValueInputComponentSettings) result.getSettings();
        final KeyValueInputComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private KeyValueInputComponentSettings writeSettings(final KeyValueInputComponentSettings settings) {
        return settings
                .copy()
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final KeyValueInputComponentSettings oldSettings =
                (KeyValueInputComponentSettings) componentConfig.getSettings();
        final KeyValueInputComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = true;

        return !equal;
    }

    public interface BasicKeyValueInputSettingsView extends BasicSettingsView {

    }
}
