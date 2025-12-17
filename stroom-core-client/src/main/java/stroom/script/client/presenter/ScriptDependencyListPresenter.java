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

package stroom.script.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.WrapperView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasDocumentWrite;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.script.shared.ScriptDoc;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class ScriptDependencyListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<ScriptDoc>, HasDocumentWrite<ScriptDoc>, HasDirtyHandlers {

    private final ScriptListPresenter scriptListPresenter;
    private final DocSelectionPopup explorerDropDownTreePresenter;
    private final ButtonView addButton;
    private final ButtonView removeButton;
    private List<DocRef> scripts;

    private boolean readOnly = true;

    @Inject
    public ScriptDependencyListPresenter(final EventBus eventBus, final WrapperView view,
                                         final ScriptListPresenter scriptListPresenter,
                                         final DocSelectionPopup explorerDropDownTreePresenter) {
        super(eventBus, view);
        this.scriptListPresenter = scriptListPresenter;
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;

        explorerDropDownTreePresenter.setIncludedTypes(ScriptDoc.TYPE);
        explorerDropDownTreePresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setView(scriptListPresenter.getView());

        addButton = scriptListPresenter.getView().addButton(SvgPresets.ADD);
        removeButton = scriptListPresenter.getView().addButton(SvgPresets.REMOVE);

        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(this::onAdd));
        registerHandler(removeButton.addClickHandler(this::onRemove));
        registerHandler(scriptListPresenter.getSelectionModel().addSelectionHandler(event -> enableButtons()));
    }

    private void onAdd(final ClickEvent event) {
        explorerDropDownTreePresenter.show(script -> {
            if (script != null) {
                if (!scripts.contains(script) && scripts.add(script)) {
                    DirtyEvent.fire(ScriptDependencyListPresenter.this, true);
                    refresh();
                }
            }
        });
    }

    private void onRemove(final ClickEvent event) {
        final List<DocRef> list = scriptListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to remove this script dependency?";
            if (list.size() > 1) {
                message = "Are you sure you want to remove these script dependencies?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    for (final DocRef script : list) {
                        scripts.remove(script);
                    }

                    scriptListPresenter.getSelectionModel().clear();
                    DirtyEvent.fire(ScriptDependencyListPresenter.this, true);
                    refresh();
                }
            });
        }
    }

    @Override
    public void read(final DocRef docRef, final ScriptDoc document, final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();

        scripts = new ArrayList<>();
        if (document != null) {
            if (document.getDependencies() != null) {
                scripts = document.getDependencies();
            }
        }
        refresh();
    }

    @Override
    public ScriptDoc write(final ScriptDoc document) {
        document.setDependencies(scripts);
        return document;
    }

    private void refresh() {
        if (scripts != null) {
            final List<DocRef> list = new ArrayList<>(scripts);
            list.sort(DocRef::compareTo);
            scriptListPresenter.setData(list);
        } else {
            scriptListPresenter.setData(new ArrayList<>());
        }
    }

    private void enableButtons() {
        final DocRef selected = scriptListPresenter.getSelectionModel().getSelected();
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selected != null);

        if (readOnly) {
            addButton.setTitle("Add dependency disabled as script is read only");
            removeButton.setTitle("Remove dependency disabled as script is read only");
        } else {
            addButton.setTitle("Add Dependency");
            removeButton.setTitle("Remove Dependency");
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
