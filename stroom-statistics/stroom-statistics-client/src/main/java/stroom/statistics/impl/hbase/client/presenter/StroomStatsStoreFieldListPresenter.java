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

package stroom.statistics.impl.hbase.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.statistics.impl.hbase.shared.StatisticField;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

public class StroomStatsStoreFieldListPresenter extends DocumentEditPresenter<PagerView, StroomStatsStoreDoc> {

    private final MyDataGrid<StatisticField> dataGrid;
    private final MultiSelectionModelImpl<StatisticField> selectionModel;

    private final StroomStatsStoreFieldEditPresenter stroomStatsStoreFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private StroomStatsStoreEntityData stroomStatsStoreEntityData;

    private StroomStatsStoreCustomMaskListPresenter customMaskListPresenter;

    @Inject
    public StroomStatsStoreFieldListPresenter(
            final EventBus eventBus,
            final PagerView view,
            final StroomStatsStoreFieldEditPresenter stroomStatsStoreFieldEditPresenter) {

        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.stroomStatsStoreFieldEditPresenter = stroomStatsStoreFieldEditPresenter;

        newButton = view.addButton(SvgPresets.NEW_ITEM);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.REMOVE);

        addColumns();

        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAdd();
            }
        }));

        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onEdit();
            }
        }));

        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onRemove();
            }
        }));

        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void enableButtons() {
        newButton.setEnabled(!isReadOnly());
        if (stroomStatsStoreEntityData != null && stroomStatsStoreEntityData.getFields() != null) {
            final StatisticField selected = selectionModel.getSelected();
            final boolean enabled = !isReadOnly() && selected != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (isReadOnly()) {
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
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new Column<StatisticField, String>(new TextCell()) {
            @Override
            public String getValue(final StatisticField row) {
                return row.getFieldName();
            }
        }, "Name", 150);
    }

    private void onAdd() {
        if (!isReadOnly()) {
            final StatisticField statisticField = new StatisticField();
            final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();
            final List<StatisticField> otherFields = stroomStatsStoreEntityData.getFields();

            stroomStatsStoreFieldEditPresenter.read(statisticField, otherFields);
            stroomStatsStoreFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    if (stroomStatsStoreFieldEditPresenter.write(statisticField)) {
                        stroomStatsStoreEntityData.addStatisticField(statisticField);
                        reComputeRollUpBitMask(oldStroomStatsStoreEntityData, stroomStatsStoreEntityData);
                        refresh();
                        e.hide();
                        DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
                    } else {
                        e.reset();
                    }
                } else {
                    e.hide();
                }
            });
        }
    }

    private void onEdit() {
        if (!isReadOnly()) {
            final StatisticField statisticField = selectionModel.getSelected();
            if (statisticField != null) {
                final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();

                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final List<StatisticField> otherFields = new ArrayList<>(
                        stroomStatsStoreEntityData.getFields());
                otherFields.remove(statisticField);

                stroomStatsStoreFieldEditPresenter.read(statisticField, otherFields);
                stroomStatsStoreFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        if (stroomStatsStoreFieldEditPresenter.write(statisticField)) {
                            stroomStatsStoreEntityData.reOrderStatisticFields();
                            reComputeRollUpBitMask(oldStroomStatsStoreEntityData,
                                    stroomStatsStoreEntityData);
                            refresh();
                            e.hide();
                            DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                });
            }
        }
    }

    private void onRemove() {
        if (!isReadOnly()) {
            final List<StatisticField> list = selectionModel.getSelectedItems();
            if (list != null && list.size() > 0) {
                final StroomStatsStoreEntityData oldStroomStatsStoreEntityData = stroomStatsStoreEntityData.deepCopy();

                stroomStatsStoreEntityData.getFields().removeAll(list);
                selectionModel.clear();
                reComputeRollUpBitMask(oldStroomStatsStoreEntityData, stroomStatsStoreEntityData);
                refresh();
                DirtyEvent.fire(StroomStatsStoreFieldListPresenter.this, true);
            }
        }
    }

    private void reComputeRollUpBitMask(final StroomStatsStoreEntityData oldStroomStatsStoreEntityData,
                                        final StroomStatsStoreEntityData newStroomStatsStoreEntityData) {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.reComputeRollUpBitMask(oldStroomStatsStoreEntityData,
                    newStroomStatsStoreEntityData);
        }
    }

    public void refresh() {
        if (stroomStatsStoreEntityData == null) {
            stroomStatsStoreEntityData = new StroomStatsStoreEntityData();
        }

        dataGrid.setRowData(0, new ArrayList<>(stroomStatsStoreEntityData.getFields()));
        dataGrid.setRowCount(stroomStatsStoreEntityData.getFields().size(), true);
    }

    @Override
    protected void onRead(final DocRef docRef, final StroomStatsStoreDoc document, final boolean readOnly) {
        enableButtons();

        if (document != null) {
            stroomStatsStoreEntityData = document.getConfig();

            if (customMaskListPresenter != null) {
                customMaskListPresenter.read(docRef, document, readOnly);
            }
            refresh();
        }
    }

    @Override
    protected StroomStatsStoreDoc onWrite(final StroomStatsStoreDoc document) {
        document.setConfig(stroomStatsStoreEntityData);
        return document;
    }

    public void setCustomMaskListPresenter(final StroomStatsStoreCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
