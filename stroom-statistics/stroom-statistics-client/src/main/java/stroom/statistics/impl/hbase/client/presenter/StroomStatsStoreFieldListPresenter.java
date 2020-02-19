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

package stroom.statistics.impl.hbase.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.statistics.impl.hbase.shared.StatisticField;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StroomStatsStoreFieldListPresenter extends MyPresenterWidget<DataGridView<StatisticField>>
        implements HasDocumentRead<StroomStatsStoreDoc>, HasWrite<StroomStatsStoreDoc>, HasDirtyHandlers, ReadOnlyChangeHandler {
    private final StroomStatsStoreFieldEditPresenter stroomStatsStoreFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private StroomStatsStoreEntityData stroomStatsStoreEntityData;

    private StroomStatsStoreCustomMaskListPresenter customMaskListPresenter;

    private boolean readOnly = true;

    @SuppressWarnings("unchecked")
    @Inject
    public StroomStatsStoreFieldListPresenter(final EventBus eventBus,
                                              final StroomStatsStoreFieldEditPresenter stroomStatsStoreFieldEditPresenter) {
        super(eventBus, new DataGridViewImpl<>(true, true));
        this.stroomStatsStoreFieldEditPresenter = stroomStatsStoreFieldEditPresenter;

        newButton = getView().addButton(SvgPresets.NEW_ITEM);
        editButton = getView().addButton(SvgPresets.EDIT);
        removeButton = getView().addButton(SvgPresets.REMOVE);

        addColumns();

        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onAdd();
            }
        }));

        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onEdit();
            }
        }));

        registerHandler(removeButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onRemove();
            }
        }));

        registerHandler(getView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (stroomStatsStoreEntityData != null && stroomStatsStoreEntityData.getStatisticFields() != null) {
            StatisticField selected = getView().getSelectionModel().getSelected();
            final boolean enabled = !readOnly && selected != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New field disabled as fields are read only");
            editButton.setTitle("Edit field disabled as fields are read only");
            removeButton.setTitle("Remove field disabled as fields are read only");
        } else {
            newButton.setTitle("New Field");
            editButton.setTitle("Edit Field");
            removeButton.setTitle("Remove Field");
        }
    }

    private void addColumns() {
        addNameColumn();
        getView().addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        getView().addResizableColumn(new Column<StatisticField, String>(new TextCell()) {
            @Override
            public String getValue(final StatisticField row) {
                return row.getFieldName();
            }
        }, "Name", 150);
    }

    private void onAdd() {
        if (!readOnly) {
            final StatisticField statisticField = new StatisticField();
            final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();
            final List<StatisticField> otherFields = stroomStatsStoreEntityData.getStatisticFields();

            stroomStatsStoreFieldEditPresenter.read(statisticField, otherFields);
            stroomStatsStoreFieldEditPresenter.show("New Field", new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        if (stroomStatsStoreFieldEditPresenter.write(statisticField)) {
                            stroomStatsStoreEntityData.addStatisticField(statisticField);
                            reComputeRollUpBitMask(oldStroomStatsStoreEntityData, stroomStatsStoreEntityData);
                            refresh();
                            stroomStatsStoreFieldEditPresenter.hide();
                            DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
                        }
                    } else {
                        stroomStatsStoreFieldEditPresenter.hide();
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Ignore.
                }
            });
        }
    }

    private void onEdit() {
        if (!readOnly) {
            final StatisticField statisticField = getView().getSelectionModel().getSelected();
            if (statisticField != null) {
                final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();

                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final List<StatisticField> otherFields = new ArrayList<>(
                        stroomStatsStoreEntityData.getStatisticFields());
                otherFields.remove(statisticField);

                stroomStatsStoreFieldEditPresenter.read(statisticField, otherFields);
                stroomStatsStoreFieldEditPresenter.show("Edit Field", new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            if (stroomStatsStoreFieldEditPresenter.write(statisticField)) {
                                stroomStatsStoreEntityData.reOrderStatisticFields();
                                reComputeRollUpBitMask(oldStroomStatsStoreEntityData, stroomStatsStoreEntityData);
                                refresh();
                                stroomStatsStoreFieldEditPresenter.hide();
                                DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
                            }
                        } else {
                            stroomStatsStoreFieldEditPresenter.hide();
                        }
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                        // Ignore.
                    }
                });
            }
        }
    }

    private void onRemove() {
        if (!readOnly) {
            final List<StatisticField> list = getView().getSelectionModel().getSelectedItems();
            if (list != null && list.size() > 0) {
                final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();

                stroomStatsStoreEntityData.getStatisticFields().removeAll(list);
                getView().getSelectionModel().clear();
                reComputeRollUpBitMask(oldStroomStatsStoreEntityData, stroomStatsStoreEntityData);
                refresh();
                DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
            }
        }
    }

    private void reComputeRollUpBitMask(final StroomStatsStoreEntityData oldStroomStatsStoreEntityData,
                                        final StroomStatsStoreEntityData newStroomStatsStoreEntityData) {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.reComputeRollUpBitMask(oldStroomStatsStoreEntityData, newStroomStatsStoreEntityData);
        }
    }

    public void refresh() {
        if (stroomStatsStoreEntityData == null) {
            stroomStatsStoreEntityData = new StroomStatsStoreEntityData();
        }

        getView().setRowData(0, new ArrayList<>(stroomStatsStoreEntityData.getStatisticFields()));
        getView().setRowCount(stroomStatsStoreEntityData.getStatisticFields().size(), true);
    }

    @Override
    public void read(final DocRef docRef, final StroomStatsStoreDoc stroomStatsStoreEntity) {
        if (stroomStatsStoreEntity != null) {
            stroomStatsStoreEntityData = stroomStatsStoreEntity.getConfig();

            if (customMaskListPresenter != null) {
                customMaskListPresenter.read(docRef, stroomStatsStoreEntity);
            }
            refresh();
        }
    }

    @Override
    public void write(final StroomStatsStoreDoc entity) {
        entity.setConfig(stroomStatsStoreEntityData);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setCustomMaskListPresenter(final StroomStatsStoreCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
