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
 *
 */

package stroom.dictionary.client.presenter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.node.client.view.WrapperView;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class DictionaryListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<DictionaryDoc>, HasWrite<DictionaryDoc>, HasDirtyHandlers {
    private final DocRefListPresenter docRefListPresenter;
    private final Provider<EntityChooser> dictionarySelection;
    private final ButtonView addButton;
    private final ButtonView removeButton;
    private List<DocRef> imports;
    private DocRef currentDoc;

    @Inject
    public DictionaryListPresenter(final EventBus eventBus, final WrapperView view,
                                   final DocRefListPresenter docRefListPresenter, final Provider<EntityChooser> dictionarySelection) {
        super(eventBus, view);
        this.docRefListPresenter = docRefListPresenter;
        this.dictionarySelection = dictionarySelection;

        view.setView(docRefListPresenter.getView());

        addButton = docRefListPresenter.getView().addButton(SvgPresets.ADD);
        addButton.setTitle("Add Import");
        removeButton = docRefListPresenter.getView().addButton(SvgPresets.DELETE);
        removeButton.setTitle("Remove Import");
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(event -> onAdd(event)));
        registerHandler(removeButton.addClickHandler(event -> onRemove(event)));
        registerHandler(docRefListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
            removeButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
        }));
    }

    private void onAdd(final ClickEvent event) {
        final EntityChooser chooser = dictionarySelection.get();
        chooser.setCaption("Import a dictionary");
        chooser.setIncludedTypes(DictionaryDoc.ENTITY_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.USE);
        chooser.addDataSelectionHandler(e -> {
            final DocRef docRef = chooser.getSelectedEntityReference();
            if (docRef != null && !docRef.equals(currentDoc) && !imports.contains(docRef)) {
                imports.add(docRef);
                DirtyEvent.fire(DictionaryListPresenter.this, true);
                refresh();
            }
        });

        chooser.show();
    }

    public void onRemove(final ClickEvent event) {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        final List<DocRef> selected = selectionModel.getSelectedItems();
        if (selected != null && selected.size() > 0) {
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
    public void read(final DocRef docRef, final DictionaryDoc dictionary) {
        currentDoc = docRef;
        imports = new ArrayList<>();
        if (dictionary != null) {
            if (dictionary.getImports() != null) {
                imports.addAll(dictionary.getImports());
            }
        }
        refresh();
    }

    @Override
    public void write(final DictionaryDoc dictionary) {
        if (imports.size() == 0) {
            dictionary.setImports(null);
        } else {
            dictionary.setImports(imports);
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
