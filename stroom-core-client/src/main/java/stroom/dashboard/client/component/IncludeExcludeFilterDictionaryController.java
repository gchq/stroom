/*
 * Copyright 2018 Crown Copyright
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

import stroom.alert.client.event.ConfirmEvent;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Fills a filter's dictionaries ([IncludeExcludeFilterDictionary]), a list its owner's template
/// declares: Add picks a dictionary to add, Remove removes those selected once confirmed.
///
/// ```java
/// make(dictionariesProvider).attach(view.getIncludeDictionaries());
/// ```
public class IncludeExcludeFilterDictionaryController extends Controller {

    private final Provider<ExplorerPopupController> dictionarySelection;
    private IncludeExcludeFilterDictionary view;
    private List<DocRef> dictionaries = new ArrayList<>();

    /// Made by GIN.
    ///
    /// @param eventBus            The application's event bus.
    /// @param dictionarySelection Makes the popup that picks a dictionary to add.
    @Inject
    public IncludeExcludeFilterDictionaryController(final EventBus eventBus,
                                                    final Provider<ExplorerPopupController> dictionarySelection) {
        super(eventBus);
        this.dictionarySelection = dictionarySelection;
    }

    /// Attaches this to the list it fills.
    ///
    /// @param view The list, e.g. one a template declares.
    public void attach(final IncludeExcludeFilterDictionary view) {
        this.view = Objects.requireNonNull(view, "view");
        view.setOnAdd(this::onAdd);
        view.setOnRemove(this::onRemove);
        refresh();
    }

    private void onAdd() {
        final ExplorerPopupController chooser = dictionarySelection.get();
        chooser.setIncludeNullSelection(false);
        chooser.setCaption("Choose Dictionary");
        chooser.setIncludedTypes(DictionaryDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.showOnce(this, docRef -> {
            if (docRef != null && !dictionaries.contains(docRef)) {
                dictionaries.add(docRef);
                refresh();
            }
        });
    }

    private void onRemove() {
        final MultiSelectionModel<DocRef> selectionModel = view.getSelectionModel();
        final List<DocRef> selected = selectionModel.getSelectedItems();
        if (NullSafe.hasItems(selected)) {
            String message = "Are you sure you want to remove this dictionary?";
            if (selected.size() > 1) {
                message = "Are you sure you want to remove these dictionaries?";
            }

            ConfirmEvent.fire(this,
                    message,
                    result -> {
                        if (result) {
                            dictionaries.removeAll(selected);
                            selectionModel.clear();
                            refresh();
                        }
                    });
        }
    }

    /// @param dictionaries The dictionaries to show, or `null` for none.
    public void setDictionaries(final List<DocRef> dictionaries) {
        this.dictionaries = dictionaries == null
                ? new ArrayList<>()
                : new ArrayList<>(dictionaries);
        refresh();
    }

    /// @return The dictionaries shown.
    public List<DocRef> getDictionaries() {
        return dictionaries;
    }

    private void refresh() {
        if (view != null) {
            view.setData(dictionaries);
        }
    }
}
