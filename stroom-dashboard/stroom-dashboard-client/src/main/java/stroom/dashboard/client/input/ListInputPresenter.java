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

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class ListInputPresenter extends AbstractComponentPresenter<ListInputView> implements
        ListInputUiHandlers {

    public static final ComponentType TYPE = new ComponentType(1, "list-input", "List Input", ComponentUse.INPUT);

    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private final RestFactory restFactory;

    @Inject
    public ListInputPresenter(final EventBus eventBus,
                              final ListInputView view,
                              final RestFactory restFactory) {
        super(eventBus, view, null);
        this.restFactory = restFactory;
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
        if (settings instanceof ListInputComponentSettings) {
            final ListInputComponentSettings componentSettings =
                    (ListInputComponentSettings) settings;

            if (componentSettings.isUseDictionary() &&
                    componentSettings.getDictionary() != null) {
                final Rest<List<String>> rest = restFactory.create();
                rest.onSuccess(words -> {
                            if (words != null) {
                                getView().setValues(words);
                                getView().setSelectedValue(componentSettings.getValue());
                            }
                        })
                        .call(WORD_LIST_RESOURCE)
                        .getWords(componentSettings.getDictionary().getUuid());
            } else {
                getView().setValues(componentSettings.getValues());
                getView().setSelectedValue(componentSettings.getValue());
            }
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

    public interface ListInputView extends View, HasUiHandlers<ListInputUiHandlers> {

        void setValues(List<String> values);

        void setSelectedValue(String selected);

        String getSelectedValue();
    }
}
