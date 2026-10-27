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

import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.HasParams;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.Param;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/// A dashboard's List Input pane ([ListInput]): the value chosen, with the key from its settings,
/// is a query parameter of the dashboard. Its values are typed in its settings or are a
/// dictionary's words.
public class ListInputController extends DashboardComponentController implements HasParams {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "list-input-component";
    /// The pane's type, as the dashboard's Add menu offers it.
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "list-input",
                    "List Input",
                    ComponentUse.INPUT);

    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private final ListInput view;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param view                      The content.
    /// @param settingsPresenterProvider Makes the pane's settings dialog.
    /// @param restFactory               Fetches a dictionary's words.
    @Inject
    public ListInputController(final EventBus eventBus,
                               final ListInput view,
                               final Provider<ListInputSettingsController> settingsPresenterProvider,
                               final RestFactory restFactory) {
        super(eventBus, settingsPresenterProvider);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnValueChange(this::onValueChanged);
    }

    private void onValueChanged(final WordItem value) {
        setSettings(getListInputSettings().copy().value(value.getWord()).build());
        onChange();
    }

    @Override
    public List<Param> getParams() {
        final List<Param> list = new ArrayList<>();
        final String key = NullSafe.trim(getListInputSettings().getKey());
        final String value = NullSafe.trim(view.getSelectedValue());
        if (!key.isEmpty() && !value.isEmpty()) {
            final Param param = new Param(key, value);
            list.add(param);
        }
        return list;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof ListInputComponentSettings)) {
            setSettings(createSettings());
        }

        update(getListInputSettings());
    }

    private void update(final ListInputComponentSettings settings) {
        // Named for the component (e.g. "Colour"), so a rename renames it too
        view.setAccessibleName(getComponentConfig().getName());
        view.setAllowTextEntry(settings.isAllowTextEntry());

        if (settings.isUseDictionary() &&
            settings.getDictionary() != null) {
            restFactory
                    .create(WORD_LIST_RESOURCE)
                    .method(res -> res.getWords(settings.getDictionary().getUuid()))
                    .onSuccess(wordList -> {
                        if (wordList != null && !wordList.isEmpty()) {
                            final List<WordItem> values = wordList.getSortedList()
                                    .stream()
                                    .map(wordObj ->
                                            new WordItem(wordObj, wordList))
                                    .collect(Collectors.toList());

                            final WordItem selectedValue = WordItem.sourcedWord(settings.getValue(), wordList);
                            view.setValues(values);
                            view.setSelectedValue(selectedValue);
                        } else {
                            view.setValues(Collections.emptyList());
                            view.setSelectedValue(null);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        } else {
            final List<WordItem> simpleValues = NullSafe.stream(settings.getValues())
                    .map(WordItem::simpleWord)
                    .collect(Collectors.toList());
            view.setValues(simpleValues);
            view.setSelectedValue(WordItem.simpleWord(settings.getValue()));
        }
    }

    private ListInputComponentSettings getListInputSettings() {
        return (ListInputComponentSettings) getSettings();
    }

    private ListInputComponentSettings createSettings() {
        return ListInputComponentSettings.builder().build();
    }

    @Override
    public void link() {
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        update(getListInputSettings());
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
