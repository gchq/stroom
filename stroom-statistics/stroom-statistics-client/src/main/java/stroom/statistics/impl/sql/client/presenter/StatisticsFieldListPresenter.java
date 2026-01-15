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

package stroom.statistics.impl.sql.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
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

public class StatisticsFieldListPresenter extends DocumentEditPresenter<PagerView, StatisticStoreDoc> {

    private final MyDataGrid<StatisticField> dataGrid;
    private final MultiSelectionModelImpl<StatisticField> selectionModel;

    private final StatisticsFieldEditPresenter statisticsFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private StatisticsDataSourceData statisticsDataSourceData;

    private StatisticsCustomMaskListPresenter customMaskListPresenter;

    @Inject
    public StatisticsFieldListPresenter(final EventBus eventBus,
                                        final PagerView view,
                                        final StatisticsFieldEditPresenter statisticsFieldEditPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.statisticsFieldEditPresenter = statisticsFieldEditPresenter;

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
        if (statisticsDataSourceData != null && statisticsDataSourceData.getFields() != null) {
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
            final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();
            final List<StatisticField> otherFields = statisticsDataSourceData.getFields();

            statisticsFieldEditPresenter.read(statisticField, otherFields);
            statisticsFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    if (statisticsFieldEditPresenter.write(statisticField)) {
                        statisticsDataSourceData.addStatisticField(statisticField);
                        reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                        refresh();
                        e.hide();
                        DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
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
                final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final List<StatisticField> otherFields = new ArrayList<>(
                        statisticsDataSourceData.getFields());
                otherFields.remove(statisticField);

                statisticsFieldEditPresenter.read(statisticField, otherFields);
                statisticsFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        if (statisticsFieldEditPresenter.write(statisticField)) {
                            statisticsDataSourceData.reOrderStatisticFields();
                            reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                            refresh();
                            e.hide();
                            DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
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
                final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

                statisticsDataSourceData.getFields().removeAll(list);
                selectionModel.clear();
                reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                refresh();
                DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
            }
        }
    }

    private void reComputeRollUpBitMask(final StatisticsDataSourceData oldStatisticsDataSourceData,
                                        final StatisticsDataSourceData newStatisticsDataSourceData) {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.reComputeRollUpBitMask(oldStatisticsDataSourceData, newStatisticsDataSourceData);
        }
    }

    public void refresh() {
        if (statisticsDataSourceData == null) {
            statisticsDataSourceData = new StatisticsDataSourceData(new ArrayList<>());
        }

        dataGrid.setRowData(0, new ArrayList<>(statisticsDataSourceData.getFields()));
        dataGrid.setRowCount(statisticsDataSourceData.getFields().size(), true);
    }

    @Override
    protected void onRead(final DocRef docRef, final StatisticStoreDoc document, final boolean readOnly) {
        enableButtons();

        if (document != null) {
            statisticsDataSourceData = document.getConfig();

            if (customMaskListPresenter != null) {
                customMaskListPresenter.read(docRef, document, readOnly);
            }
            refresh();
        }
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        document.setConfig(statisticsDataSourceData);
        return document;
    }

    public void setCustomMaskListPresenter(final StatisticsCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
