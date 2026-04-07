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

import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.statistics.impl.sql.client.presenter.State.Field;
import stroom.statistics.impl.sql.client.presenter.State.Mask;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StatisticsCustomMaskListPresenter
        extends DocPresenter<PagerView, StatisticStoreDoc> {

    private final MyDataGrid<Mask> dataGrid;
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

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        newButton = view.addButton(SvgPresets.NEW_ITEM);
        autoGenerateButton = view.addButton(SvgPresets.GENERATE);
        removeButton = view.addButton(SvgPresets.REMOVE);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAdd(event);
            }
        }));

        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onRemove(event);
            }
        }));

        registerHandler(autoGenerateButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAutoGenerate(event);
            }
        }));

        registerHandler(selectionModel.addSelectionHandler(event -> enableButtons()));
    }

    private void enableButtons() {
        newButton.setEnabled(!isReadOnly());
        autoGenerateButton.setEnabled(!isReadOnly());

        final Mask selectedElement = selectionModel.getSelected();
        removeButton.setEnabled(!isReadOnly() && selectedElement != null);

        if (isReadOnly()) {
            newButton.setTitle("New roll-up permutation disabled as read only");
            autoGenerateButton.setTitle("Auto-generate roll-up permutations disabled as read only");
            removeButton.setTitle("Remove roll-up permutation disabled as read only");
        } else {
            newButton.setTitle("New roll-up permutation");
            autoGenerateButton.setTitle("Auto-generate roll-up permutations");
            removeButton.setTitle("Remove roll-up permutation");
        }
    }

    private void addColumns() {
        for (final Field statisticField : state.getFields()) {
            addStatFieldColumn(statisticField);
        }
        final EndColumn<Mask> endColumn = new EndColumn<>();
        dataGrid.addEndColumn(endColumn);
        columns.add(endColumn);
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

    private void onAdd(final ClickEvent event) {
        this.state.addMask(new HashSet<>());
        refresh();
        DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
    }

    private void onAutoGenerate(final ClickEvent event) {
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

    private void onRemove(final ClickEvent event) {
        final List<Mask> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            state.removeMasks(list);

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
