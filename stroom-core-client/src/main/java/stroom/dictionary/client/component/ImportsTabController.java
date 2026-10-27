/*
 * Copyright 2026 Crown Copyright
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

package stroom.dictionary.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// A dictionary's Imports tab ([ImportsTab]): the dictionaries it imports, which can be added and
/// removed unless the dictionary is read only, and the effective words of the one selected.
public class ImportsTabController extends Controller {

    private static final String ADD_TITLE = "Add Imported Dictionary";
    private static final String REMOVE_TITLE = "Remove Imported Dictionary";

    private final DocRefListController importList;
    private final EffectiveWordsListController effectiveWords;
    private final Provider<ExplorerPopupController> docSelectionPopupProvider;
    private final ToolbarButton addButton;
    private final ToolbarButton removeButton;
    // Made when first used, and kept for the next time
    private ExplorerPopupController docSelectionPopup;
    private List<DocRef> imports = new ArrayList<>();
    private DocRef dictionary;
    private boolean readOnly = true;
    private Runnable onChange = () -> {
    };

    /// Makes the tab: the dictionaries imported, and the words they add up to.
    ///
    /// @param eventBus                  The app's event bus.
    /// @param view                      The tab.
    /// @param docRefListProvider        Makes the list of dictionaries imported.
    /// @param effectiveWords            The words the dictionary and its imports add up to.
    /// @param docSelectionPopupProvider Makes the popup that picks a dictionary to import.
    @Inject
    public ImportsTabController(final EventBus eventBus,
                                final ImportsTab view,
                                final Provider<DocRefListController> docRefListProvider,
                                final EffectiveWordsListController effectiveWords,
                                final Provider<ExplorerPopupController> docSelectionPopupProvider) {
        super(eventBus);
        this.docSelectionPopupProvider = docSelectionPopupProvider;
        this.effectiveWords = own(effectiveWords);
        importList = make(docRefListProvider);

        importList.initTableColumns("Document Name", true);
        final Toolbar toolbar = importList.getToolbar();
        toolbar.setName("Dictionary actions");
        addButton = toolbar.button(SvgImage.ADD, ADD_TITLE, this::addImport);
        removeButton = toolbar.button(SvgImage.DELETE, REMOVE_TITLE, this::removeSelected);
        own(getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (!event.getSelectionType().isDoubleSelect()
                && !event.getSelectionType().isMultiSelect()
                && getSelectionModel().getSelectedCount() == 1) {
                effectiveWords.setDocRef(getSelectionModel().getSelected());
            }
        }));

        initWidget(view);
        view.setImports(importList);
        view.setWords(effectiveWords);

        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            enableButtons();
        });
    }

    /// @param onChange What to run when the user adds or removes an import.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// Shows a dictionary's imports, with the effective words of the first.
    ///
    /// @param dictionary The dictionary.
    /// @param doc        Its document.
    public void read(final DocRef dictionary, final DictionaryDoc doc) {
        this.dictionary = dictionary;
        if (dictionary.getName() != null) {
            importList.setTableName("Dictionary '" + dictionary.getName() + "' Imports");
        }
        imports = new ArrayList<>(NullSafe.list(doc.getImports()));
        showImports();
        if (!imports.isEmpty()) {
            // Not getFirst(), which GWT doesn't emulate
            effectiveWords.setDocRef(imports.get(0));
        }
    }

    /// @param doc The document to add the imports to.
    /// @return A copy of it with the imports shown.
    public DictionaryDoc write(final DictionaryDoc doc) {
        return doc.copy()
                .imports(imports.isEmpty()
                        ? null
                        : new ArrayList<>(imports))
                .build();
    }

    private void addImport() {
        if (docSelectionPopup == null) {
            docSelectionPopup = make(docSelectionPopupProvider);
            docSelectionPopup.setIncludeNullSelection(false);
        }
        final ExplorerPopupController chooser = docSelectionPopup;
        chooser.setCaption("Import a dictionary");
        chooser.setIncludedTypes(DictionaryDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.show(guard(docRef -> {
            if (docRef != null && !docRef.equals(dictionary) && !imports.contains(docRef)) {
                imports.add(docRef);
                showImports();
                onChange.run();
            }
        }));
    }

    private void removeSelected() {
        final List<DocRef> selected = getSelectionModel().getSelectedItems();
        if (NullSafe.hasItems(selected)) {
            final String message = selected.size() > 1
                    ? "Are you sure you want to remove these imported dictionaries?"
                    : "Are you sure you want to remove this imported dictionary?";
            ConfirmEvent.fire(this, message, confirmed -> {
                if (confirmed && !isDisposed()) {
                    imports.removeAll(selected);
                    getSelectionModel().clear();
                    showImports();
                    onChange.run();
                }
            });
        }
    }

    private void showImports() {
        importList.setData(imports);
        enableButtons();
    }

    private void enableButtons() {
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && NullSafe.hasItems(getSelectionModel().getSelectedItems()));
        // The name stays, with why the buttons are disabled for a read-only dictionary
        final String suffix = readOnly
                ? " (dictionary is read only)"
                : "";
        addButton.setTitle(ADD_TITLE + suffix);
        removeButton.setTitle(REMOVE_TITLE + suffix);
    }

    private MultiSelectionModel<DocRef> getSelectionModel() {
        return importList.getSelectionModel();
    }
}
