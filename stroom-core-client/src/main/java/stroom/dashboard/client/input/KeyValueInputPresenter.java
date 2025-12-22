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

import stroom.dashboard.client.input.KeyValueInputPresenter.KeyValueInputView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentChangeEvent;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.HasParams;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.KeyValueInputComponentSettings;
import stroom.query.api.Param;
import stroom.query.api.ParamUtil;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class KeyValueInputPresenter
        extends AbstractComponentPresenter<KeyValueInputView>
        implements KeyValueInputUiHandlers, HasParams {

    public static final String TAB_TYPE = "key-value-input-component";
    public static final ComponentType TYPE = new ComponentType(1,
            "key-value-input",
            "Key/Value Input",
            ComponentUse.INPUT);

    @Inject
    public KeyValueInputPresenter(final EventBus eventBus,
                                  final KeyValueInputView view,
                                  final Provider<KeyValueInputSettingsPresenter> settingsPresenterProvider) {
        super(eventBus, view, settingsPresenterProvider);
        view.setUiHandlers(this);
    }

    @Override
    public void onValueChanged(final String value) {
        setSettings(getKeyValueInputSettings().copy().text(value).build());
        ComponentChangeEvent.fire(this, this);
        setDirty(true);
    }

    @Override
    public List<Param> getParams() {
        return ParamUtil.parse(getView().getValue());
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);
        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof KeyValueInputComponentSettings)) {
            setSettings(createSettings());
        }
        getView().setValue(getKeyValueInputSettings().getText());
    }

    private KeyValueInputComponentSettings getKeyValueInputSettings() {
        return (KeyValueInputComponentSettings) getSettings();
    }

    private KeyValueInputComponentSettings createSettings() {
        return KeyValueInputComponentSettings.builder().build();
    }

    @Override
    public void link() {
    }

    @Override
    public void changeSettings() {
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    public void setValue(final String value) {
        getView().setValue(value);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface KeyValueInputView extends View, HasUiHandlers<KeyValueInputUiHandlers> {

        void setValue(String value);

        String getValue();
    }
}
