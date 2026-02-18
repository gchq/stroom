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

import stroom.alert.client.event.AlertEvent;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatisticsFieldListPresenter extends DocPresenter<PagerView, StatisticStoreDoc> {

    private final MyDataGrid<StatisticField> dataGrid;
    private final MultiSelectionModelImpl<StatisticField> selectionModel;

    private final StatisticsFieldEditPresenter statisticsFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final List<StatisticField> fields = new ArrayList<>();
    private final Set<Set<StatisticField>> customRollUpMasks = new HashSet<>();

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
        final StatisticField selected = selectionModel.getSelected();
        final boolean enabled = !isReadOnly() && selected != null;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);

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
            final StatisticField statisticField = StatisticField.builder().build();
            final List<StatisticField> otherFields = new ArrayList<>(this.fields);

            statisticsFieldEditPresenter.read(statisticField, otherFields);
            statisticsFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    try {
                        final StatisticField updated = statisticsFieldEditPresenter.write(statisticField);
                        fields.add(updated);
                        fields.sort(StatisticField::compareTo);
                        refresh();
                        e.hide();
                        DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                    } catch (final RuntimeException ex) {
                        AlertEvent.fireError(this, ex.getMessage(), e::reset);
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
                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final List<StatisticField> otherFields = new ArrayList<>(this.fields);
                otherFields.remove(statisticField);

                statisticsFieldEditPresenter.read(statisticField, otherFields);
                statisticsFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        try {
                            final StatisticField updated = statisticsFieldEditPresenter.write(statisticField);
                            final int index = fields.indexOf(statisticField);
                            fields.set(index, updated);
                            fields.sort(StatisticField::compareTo);
                            customRollUpMasks.forEach(mask -> {
                                if (mask.contains(statisticField)) {
                                    mask.remove(statisticField);
                                    mask.add(updated);
                                }
                            });
                            refresh();
                            e.hide();
                            DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                        } catch (final RuntimeException ex) {
                            AlertEvent.fireError(this, ex.getMessage(), e::reset);
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
            if (!NullSafe.isEmptyCollection(list)) {
                fields.removeAll(list);
                selectionModel.clear();
                refresh();
                DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
            }
        }
    }

    public void refresh() {
        dataGrid.setRowData(0, fields);
        dataGrid.setRowCount(fields.size(), true);
        updateMasks();
    }

    private void updateMasks() {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.refresh(fields, customRollUpMasks);
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final StatisticStoreDoc document, final boolean readOnly) {
        fields.clear();
        customRollUpMasks.clear();
        enableButtons();

        if (document != null) {
            final StatisticsDataSourceData config = document.getConfig();
            if (config != null) {
                final List<StatisticField> fields = NullSafe.list(config.getFields());
                this.fields.addAll(fields);

                NullSafe.collection(config.getCustomRollUpMasks()).forEach(customRollUpMask -> {
                    final Set<StatisticField> rollup = new HashSet<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (customRollUpMask.isTagRolledUp(i)) {
                            final StatisticField statisticField = fields.get(i);
                            rollup.add(statisticField);
                        }
                    }
                    customRollUpMasks.add(rollup);
                });

                if (customMaskListPresenter != null) {
                    customMaskListPresenter.read(docRef, document, readOnly);
                }
            }
        }
        refresh();
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        final StatisticsDataSourceData config = NullSafe.getOrElse(
                        document,
                        StatisticStoreDoc::getConfig,
                        StatisticsDataSourceData::copy,
                        StatisticsDataSourceData.builder())
                .fields(fields).build();
        return document.copy().config(config).build();
    }

    public void setCustomMaskListPresenter(final StatisticsCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
