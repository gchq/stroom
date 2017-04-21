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
import stroom.data.table.client.CellTableViewImpl.DefaultResources;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class StreamAttributeListPresenter
        extends MyPresenterWidget<StreamAttributeListPresenter.StreamAttributeListView>
        implements StreamAttributeListUiHandlers {
    public interface StreamAttributeListView extends View, HasUiHandlers<StreamAttributeListUiHandlers> {
        void setListView(View view);

        void setAddEnabled(boolean enabled);

        void setRemoveEnabled(boolean enabled);
    }

    private final StreamAttributePresenter streamAttributePresenter;
    private final MySingleSelectionModel<StreamAttributeCondition> selectionModel;
    private final CellTableView<StreamAttributeCondition> list;
    private List<StreamAttributeCondition> data;

    @Inject
    public StreamAttributeListPresenter(final EventBus eventBus, final StreamAttributeListView view,
            final StreamAttributePresenter streamAttributePresenter) {
        super(eventBus, view);
        this.streamAttributePresenter = streamAttributePresenter;

        selectionModel = new MySingleSelectionModel<StreamAttributeCondition>();

        list = new CellTableViewImpl<StreamAttributeCondition>(true, (Resources) GWT.create(DefaultResources.class));
        // Text.
        final Column<StreamAttributeCondition, String> textColumn = new Column<StreamAttributeCondition, String>(
                new TextCell()) {
            @Override
            public String getValue(final StreamAttributeCondition condition) {
                if (condition == null) {
                    return null;
                }

                final StringBuilder sb = new StringBuilder();
                if (condition.getStreamAttributeKey() != null) {
                    sb.append(condition.getStreamAttributeKey().getName());
                    sb.append(" ");
                }
                if (condition.getCondition() != null) {
                    sb.append(condition.getCondition().getDisplayValue());
                    sb.append(" ");
                }
                if (condition.getFieldValue() != null) {
                    sb.append(condition.getFieldValue());
                }

                return sb.toString();
            }
        };
        list.addColumn(textColumn);
        list.setSelectionModel(selectionModel);

        view.setUiHandlers(this);
        view.setListView(list);
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(event -> enableButtons()));
    }

    public void read(final List<StreamAttributeCondition> data) {
        this.data = new ArrayList<StreamAttributeCondition>(data);
        refresh();
        enableButtons();
    }

    public void write(final List<StreamAttributeCondition> data) {
        data.clear();
        data.addAll(this.data);
    }

    private void refresh() {
        list.setRowData(0, data);
        list.setRowCount(data.size());
    }

    @Override
    public void onAdd() {
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final StreamAttributeCondition condition = new StreamAttributeCondition();
                    if (streamAttributePresenter.write(condition)) {
                        data.add(condition);
                        refresh();
                        HidePopupEvent.fire(StreamAttributeListPresenter.this, streamAttributePresenter);
                    }
                } else {
                    HidePopupEvent.fire(StreamAttributeListPresenter.this, streamAttributePresenter);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };
        streamAttributePresenter.read(new StreamAttributeCondition());
        ShowPopupEvent.fire(this, streamAttributePresenter, PopupType.OK_CANCEL_DIALOG, "Add Stream Attribute",
                popupUiHandlers);
    }

    @Override
    public void onRemove() {
        final StreamAttributeCondition selected = selectionModel.getSelectedObject();
        if (selected != null) {
            data.remove(selected);
            selectionModel.clear();
            refresh();
        }
    }

    private void enableButtons() {
        final StreamAttributeCondition selected = selectionModel.getSelectedObject();
        getView().setRemoveEnabled(selected != null);
    }
}
