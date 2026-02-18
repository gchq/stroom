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
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollupResource;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class StatisticsCustomMaskListPresenter
        extends DocPresenter<PagerView, StatisticStoreDoc> {

    private static final StatisticRollupResource STATISTIC_ROLLUP_RESOURCE =
            GWT.create(StatisticRollupResource.class);

    private final MyDataGrid<MaskHolder> dataGrid;
    private final MultiSelectionModelImpl<MaskHolder> selectionModel;

    private final ButtonView newButton;
    private final ButtonView removeButton;
    private final ButtonView autoGenerateButton;
    private final List<Column<MaskHolder, ?>> columns = new ArrayList<>();
    private final RestFactory restFactory;
    private final List<StatisticField> fields = new ArrayList<>();
    private final MaskHolderList maskList = new MaskHolderList();

    @Inject
    public StatisticsCustomMaskListPresenter(final EventBus eventBus,
                                             final PagerView view,
                                             final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        newButton = view.addButton(SvgPresets.NEW_ITEM);
        autoGenerateButton = view.addButton(SvgPresets.GENERATE);
        removeButton = view.addButton(SvgPresets.REMOVE);

        this.restFactory = restFactory;
        refreshModel();
        enableButtons();
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

        final MaskHolder selectedElement = selectionModel.getSelected();
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
        for (final StatisticField statisticField : fields) {
            addStatFieldColumn(statisticField);
        }
        final EndColumn<MaskHolder> endColumn = new EndColumn<>();
        dataGrid.addEndColumn(endColumn);
        columns.add(endColumn);
    }

    private void removeAllColumns() {
        for (final Column<MaskHolder, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
    }

    private void addStatFieldColumn(final StatisticField statisticField) {
        // Enabled.
        final Column<MaskHolder, TickBoxState> rolledUpColumn = new Column<MaskHolder, TickBoxState>(
                TickBoxCell.create(false, true)) {
            @Override
            public TickBoxState getValue(final MaskHolder row) {
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

        dataGrid.addResizableColumn(rolledUpColumn, statisticField.getFieldName(), 100);
        columns.add(rolledUpColumn);
    }

    private void onAdd(final ClickEvent event) {
        this.maskList.addMask(new HashSet<>());

        // dataProvider.refresh();
        refreshModel();
        DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
    }

    private void onAutoGenerate(final ClickEvent event) {
        final StatisticsCustomMaskListPresenter thisInstance = this;

        ConfirmEvent.fire(this,
                "Are you sure you want to clear the existing roll-ups and generate all possible " +
                "permutations for the field list?",
                result -> {
                    if (result) {
                        final List<StatisticField> fields = new ArrayList<>(this.fields);
                        restFactory
                                .create(STATISTIC_ROLLUP_RESOURCE)
                                .method(res -> res.bitMaskPermGeneration(fields.size()))
                                .onSuccess(res -> {
                                    updateState(convertRollups(fields, res));
                                    DirtyEvent.fire(thisInstance, true);
                                })
                                .taskMonitorFactory(getView())
                                .exec();
                    }
                });
    }

    private void onRemove(final ClickEvent event) {
        final List<MaskHolder> list = selectionModel.getSelectedItems();
        if (list != null && list.size() > 0) {
            maskList.removeAll(list);

            selectionModel.clear();
            // dataProvider.refresh();
            refreshModel();

            DirtyEvent.fire(StatisticsCustomMaskListPresenter.this, true);
        }
    }

    private void addNoRollUpPerm() {
        // add a line with no rollups as a starting point
        if (maskList.size() == 0) {
            maskList.addMask(new HashSet<>());
        }
    }

    public void refreshModel() {
        dataGrid.setRowData(0, maskList);
        dataGrid.setRowCount(maskList.size(), true);
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final StatisticStoreDoc document,
                          final boolean readOnly) {
        fields.clear();
        enableButtons();

        List<Set<StatisticField>> customRollUpMasks = Collections.emptyList();
        if (document != null) {
            final StatisticsDataSourceData config = document.getConfig();
            if (config != null) {
                final List<StatisticField> fields = NullSafe.list(config.getFields());
                this.fields.addAll(fields);
                customRollUpMasks = convertRollups(fields, NullSafe.collection(config.getCustomRollUpMasks()));
            }
        }

        updateState(customRollUpMasks);
    }

    private List<Set<StatisticField>> convertRollups(final List<StatisticField> fields,
                                                     final Collection<CustomRollUpMask> customRollUpMasks) {
        return customRollUpMasks
                .stream()
                .map(customRollUpMask -> {
                    final Set<StatisticField> rollup = new HashSet<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (customRollUpMask.isTagRolledUp(i)) {
                            if (fields.size() > i) {
                                final StatisticField statisticField = fields.get(i);
                                if (statisticField != null) {
                                    rollup.add(statisticField);
                                }
                            }
                        }
                    }
                    return rollup;
                })
                .collect(Collectors.toList());
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        final Set<CustomRollUpMask> masks = maskList.getMasks().stream().map(mask ->
                        new CustomRollUpMask(mask
                                .stream()
                                .map(fields::indexOf)
                                .collect(Collectors.toList())))
                .collect(Collectors.toSet());
        final StatisticsDataSourceData config = NullSafe.getOrElse(
                        document,
                        StatisticStoreDoc::getConfig,
                        StatisticsDataSourceData::copy,
                        StatisticsDataSourceData.builder())
                .customRollUpMasks(masks).build();
        return document.copy().config(config).build();
    }

    /**
     * Call this method to inform this that it needs to update its display based
     * on state that has changed on another tab
     *
     * @param customRollUpMasks The rollup masks as updated by another tab
     */
    private void updateState(final Collection<Set<StatisticField>> customRollUpMasks) {
        maskList.clear();
        maskList.addMasks(customRollUpMasks);

        addNoRollUpPerm();

        removeAllColumns();
        addColumns();
        refreshModel();
    }

    public void refresh(final List<StatisticField> fields,
                        final Set<Set<StatisticField>> customRollUpMasks) {
        this.fields.clear();
        this.fields.addAll(fields);
        updateState(customRollUpMasks);
    }

    /**
     * Wrap the mask with an ID value that can be used for equality checks in
     * the UI, to allow multiple rows with the same check box values
     */
    public static class MaskHolder {

        private final int id;
        private final Set<StatisticField> mask;

        public MaskHolder(final int id, final Set<StatisticField> mask) {
            this.id = id;
            this.mask = mask;
        }

        public Set<StatisticField> getMask() {
            return mask;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MaskHolder that = (MaskHolder) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    /**
     * Extension of ArrayList that adds an ID value to each
     * {@link CustomRollUpMask} object added to it
     */
    public static class MaskHolderList extends ArrayList<MaskHolder> {

        private int idCounter = 0;

        public boolean addMask(final Set<StatisticField> mask) {
            final MaskHolder holder = new MaskHolder(idCounter++, mask);
            return super.add(holder);
        }

        public boolean addMasks(final Collection<Set<StatisticField>> masks) {
            final List<MaskHolder> list = new ArrayList<>();
            for (final Set<StatisticField> mask : masks) {
                final MaskHolder holder = new MaskHolder(idCounter++, mask);
                list.add(holder);
            }
            return super.addAll(list);
        }

        public List<Set<StatisticField>> getMasks() {
            final List<Set<StatisticField>> list = new ArrayList<>();
            for (final MaskHolder holder : this) {
                list.add(holder.getMask());
            }
            return list;
        }
    }
}
