/*
 * Copyright 2024 Crown Copyright
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

package stroom.dictionary.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.WrapperView;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasDocumentWrite;
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
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DictionaryListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<DictionaryDoc>, HasDocumentWrite<DictionaryDoc>, HasDirtyHandlers {

    private final DocRefListPresenter docRefListPresenter;
    private final Provider<DocSelectionPopup> dictionarySelection;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private List<DocRef> imports;
    private DocRef currentDoc;
    private boolean readOnly = true;

    @Inject
    public DictionaryListPresenter(final EventBus eventBus,
                                   final WrapperView view,
                                   final DocRefListPresenter docRefListPresenter,
                                   final Provider<DocSelectionPopup> dictionarySelection) {
        super(eventBus, view);
        this.docRefListPresenter = docRefListPresenter;
        this.dictionarySelection = dictionarySelection;

        view.setView(docRefListPresenter.getView());

        addButton = docRefListPresenter.getView().addButton(SvgPresets.ADD);
        removeButton = docRefListPresenter.getView().addButton(SvgPresets.DELETE);
        docRefListPresenter.initTableColumns("Document Name", true);

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
        chooser.setCaption("Import a dictionary");
        chooser.setIncludedTypes(DictionaryDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.show(docRef -> {
            if (docRef != null && !docRef.equals(currentDoc) && !imports.contains(docRef)) {
                imports.add(docRef);
                DirtyEvent.fire(DictionaryListPresenter.this, true);
                refresh();
            }
        });
    }

    public void onRemove(final ClickEvent event) {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        final List<DocRef> selected = selectionModel.getSelectedItems();
        if (NullSafe.hasItems(selected)) {
            String message = "Are you sure you want to remove this imported dictionary?";
            if (selected.size() > 1) {
                message = "Are you sure you want to remove these imported dictionaries?";
            }

            ConfirmEvent.fire(this,
                    message,
                    result -> {
                        if (result) {
                            imports.removeAll(selected);
                            selectionModel.clear();
                            DirtyEvent.fire(DictionaryListPresenter.this, true);
                            refresh();
                        }
                    });
        }
    }

    @Override
    public void read(final DocRef docRef, final DictionaryDoc document, final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();

        currentDoc = docRef;
        imports = new ArrayList<>();
        if (document != null) {
            if (document.getImports() != null) {
                imports.addAll(document.getImports());
            }
        }
        refresh();
    }

    @Override
    public DictionaryDoc write(final DictionaryDoc document) {
        if (imports.isEmpty()) {
            document.setImports(null);
        } else {
            document.setImports(imports);
            // Select first item
            docRefListPresenter.getSelectionModel().setSelected(imports.get(0));
        }
        return document;
    }

    public void registerDictionarySelectionHandler(final Consumer<DocRef> docRefConsumer) {
        if (docRefConsumer != null) {
            final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
            registerHandler(selectionModel.addSelectionHandler(event -> {
                if (!event.getSelectionType().isDoubleSelect()
                        && !event.getSelectionType().isMultiSelect()) {
                    if (selectionModel.getSelectedCount() == 1) {
                        docRefConsumer.accept(selectionModel.getSelected());
                    }
                }
            }));
        }
    }

    private void enableButtons() {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && NullSafe.hasItems(selectionModel.getSelectedItems()));

        if (readOnly) {
            addButton.setTitle("Add import disabled as this dictionary is read only");
            removeButton.setTitle("Remove import disabled as this dictionary is read only");
        } else {
            addButton.setTitle("Add Import");
            removeButton.setTitle("Remove Import");
        }
    }

    private void refresh() {
        docRefListPresenter.setData(imports);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
