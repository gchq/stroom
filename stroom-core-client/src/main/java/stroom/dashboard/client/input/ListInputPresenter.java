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

import stroom.dashboard.client.input.ListInputPresenter.ListInputView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentChangeEvent;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.HasParams;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.query.api.Param;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ListInputPresenter
        extends AbstractComponentPresenter<ListInputView>
        implements ListInputUiHandlers, HasParams {

    public static final String TAB_TYPE = "list-input-component";
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "list-input",
                    "List Input",
                    ComponentUse.INPUT);

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
    public void onValueChanged(final WordItem value) {
        setSettings(getListInputSettings().copy().value(value.getWord()).build());
        ComponentChangeEvent.fire(this, this);
        setDirty(true);
    }

    @Override
    public List<Param> getParams() {
        final List<Param> list = new ArrayList<>();
        final String key = NullSafe.trim(getListInputSettings().getKey());
        final String value = NullSafe.trim(getView().getSelectedValue());
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

        getView().setAllowTextEntry(settings.isAllowTextEntry());

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
                            getView().setValues(values);
                            getView().setSelectedValue(selectedValue);
                        } else {
                            getView().setValues(Collections.emptyList());
                            getView().setSelectedValue(null);
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        } else {
            final List<WordItem> simpleValues = NullSafe.stream(settings.getValues())
                    .map(WordItem::simpleWord)
                    .collect(Collectors.toList());
            getView().setValues(simpleValues);
            getView().setSelectedValue(WordItem.simpleWord(settings.getValue()));
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
    public void changeSettings() {
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

    // --------------------------------------------------------------------------------


    public interface ListInputView extends View, HasUiHandlers<ListInputUiHandlers> {

        void setValues(List<WordItem> values);

        void setSelectedValue(WordItem selected);

        String getSelectedValue();

        void setAllowTextEntry(boolean allowTextEntry);
    }


    // --------------------------------------------------------------------------------


    public static class WordItem implements HasDisplayValue {

        public static final WordItem EMPTY = new WordItem("", null);

        private final String word;
        private final DocRef sourceDocRef;

        public WordItem(final String word, final DocRef sourceDocRef) {
            this.word = word;
            this.sourceDocRef = sourceDocRef;
        }

        /**
         * For non dictionary based lists
         *
         * @param word
         */
        public static WordItem simpleWord(final String word) {
            if (NullSafe.isNonBlankString(word)) {
                return new WordItem(word, null);
            } else {
                return EMPTY;
            }
        }

        public static WordItem sourcedWord(final String word, final WordList wordList) {
            if (NullSafe.isNonBlankString(word)) {
                return wordList.getWord(word)
                        .map(wordObj -> {
                            final DocRef sourceDocRef = wordList.getSource(wordObj).orElse(null);
                            return new WordItem(word, sourceDocRef);
                        })
                        .orElse(EMPTY);
            } else {
                return EMPTY;
            }
        }

        public WordItem(final Word word, final WordList wordList) {
            this.word = Objects.requireNonNull(word).getWord();
            this.sourceDocRef = wordList.getSource(word).orElseThrow();
        }

        public String getWord() {
            return word;
        }

        public Optional<DocRef> getSourceDocRef() {
            return Optional.ofNullable(sourceDocRef);
        }

        @Override
        public String getDisplayValue() {
            return word;
        }
    }
}
