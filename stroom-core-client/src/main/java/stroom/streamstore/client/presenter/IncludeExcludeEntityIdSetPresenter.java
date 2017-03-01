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

package stroom.streamstore.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.DisabledResources;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefs;
import stroom.query.api.DocRef;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityReferenceComparator;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedMap;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IncludeExcludeEntityIdSetPresenter<T extends BaseEntity>
        extends MyPresenterWidget<IncludeExcludeEntityIdSetPresenter.IncludeExcludeEntityIdSetView>
        implements IncludeExcludeEntityIdSetUiHandlers {
    private final IncludeExcludeEntityIdSetPopupPresenter popupPresenter;
    private final ClientDispatchAsync dispatcher;
    private CellTableView<String> list;
    private List<String> data;
    private boolean enabled = true;
    private String type;
    private boolean groupedEntity;
    private IncludeExcludeEntityIdSet<T> includeExcludeEntityIdSet;
    @Inject
    public IncludeExcludeEntityIdSetPresenter(final EventBus eventBus, final IncludeExcludeEntityIdSetView view,
                                              final IncludeExcludeEntityIdSetPopupPresenter popupPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.popupPresenter = popupPresenter;
        this.dispatcher = dispatcher;

        view.setUiHandlers(this);

        createList();
    }

    private void createList() {
        list = new CellTableViewImpl<String>(false, (Resources) GWT.create(DisabledResources.class));

        // Text.
        final Column<String, String> textColumn = new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String text) {
                return text;
            }
        };

        list.addColumn(textColumn);
        getView().setListView(list);
    }

    public void read(final String type, final boolean groupedEntity,
                     final IncludeExcludeEntityIdSet<T> includeExcludeEntityIdSet) {
        this.type = type;
        this.groupedEntity = groupedEntity;
        this.includeExcludeEntityIdSet = new IncludeExcludeEntityIdSet<T>();
        this.includeExcludeEntityIdSet.copyFrom(includeExcludeEntityIdSet);

        updateList();
    }

    public void write(final IncludeExcludeEntityIdSet<T> includeExcludeEntityIdSet) {
        includeExcludeEntityIdSet.copyFrom(this.includeExcludeEntityIdSet);
    }

    private void updateList() {
        if (includeExcludeEntityIdSet != null && includeExcludeEntityIdSet.isConstrained()) {
            // Load the entities.
            final SetId includeSetId = new SetId("include", type);
            final SetId excludeSetId = new SetId("exclude", type);
            final SharedMap<SetId, EntityIdSet<?>> loadMap = new SharedMap<SetId, EntityIdSet<?>>();

            if (includeExcludeEntityIdSet.getInclude() != null) {
                loadMap.put(includeSetId, includeExcludeEntityIdSet.getInclude());
            }
            if (includeExcludeEntityIdSet.getExclude() != null) {
                loadMap.put(excludeSetId, includeExcludeEntityIdSet.getExclude());
            }

            final LoadEntityIdSetAction action = new LoadEntityIdSetAction(loadMap);
            dispatcher.execute(action, new AsyncCallbackAdaptor<SharedMap<SetId, DocRefs>>() {
                @Override
                public void onSuccess(final SharedMap<SetId, DocRefs> result) {
                    final DocRefs included = result.get(includeSetId);
                    final DocRefs excluded = result.get(excludeSetId);

                    data = new ArrayList<String>();
                    if (included != null && included.getDoc().size() > 0) {
                        final List<DocRef> refs = new ArrayList<>(included.getDoc());
                        Collections.sort(refs, new EntityReferenceComparator());
                        for (final DocRef entity : refs) {
                            data.add("+ " + entity.getName());
                        }
                    }

                    if (excluded != null && excluded.getDoc().size() > 0) {
                        final List<DocRef> refs = new ArrayList<>(excluded.getDoc());
                        Collections.sort(refs, new EntityReferenceComparator());
                        for (final DocRef entity : refs) {
                            data.add("- " + entity.getName());
                        }
                    }

                    refresh();
                }
            });
        } else {
            this.data = new ArrayList<String>();
            refresh();
        }
    }

    private void refresh() {
        if (data != null) {
            list.setRowData(0, data);
            list.setRowCount(data.size());
        }
    }

    @Override
    public void onEdit() {
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    popupPresenter.write(includeExcludeEntityIdSet);
                    updateList();
                }

                HidePopupEvent.fire(IncludeExcludeEntityIdSetPresenter.this, popupPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        popupPresenter.read(type, groupedEntity, includeExcludeEntityIdSet);

        final PopupSize popupSize = new PopupSize(400, 500, 300, 300, true);
        ShowPopupEvent.fire(this, popupPresenter, PopupType.OK_CANCEL_DIALOG, popupSize,
                "Choose Feeds To Include And Exclude", popupUiHandlers);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;

        createList();
        refresh();

        if (enabled) {
            getView().asWidget().getElement().getStyle().setBackgroundColor("white");
            getView().setEditEnabled(true);
        } else {
            getView().asWidget().getElement().getStyle().setBackgroundColor("#EEE");
            getView().setEditEnabled(false);
        }
    }

    public interface IncludeExcludeEntityIdSetView extends View, HasUiHandlers<IncludeExcludeEntityIdSetUiHandlers> {
        void setListView(View view);

        void setEditEnabled(boolean enabled);
    }
}
