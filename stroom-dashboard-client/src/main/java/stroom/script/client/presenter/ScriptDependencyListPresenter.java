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

package stroom.script.client.presenter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.explorer.shared.ExplorerNode;
import stroom.node.client.view.WrapperView;
import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;

import java.util.ArrayList;
import java.util.List;

public class ScriptDependencyListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<ScriptDoc>, HasWrite<ScriptDoc>, HasDirtyHandlers {
    private final ScriptListPresenter scriptListPresenter;
    private final EntityChooser explorerDropDownTreePresenter;
    private final ButtonView addButton;
    private final ButtonView removeButton;
    private List<DocRef> scripts;

    @Inject
    public ScriptDependencyListPresenter(final EventBus eventBus, final WrapperView view,
                                         final ScriptListPresenter scriptListPresenter,
                                         final EntityChooser explorerDropDownTreePresenter) {
        super(eventBus, view);
        this.scriptListPresenter = scriptListPresenter;
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;

        explorerDropDownTreePresenter.setIncludedTypes(ScriptDoc.DOCUMENT_TYPE);
        explorerDropDownTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setView(scriptListPresenter.getView());

        addButton = scriptListPresenter.getView().addButton(SvgPresets.ADD);
        addButton.setTitle("Add Dependency");
        removeButton = scriptListPresenter.getView().addButton(SvgPresets.REMOVE);
        removeButton.setTitle("Remove Dependency");
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(this::onAdd));
        registerHandler(removeButton.addClickHandler(this::onRemove));
        registerHandler(scriptListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final DocRef selected = scriptListPresenter.getSelectionModel().getSelected();
            removeButton.setEnabled(selected != null);
        }));
        registerHandler(explorerDropDownTreePresenter.addDataSelectionHandler(event -> {
            final ExplorerNode selectedItem = event.getSelectedItem();
            if (selectedItem != null) {
                final DocRef script = selectedItem.getDocRef();
                if (script != null) {
                    if (!scripts.contains(script) && scripts.add(script)) {
                        DirtyEvent.fire(ScriptDependencyListPresenter.this, true);
                        refresh();
                    }
                }
            }
        }));
    }

    private void onAdd(final ClickEvent event) {
        explorerDropDownTreePresenter.show();
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
    public void read(final DocRef docRef, final ScriptDoc script) {
        scripts = new ArrayList<>();
        if (script != null) {
            if (script.getDependencies() != null) {
                scripts = script.getDependencies();
            }
        }
        refresh();
    }

    @Override
    public void write(final ScriptDoc script) {
        script.setDependencies(scripts);
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

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
