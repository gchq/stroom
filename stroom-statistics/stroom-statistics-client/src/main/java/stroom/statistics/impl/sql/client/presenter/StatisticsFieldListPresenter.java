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
import stroom.statistics.impl.sql.client.presenter.State.Field;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StatisticsFieldListPresenter extends DocPresenter<PagerView, StatisticStoreDoc> {

    private final MyDataGrid<Field> dataGrid;
    private final MultiSelectionModelImpl<Field> selectionModel;

    private final StatisticsFieldEditPresenter statisticsFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private State state = new State();

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
        final Field selected = selectionModel.getSelected();
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
        dataGrid.addResizableColumn(new Column<Field, String>(new TextCell()) {
            @Override
            public String getValue(final Field row) {
                return row.getName();
            }
        }, "Name", 150);
    }

    private void onAdd() {
        if (!isReadOnly()) {
            final Field statisticField = state.createField("");
            final Set<String> otherFieldNames =
                    state.getFields().stream().map(Field::getName).collect(Collectors.toSet());
            statisticsFieldEditPresenter.read(statisticField, otherFieldNames);
            statisticsFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    try {
                        statisticsFieldEditPresenter.write(statisticField);
                        state.addField(statisticField);
                        state.sortFields();
                        refresh();
                        updateMasks();
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
            final Field statisticField = selectionModel.getSelected();
            if (statisticField != null) {
                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final Set<String> otherFieldNames =
                        state.getFields().stream().map(Field::getName).collect(Collectors.toSet());
                otherFieldNames.remove(statisticField.getName());

                statisticsFieldEditPresenter.read(statisticField, otherFieldNames);
                statisticsFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        try {
                            statisticsFieldEditPresenter.write(statisticField);
                            state.sortFields();
                            refresh();
                            updateMasks();
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
            final List<Field> list = selectionModel.getSelectedItems();
            if (!NullSafe.isEmptyCollection(list)) {
                state.removeFields(list);
                selectionModel.clear();
                refresh();
                updateMasks();
                DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
            }
        }
    }

    public void refresh() {
        dataGrid.setRowData(0, state.getFields());
        dataGrid.setRowCount(state.getFields().size(), true);
    }

    private void updateMasks() {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.update();
        }
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final StatisticStoreDoc document,
                          final boolean readOnly) {
        enableButtons();
        refresh();
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        return document;
    }

    public void setCustomMaskListPresenter(final StatisticsCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
        this.customMaskListPresenter.setState(state);
    }

    void setState(final State state) {
        this.state = state;
    }
}
