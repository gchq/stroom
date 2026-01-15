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

package stroom.dashboard.client.table;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.WrapperView;
import stroom.dictionary.client.presenter.DocRefListPresenter;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class IncludeExcludeFilterDictionaryPresenter extends MyPresenterWidget<WrapperView> {

    private final DocRefListPresenter docRefListPresenter;
    private final Provider<DocSelectionPopup> dictionarySelection;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private List<DocRef> dictionaries = new ArrayList<>();

    @Inject
    public IncludeExcludeFilterDictionaryPresenter(final EventBus eventBus,
                                                   final WrapperView view,
                                                   final DocRefListPresenter docRefListPresenter,
                                                   final Provider<DocSelectionPopup> dictionarySelection) {
        super(eventBus, view);
        this.docRefListPresenter = docRefListPresenter;
        this.dictionarySelection = dictionarySelection;

        view.setView(docRefListPresenter.getView());

        addButton = docRefListPresenter.getView().addButton(SvgPresets.ADD);
        addButton.setTitle("Add Dictionary");

        removeButton = docRefListPresenter.getView().addButton(SvgPresets.DELETE);
        removeButton.setTitle("Remove Dictionary");

        docRefListPresenter.getView().getRefreshButton().setVisible(false);
        docRefListPresenter.initTableColumns("Dictionary Name", false);

        addButton.setEnabled(true);
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(this::onAdd));
        registerHandler(removeButton.addClickHandler(this::onRemove));
        registerHandler(docRefListPresenter.getSelectionModel().addSelectionHandler(event -> enableButtons()));
    }

    private void onAdd(final ClickEvent event) {
        final DocSelectionPopup chooser = dictionarySelection.get();
        chooser.setCaption("Choose Dictionary");
        chooser.setIncludedTypes(DictionaryDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.show(docRef -> {
            if (docRef != null && !dictionaries.contains(docRef)) {
                dictionaries.add(docRef);
                refresh();
            }
        });
    }

    public void onRemove(final ClickEvent event) {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
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

    private void enableButtons() {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        removeButton.setEnabled(NullSafe.hasItems(selectionModel.getSelectedItems()));
    }

    public void setDictionaries(final List<DocRef> dictionaries) {
        this.dictionaries = dictionaries == null ? new ArrayList<>() : new ArrayList<>(dictionaries);
        refresh();
    }

    public List<DocRef> getDictionaries() {
        return dictionaries;
    }

    private void refresh() {
        docRefListPresenter.setData(dictionaries);
    }
}
