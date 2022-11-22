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

import stroom.dashboard.client.input.ListInputPresenter.ListInputView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.Param;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;

public class ListInputPresenter extends AbstractComponentPresenter<ListInputView> implements
        ListInputUiHandlers {

    public static final ComponentType TYPE = new ComponentType(1, "list-input", "List Input", ComponentUse.INPUT);

    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private final RestFactory restFactory;

    @Inject
    public ListInputPresenter(final EventBus eventBus,
                              final ListInputView view,
                              final Provider<ListInputSettingsPresenter> settingsPresenterProvider,
                              final RestFactory restFactory) {
        super(eventBus, view, settingsPresenterProvider);
        this.restFactory = restFactory;
        view.setUiHandlers(this);
    }

    @Override
    public void onValueChanged(final String value) {
        setSettings(getListInputSettings().copy().value(value).build());
        updateParams(value);

//        if (!EqualsUtil.isEquals(currentParams, trimmed)) {
//            setDirty(true);
//
//            currentParams = trimmed;
//            start();
//        }
    }

    private void updateParams(final String value) {
        final String key = getListInputSettings().getKey();
        final String componentId = getComponentConfig().getId();
        getDashboardContext().removeParams(componentId);

        if (key != null && key.trim().length() > 0 && value != null && value.trim().length() > 0) {
            final Param param = new Param(key.trim(), value.trim());
            getDashboardContext().addParams(componentId, Collections.singletonList(param));
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
        if (!(settings instanceof ListInputComponentSettings)) {
            setSettings(createSettings());
        }

        update(getListInputSettings());
    }

    private void update(final ListInputComponentSettings settings) {
        if (settings.isUseDictionary() &&
                settings.getDictionary() != null) {
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(words -> {
                        if (words != null) {
                            getView().setValues(words);
                            getView().setSelectedValue(settings.getValue());
                        }
                    })
                    .call(WORD_LIST_RESOURCE)
                    .getWords(settings.getDictionary().getUuid());
        } else {
            getView().setValues(settings.getValues());
            getView().setSelectedValue(settings.getValue());
        }

        updateParams(settings.getValue());
    }

    public ListInputComponentSettings getListInputSettings() {
        return (ListInputComponentSettings) getSettings();
    }

    private ListInputComponentSettings createSettings() {
        return ListInputComponentSettings.builder().build();
    }


    @Override
    public void link() {
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        update(getListInputSettings());
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    public interface ListInputView extends View, HasUiHandlers<ListInputUiHandlers> {

        void setValues(List<String> values);

        void setSelectedValue(String selected);

        String getSelectedValue();
    }
}
