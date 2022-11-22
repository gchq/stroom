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

import stroom.dashboard.client.input.MultiInputPresenter.MultiInputView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.query.QueryButtons;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.MultiInputComponentSettings;
import stroom.dispatch.client.RestFactory;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class MultiInputPresenter extends AbstractComponentPresenter<MultiInputView> implements MultiInputUiHandlers {

    public static final ComponentType TYPE = new ComponentType(1, "multi-input", "Multi Input");

    @Inject
    public MultiInputPresenter(final EventBus eventBus,
                               final MultiInputView view,
                               final RestFactory restFactory) {
        super(eventBus, view, null);
        view.setUiHandlers(this);
    }

    @Override
    public void onValueChanged(final String value) {
//        String trimmed = "";
//        if (params != null && params.trim().length() > 0) {
//            trimmed = params.trim();
//        }
//
//        if (!EqualsUtil.isEquals(currentParams, trimmed)) {
//            setDirty(true);
//
//            currentParams = trimmed;
//            start();
//        }
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
        final ComponentSettings settings = componentConfig.getSettings();
        if (settings instanceof MultiInputComponentSettings) {
            final MultiInputComponentSettings multiInputComponentSettings = (MultiInputComponentSettings) settings;
            getView().setValue(multiInputComponentSettings.getText());
        }
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

    public interface MultiInputView extends View, HasUiHandlers<MultiInputUiHandlers> {

        void setValue(String value);

        String getValue();

        QueryButtons getQueryButtons();
    }
}
