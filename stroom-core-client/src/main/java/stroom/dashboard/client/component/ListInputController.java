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

import stroom.dashboard.client.main.PaneRegistry.ComponentType;
import stroom.dashboard.client.main.PaneRegistry.ComponentUse;
import stroom.dashboard.client.model.WordItem;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.Param;
import stroom.query.client.result.table.HasParams;
import stroom.util.shared.NullSafe;
import stroom.widget.chooser.client.MultiChooserController;
import stroom.widget.chooser.client.OpenChooser;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// A dashboard's List Input pane ([ListInput]): the value chosen, with the key from its settings,
/// is a query parameter of the dashboard. Its values are typed in its settings or are a
/// dictionary's words. When its settings allow several values to be chosen, they are ticked in a
/// chooser ([MultiChooserController]) and the parameter is the values ticked, joined with commas.
public class ListInputController extends DashboardPaneController implements HasParams {

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
    private final Provider<MultiChooserController<WordItem>> multiChooserProvider;
    private final OpenChooser openChooser = new OpenChooser();
    // The values offered, in the list's order, without blank ones
    private List<WordItem> values = Collections.emptyList();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param settingsControllerProvider Makes the pane's settings dialog.
    /// @param restFactory                Fetches a dictionary's words.
    /// @param multiChooserProvider       Makes the chooser of several values.
    @Inject
    public ListInputController(final EventBus eventBus,
                               final ListInput view,
                               final Provider<ListInputSettingsController> settingsControllerProvider,
                               final RestFactory restFactory,
                               final Provider<MultiChooserController<WordItem>> multiChooserProvider) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        this.restFactory = restFactory;
        this.multiChooserProvider = multiChooserProvider;
        initWidget(view);
        view.setOnValueChange(this::onValueChanged);
        view.setOnOpenValues(this::showValuesChooser);
    }

    private void onValueChanged(final WordItem value) {
        setSettings(getListInputSettings().copy().value(value.getWord()).build());
        onChange();
    }

    // Ticked values come in the order they were ticked, so are put back in the list's order
    private void onSelectedValuesChanged(final List<WordItem> selected) {
        final Set<WordItem> ticked = new HashSet<>(selected);
        final List<String> words = values.stream()
                .filter(ticked::contains)
                .map(WordItem::getWord)
                .collect(Collectors.toList());
        setSettings(getListInputSettings().copy().selectedValues(words).build());
        view.setSelectedValues(words);
        onChange();
    }

    private void showValuesChooser() {
        final Element element = view.getValuesElement();
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final MultiChooserController<WordItem> chooser = multiChooserProvider.get();
        chooser.setDataSupplier((filter, consumer) -> consumer.accept(WordItem.matching(values, filter)));
        chooser.setDisplayValueFunction(ListInput::render);
        chooser.setAccessibleName(getComponentConfig().getName());
        chooser.setSelectAllShown(true);
        chooser.setSelectedItems(getSelectedItems(getListInputSettings()));
        chooser.show(this, element, "Choose Values", this::onSelectedValuesChanged);
        openChooser.opened(chooser, element);
    }

    // The values offered that are ticked in the settings, in the list's order; a value ticked once
    // but no longer offered (e.g. taken out of the dictionary) is left out
    private List<WordItem> getSelectedItems(final ListInputComponentSettings settings) {
        final Set<String> words = new HashSet<>(NullSafe.list(settings.getSelectedValues()));
        return values.stream()
                .filter(value -> words.contains(value.getWord()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Param> getParams() {
        final List<Param> list = new ArrayList<>();
        final ListInputComponentSettings settings = getListInputSettings();
        final String key = NullSafe.trim(settings.getKey());
        final String value = settings.isMultiSelect()
                ? ListInputComponentSettings.joinSelectedValues(getSelectedItems(settings)
                        .stream()
                        .map(WordItem::getWord)
                        .collect(Collectors.toList()))
                : NullSafe.trim(view.getSelectedValue());
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
        view.setMultiSelect(settings.isMultiSelect());
        // A typed value only makes sense when one value is chosen
        view.setAllowTextEntry(settings.isAllowTextEntry() && !settings.isMultiSelect());

        if (settings.isUseDictionary() &&
            settings.getDictionary() != null) {
            restFactory
                    .create(WORD_LIST_RESOURCE)
                    .method(res -> res.getWords(settings.getDictionary().getUuid()))
                    .onSuccess(wordList -> {
                        if (wordList != null && !wordList.isEmpty()) {
                            final List<WordItem> dictionaryValues = wordList.getSortedList()
                                    .stream()
                                    .map(wordObj ->
                                            new WordItem(wordObj, wordList))
                                    .collect(Collectors.toList());

                            final WordItem selectedValue = WordItem.sourcedWord(settings.getValue(), wordList);
                            setValues(dictionaryValues);
                            view.setSelectedValue(selectedValue);
                        } else {
                            setValues(Collections.emptyList());
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
            setValues(simpleValues);
            view.setSelectedValue(WordItem.simpleWord(settings.getValue()));
        }
    }

    // Offers the values, and shows those of them ticked
    private void setValues(final List<WordItem> offered) {
        view.setValues(offered);
        // Blank lines in the values become empty values, which can't usefully be ticked
        values = offered.stream()
                .filter(value -> NullSafe.isNonBlankString(value.getWord()))
                .collect(Collectors.toList());
        view.setSelectedValues(getSelectedItems(getListInputSettings())
                .stream()
                .map(WordItem::getWord)
                .collect(Collectors.toList()));
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

}
