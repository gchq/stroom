/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefs;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.node.client.view.WrapperView;
import stroom.script.shared.Script;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScriptDependencyListPresenter extends MyPresenterWidget<WrapperView>
        implements HasRead<Script>, HasWrite<Script>, HasDirtyHandlers {
    private final ScriptListPresenter scriptListPresenter;
    private final EntityChooser explorerDropDownTreePresenter;
    private final GlyphButtonView addButton;
    private final GlyphButtonView removeButton;
    private final MySingleSelectionModel<DocRef> selectionModel;
    private DocRef selectedElement;
    private DocRefs scripts;

    @Inject
    public ScriptDependencyListPresenter(final EventBus eventBus, final WrapperView view,
                                         final ScriptListPresenter scriptListPresenter,
                                         final EntityChooser explorerDropDownTreePresenter) {
        super(eventBus, view);
        this.scriptListPresenter = scriptListPresenter;
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;

        explorerDropDownTreePresenter.setIncludedTypes(Script.ENTITY_TYPE);
        explorerDropDownTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setView(scriptListPresenter.getView());

        selectionModel = scriptListPresenter.getSelectionModel();

        addButton = scriptListPresenter.getView().addButton(GlyphIcons.ADD);
        addButton.setTitle("Add Dependency");
        removeButton = scriptListPresenter.getView().addButton(GlyphIcons.REMOVE);
        removeButton.setTitle("Remove Dependency");
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                onAdd(event);
            }
        }));
        registerHandler(removeButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                onRemove(event);
            }
        }));
        registerHandler(selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                selectedElement = selectionModel.getSelectedObject();
                removeButton.setEnabled(selectedElement != null);
            }
        }));
        registerHandler(explorerDropDownTreePresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                final EntityData selectedItem = (EntityData) event.getSelectedItem();
                if (selectedItem != null) {
                    final DocRef script = selectedItem.getDocRef();
                    if (script != null) {
                        if (scripts.add(script)) {
                            DirtyEvent.fire(ScriptDependencyListPresenter.this, true);
                            refresh();
                        }
                    }
                }
            }
        }));
    }

    private void onAdd(final ClickEvent event) {
        explorerDropDownTreePresenter.show();
    }

    public void onRemove(final ClickEvent event) {
        final DocRef script = selectionModel.getSelectedObject();
        if (script != null) {
            ConfirmEvent.fire(this, "Are you sure you want to remove this script dependency?", new ConfirmCallback() {
                @Override
                public void onResult(final boolean result) {
                    if (result) {
                        scripts.remove(script);
                        selectionModel.clear();
                        DirtyEvent.fire(ScriptDependencyListPresenter.this, true);
                        refresh();
                    }
                }
            });
        }
    }

    @Override
    public void read(final Script script) {
        scripts = new DocRefs();
        if (script != null) {
            if (script.getDependencies() != null) {
                scripts = script.getDependencies();
            }
        }
        refresh();
    }

    @Override
    public void write(final Script script) {
        script.setDependencies(scripts);
    }

    private void refresh() {
        if (scripts != null) {
            final List<DocRef> list = new ArrayList<DocRef>(scripts.getDoc());
            Collections.sort(list);
            scriptListPresenter.setData(list);
        } else {
            scriptListPresenter.setData(new ArrayList<DocRef>());
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
