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

package stroom.statistics.impl.sql.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.statistics.impl.sql.client.presenter.State.Field;
import stroom.statistics.impl.sql.client.presenter.State.Mask;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerView;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StatisticsCustomMaskListPresenter
        extends DocPresenter<PagerView, StatisticStoreDoc> {

    private final DataGrid<Mask> dataGrid;
    private final MultiSelectionModelImpl<Mask> selectionModel;

    private final ButtonView newButton;
    private final ButtonView removeButton;
    private final ButtonView autoGenerateButton;
    private final List<Column<Mask, ?>> columns = new ArrayList<>();
    private ListDataProvider<Mask> dataProvider;
    private State state = new State();

    @Inject
    public StatisticsCustomMaskListPresenter(final EventBus eventBus,
                                             final PagerView view) {
        super(eventBus, view);

        dataGrid = new DataGrid<>(this);
        dataGrid.setTableName("Statistics Custom Masks");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        view.getToolbar().setName("Roll-up actions");
        newButton = view.getToolbar().button(SvgImage.ADD, "New Roll-up Permutation", this::onAdd);
        autoGenerateButton = view.getToolbar().button(SvgImage.GENERATE, "Auto-generate Roll-up Permutations",
                this::onAutoGenerate);
        removeButton = view.getToolbar().button(SvgImage.REMOVE, "Remove Selected Roll-up Permutation",
                this::onRemove);
        removeButton.setEnabled(false);
    }

    @Override
    protected void onBind() {
        super.onBind();




        registerHandler(selectionModel.addSelectionHandler(event -> enableButtons()));
    }

    private void enableButtons() {
        newButton.setEnabled(!isReadOnly());
        autoGenerateButton.setEnabled(!isReadOnly());

        final Mask selectedElement = selectionModel.getSelected();
        removeButton.setEnabled(!isReadOnly() && selectedElement != null);

        final String reason = isReadOnly()
                ? " (read only)"
                : "";
        newButton.setTitle("New Roll-up Permutation" + reason);
        autoGenerateButton.setTitle("Auto-generate Roll-up Permutations" + reason);
        removeButton.setTitle("Remove Selected Roll-up Permutation" + reason);
    }

    private void addColumns() {
        for (final Field statisticField : state.getFields()) {
            addStatFieldColumn(statisticField);
        }
    }

    private void removeAllColumns() {
        for (final Column<Mask, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
    }

    private void addStatFieldColumn(final Field statisticField) {
        // Enabled.
        final Column<Mask, TickBoxState> rolledUpColumn = new Column<Mask, TickBoxState>(
                TickBoxCell.create(false, true)) {
            @Override
            public TickBoxState getValue(final Mask row) {
                return TickBoxState.fromBoolean(row.getMask().contains(statisticField));
            }
        };
        rolledUpColumn.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                row.getMask().add(statisticField);
            } else {
                row.getMask().remove(statisticField);
            }
            DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
        });

        dataGrid.addResizableColumn(rolledUpColumn, statisticField.getName(), 100);
        columns.add(rolledUpColumn);
    }

    private void onAdd() {
        this.state.addMask(new HashSet<>());
        refresh();
        DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
    }

    private void onAutoGenerate() {
        final StatisticsCustomMaskListPresenter thisInstance = this;
        ConfirmEvent.fire(this,
                "Are you sure you want to clear the existing roll-ups and generate all possible " +
                "permutations for the field list?",
                result -> {
                    if (result) {
                        state.allPermutations();
                        update();
                        DirtyEvent.fire(thisInstance, true);
                    }
                });
    }

    private void onRemove() {
        final List<Mask> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            state.removeMasks(list);
            // The grid always has the no roll-up row, so removing the last permutation puts it back
            state.addNoRollUpPerm();

            selectionModel.clear();
            refresh();

            DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new ListDataProvider<>();
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setCompleteList(state.getMasks());
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final StatisticStoreDoc document,
                          final boolean readOnly) {
        dataGrid.setTableName("Statistics '" + docRef.getName() + "' Custom Masks");
        update();
    }

    void setState(final State state) {
        this.state = state;
        if (dataProvider != null) {
            dataProvider.setCompleteList(state.getMasks());
        }
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        return document;
    }

    /**
     * Call this method to inform this that it needs to update its display based
     * on state that has changed on another tab
     */
    public void update() {
        state.addNoRollUpPerm();
        enableButtons();
        removeAllColumns();
        addColumns();
        refresh();
    }
}
