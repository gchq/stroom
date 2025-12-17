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

import stroom.dashboard.client.input.TextInputPresenter.TextInputView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentChangeEvent;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.HasParams;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TextInputComponentSettings;
import stroom.query.api.Param;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class TextInputPresenter
        extends AbstractComponentPresenter<TextInputView>
        implements TextInputUiHandlers, HasParams {

    public static final String TAB_TYPE = "TextInput";
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "text-input",
                    "Text Input",
                    ComponentUse.INPUT);

    @Inject
    public TextInputPresenter(final EventBus eventBus,
                              final TextInputView view,
                              final Provider<TextInputSettingsPresenter> settingsPresenterProvider) {
        super(eventBus, view, settingsPresenterProvider);
        view.setUiHandlers(this);
    }

    @Override
    public void onValueChanged(final String value) {
        setSettings(getTextInputSettings().copy().value(value).build());
        ComponentChangeEvent.fire(this, this);
        setDirty(true);
    }

    @Override
    public List<Param> getParams() {
        final List<Param> list = new ArrayList<>();
        final String key = getTextInputSettings().getKey();
        final String value = getView().getValue();
        if (key != null && key.trim().length() > 0 && value != null && value.trim().length() > 0) {
            final Param param = new Param(key.trim(), value.trim());
            list.add(param);
        }
        return list;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TextInputComponentSettings)) {
            setSettings(createSettings());
        }

        update(getTextInputSettings());
    }

    private void update(final TextInputComponentSettings settings) {
        getView().setValue(settings.getValue());
    }

    private TextInputComponentSettings getTextInputSettings() {
        return (TextInputComponentSettings) getSettings();
    }

    private TextInputComponentSettings createSettings() {
        return TextInputComponentSettings.builder().build();
    }

    @Override
    public void link() {
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        update(getTextInputSettings());
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface TextInputView extends View, HasUiHandlers<TextInputUiHandlers> {

        void setValue(String value);

        String getValue();
    }
}
