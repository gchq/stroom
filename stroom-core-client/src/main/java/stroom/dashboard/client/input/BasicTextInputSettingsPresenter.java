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

package stroom.dashboard.client.input;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.input.BasicTextInputSettingsPresenter.BasicTextInputSettingsView;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextInputComponentSettings;
import stroom.dashboard.shared.TextInputFormat;
import stroom.dashboard.shared.TextInputValidator;

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
    public boolean validate() {
        if (getView().getFormat() == TextInputFormat.PATTERN
            && !TextInputValidator.isValidPattern(getView().getPattern())) {
            AlertEvent.fireError(this, "The regex pattern is missing or is not a valid regular expression.", null);
            return false;
        }
        return super.validate();
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

    public interface BasicTextInputSettingsView extends BasicSettingsView {

        String getKey();

        void setKey(String key);

        TextInputFormat getFormat();

        void setFormat(TextInputFormat format);

        String getPattern();

        void setPattern(String pattern);
    }
}
