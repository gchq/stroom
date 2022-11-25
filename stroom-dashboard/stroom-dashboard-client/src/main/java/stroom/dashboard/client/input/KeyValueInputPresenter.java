/*
 * Copyright 2017 Crown Copyright
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
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.query.QueryButtons;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.KeyValueInputComponentSettings;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.ParamUtil;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class KeyValueInputPresenter extends AbstractComponentPresenter<KeyValueInputView> implements
        KeyValueInputUiHandlers {

    public static final ComponentType TYPE = new ComponentType(1,
            "key-value-input",
            "Key/Value Input",
            ComponentUse.INPUT);

    @Inject
    public KeyValueInputPresenter(final EventBus eventBus,
                                  final KeyValueInputView view,
                                  final Provider<KeyValueInputSettingsPresenter> settingsPresenterProvider,
                                  final RestFactory restFactory) {
        super(eventBus, view, settingsPresenterProvider);
        view.setUiHandlers(this);
    }

    @Override
    public void onValueChanged(final String value) {
        updateParams(value);

//        if (!EqualsUtil.isEquals(currentParams, trimmed)) {
//            setDirty(true);
//
//            currentParams = trimmed;
//            start();
//        }
    }

    private void updateParams(final String value) {
        final String componentId = getComponentConfig().getId();
        getDashboardContext().removeParams(componentId);

        final List<Param> params = ParamUtil.parse(value);
        if (params.size() > 0) {
            getDashboardContext().addParams(componentId, params);
        }
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public void setComponents(final Components components) {
        super.setComponents(components);
//        registerHandler(components.addComponentChangeHandler(event -> {
//            if (getTextSettings() != null) {
//                final Component component = event.getComponent();
//                if (getTextSettings().getTableId() == null) {
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                } else if (EqualsUtil.isEquals(getTextSettings().getTableId(), event.getComponentId())) {
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                }
//            }
//        }));
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof KeyValueInputComponentSettings)) {
            setSettings(createSettings());
        }

        getView().setValue(getKeyValueInputSettings().getText());
        updateParams(getKeyValueInputSettings().getText());
    }

    public KeyValueInputComponentSettings getKeyValueInputSettings() {
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
//        super.changeSettings();
//        update(currentTablePresenter);
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    public interface KeyValueInputView extends View, HasUiHandlers<KeyValueInputUiHandlers> {

        void setValue(String value);

        String getValue();
    }
}
