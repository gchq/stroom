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
 */

package stroom.streamstore.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.DefaultResources;
import stroom.data.table.client.CellTableViewImpl.DisabledResources;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityReferenceComparator;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.query.api.v1.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.SharedMap;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class EntityIdSetPresenter extends MyPresenterWidget<EntityIdSetPresenter.EntityIdSetView>
        implements EntityIdSetUiHandlers {
    private final EntityChooser treePresenter;
    private final EntityChoicePresenter choicePresenter;
    private final ClientDispatchAsync dispatcher;
    private final StreamTypeUiManager streamTypeUiManager;
    private MySingleSelectionModel<DocRef> selectionModel;
    private CellTableView<DocRef> list;
    private boolean groupedEntity;
    private List<DocRef> data;
    private boolean enabled = true;

    @Inject
    public EntityIdSetPresenter(final EventBus eventBus, final EntityIdSetView view,
                                final EntityChooser treePresenter, final EntityChoicePresenter choicePresenter,
                                final StreamTypeUiManager streamTypeUiManager, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.treePresenter = treePresenter;
        this.choicePresenter = choicePresenter;
        this.dispatcher = dispatcher;
        this.streamTypeUiManager = streamTypeUiManager;

        view.setUiHandlers(this);

        createList(true);
    }

    private void createList(final boolean enabled) {
        if (enabled) {
            selectionModel = new MySingleSelectionModel<>();
            list = new CellTableViewImpl<>(true, GWT.create(DefaultResources.class));

            registerHandler(selectionModel.addSelectionChangeHandler(event -> enableButtons()));
        } else {
            selectionModel = null;
            list = new CellTableViewImpl<>(false, GWT.create(DisabledResources.class));
        }

        // Text.
        final Column<DocRef, String> textColumn = new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef entity) {
                return entity.getName();
            }
        };

        list.addColumn(textColumn);
        list.setSelectionModel(selectionModel);
        getView().setListView(list);
    }

    @Override
    protected void onBind() {
        registerHandler(treePresenter.addDataSelectionHandler(event -> {
            final DocRef docRef = treePresenter.getSelectedEntityReference();
            if (docRef != null) {
                data.add(docRef);
                refresh();
            }
        }));
        registerHandler(choicePresenter.addDataSelectionHandler(event -> {
            final DocRef docRef = event.getSelectedItem();
            if (docRef != null) {
                data.add(docRef);
                refresh();
            }
        }));
    }

    public <T extends BaseEntity> void read(final String type, final boolean groupedEntity,
                                            final EntityIdSet<T> entityIdSet) {
        this.groupedEntity = groupedEntity;

        if (entityIdSet != null && entityIdSet.getSet() != null) {
            // Load the entities.
            final SetId key = new SetId(type, type);
            final SharedMap<SetId, EntityIdSet<?>> loadMap = new SharedMap<>();
            loadMap.put(key, entityIdSet);
            final LoadEntityIdSetAction action = new LoadEntityIdSetAction(loadMap);
            dispatcher.exec(action).onSuccess(result -> {
                final DocRefs docRefs = result.get(key);
                if (docRefs != null) {
                    data = new ArrayList<>(docRefs.getDoc());
                    data.sort(new EntityReferenceComparator());
                } else {
                    data = new ArrayList<>();
                }
                refresh();
            });
        } else {
            this.data = new ArrayList<>();
            refresh();
        }

        if (!groupedEntity) {
            final List<DocRef> data = new ArrayList<>();
            for (final StreamType streamType : streamTypeUiManager.getRootStreamTypeList()) {
                data.add(DocRefUtil.create(streamType));
            }
            choicePresenter.setData(data);
        } else {
            treePresenter.setIncludedTypes(type);
            treePresenter.setRequiredPermissions(DocumentPermissionNames.USE);
            treePresenter.setAllowFolderSelection(false);
        }

        enableButtons();
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseEntity> void write(final EntityIdSet<T> entityIdSet) {
        entityIdSet.clear();
        for (final DocRef docRef : data) {
            if (docRef != null) {
                entityIdSet.add(docRef.getId());
            }
        }
    }

    private void refresh() {
        if (data != null) {
            list.setRowData(0, data);
            list.setRowCount(data.size());
        }
    }

    @Override
    public void onAdd() {
        if (!groupedEntity) {
            choicePresenter.show();
        } else {
            treePresenter.show();
        }
    }

    @Override
    public void onRemove() {
        final DocRef selected = getSelected();
        if (selected != null) {
            data.remove(selected);
            selectionModel.clear();
            refresh();
        }
    }

    private void enableButtons() {
        final DocRef selected = getSelected();
        getView().setRemoveEnabled(selected != null);
    }

    private DocRef getSelected() {
        if (selectionModel == null) {
            return null;
        }
        return selectionModel.getSelectedObject();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;

        createList(enabled);
        refresh();

        if (enabled) {
            getView().asWidget().getElement().getStyle().setBackgroundColor("white");
            enableButtons();
        } else {
            getView().asWidget().getElement().getStyle().setBackgroundColor("#EEE");
            getView().setAddEnabled(false);
            getView().setRemoveEnabled(false);
        }
    }

    public interface EntityIdSetView extends View, HasUiHandlers<EntityIdSetUiHandlers> {
        void setListView(View view);

        void setAddEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);
    }
}
