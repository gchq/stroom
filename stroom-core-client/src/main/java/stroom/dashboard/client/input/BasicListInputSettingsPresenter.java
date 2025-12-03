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

import stroom.dashboard.client.input.BasicListInputSettingsPresenter.BasicListInputSettingsView;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public class BasicListInputSettingsPresenter
        extends BasicSettingsTabPresenter<BasicListInputSettingsView>
        implements Focus {

    private final DocSelectionBoxPresenter dictionaryPresenter;

    @Inject
    public BasicListInputSettingsPresenter(final EventBus eventBus,
                                           final BasicListInputSettingsView view,
                                           final DocSelectionBoxPresenter dictionaryPresenter) {
        super(eventBus, view);
        this.dictionaryPresenter = dictionaryPresenter;

        dictionaryPresenter.setIncludedTypes(DictionaryDoc.TYPE);
        dictionaryPresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setDictionaryView(dictionaryPresenter.getView());
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ListInputComponentSettings settings = (ListInputComponentSettings) componentConfig.getSettings();
        if (settings != null) {
            getView().setKey(settings.getKey());
            getView().setValues(settings.getValues());
            getView().setUseDictionary(settings.isUseDictionary());
            dictionaryPresenter.setSelectedEntityReference(settings.getDictionary(), true);
            getView().setAllowTextEntry(settings.isAllowTextEntry());
        }
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final ListInputComponentSettings oldSettings = (ListInputComponentSettings) result.getSettings();
        final ListInputComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private ListInputComponentSettings writeSettings(final ListInputComponentSettings settings) {
        return settings
                .copy()
                .key(getView().getKey())
                .values(getView().getValues())
                .useDictionary(getView().isUseDictionary())
                .dictionary(dictionaryPresenter.getSelectedEntityReference())
                .allowTextEntry(getView().isAllowTextEntry())
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final ListInputComponentSettings oldSettings = (ListInputComponentSettings) componentConfig.getSettings();
        final ListInputComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getKey(), newSettings.getKey()) &&
                Objects.equals(oldSettings.getValues(), newSettings.getValues()) &&
                Objects.equals(oldSettings.isUseDictionary(), newSettings.isUseDictionary()) &&
                Objects.equals(oldSettings.getDictionary(), newSettings.getDictionary()) &&
                Objects.equals(oldSettings.isAllowTextEntry(), newSettings.isAllowTextEntry());

        return !equal;
    }


    // --------------------------------------------------------------------------------


    public interface BasicListInputSettingsView extends BasicSettingsView {

        String getKey();

        void setKey(String key);

        List<String> getValues();

        void setValues(List<String> values);

        boolean isUseDictionary();

        void setUseDictionary(boolean useDictionary);

        void setDictionaryView(View view);

        boolean isAllowTextEntry();

        void setAllowTextEntry(boolean allowTextEntry);
    }
}
