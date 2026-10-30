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

package stroom.dashboard.client.component;

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.explorer.client.component.DocSelectionController;
import stroom.security.shared.DocumentPermission;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// A List Input pane's Basic settings tab ([BasicListInputSettings]): its name, key and values, and
/// the dictionary whose words it can offer instead.
public class BasicListInputSettingsController extends BasicSettingsTabController<BasicListInputSettings> {

    private final DocSelectionController dictionary;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the dictionary's picker.
    @Inject
    public BasicListInputSettingsController(final EventBus eventBus,
                                            final BasicListInputSettings view,
                                            final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus, view);
        dictionary = make(docSelectionProvider);
        dictionary.attach(view.getDictionaryBox());
        dictionary.setIncludedTypes(DictionaryDoc.TYPE);
        dictionary.setRequiredPermissions(DocumentPermission.USE);
        // The dictionary is used only when Use Dictionary is ticked
        dictionary.setEnabled(false);
        view.setOnUseDictionaryChange(dictionary::setEnabled);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ListInputComponentSettings settings = (ListInputComponentSettings) componentConfig.getSettings();
        if (settings != null) {
            getView().setKey(settings.getKey());
            getView().setValues(settings.getValues());
            getView().setUseDictionary(settings.isUseDictionary());
            dictionary.setEnabled(settings.isUseDictionary());
            dictionary.setSelectedEntityReference(settings.getDictionary(), true);
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
                .dictionary(dictionary.getSelectedEntityReference())
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
}
