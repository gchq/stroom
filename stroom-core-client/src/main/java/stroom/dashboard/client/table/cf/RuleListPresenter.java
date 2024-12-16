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

package stroom.dashboard.client.table.cf;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RuleListPresenter extends MyPresenterWidget<PagerView> implements HasDirtyHandlers {

    private final MyDataGrid<ConditionalFormattingRule> dataGrid;
    private final MultiSelectionModelImpl<ConditionalFormattingRule> selectionModel;
    private List<ConditionalFormattingRule> rules = new ArrayList<>();
    private Runnable dataChangeHandler = null;

    @Inject
    public RuleListPresenter(final EventBus eventBus,
                             final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        addEnabledColumn();

        // Expression.
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(ConditionalFormattingRule::getExpression, Object::toString)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Expression")
                        .withToolTip("Rows matching this expression will be formatted by this rule.")
                        .build(),
                200);
        // Background colour.
        dataGrid.addColumn(
                DataGridUtil.colourSwatchColumnBuilder(ConditionalFormattingRule::getBackgroundColor)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Background Colour")
                        .withToolTip("The background colour of matching rows.")
                        .build(),
                150);
        // Text colour.
        dataGrid.addColumn(
                DataGridUtil.colourSwatchColumnBuilder(ConditionalFormattingRule::getTextColor)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Text Colour")
                        .withToolTip("The text colour of matching rows.")
                        .build(),
                150);
        // Hide.
        addHideColumn();

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addEnabledColumn() {
        // Enabled.
        final Column<ConditionalFormattingRule, TickBoxState> enabledColumn =
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ConditionalFormattingRule::isEnabled))
                        .centerAligned()
                        .build();

        enabledColumn.setFieldUpdater((index, row, tickBoxState) -> {
            if (row != null) {
                final ConditionalFormattingRule newRow = row
                        .copy()
                        .enabled(GwtNullSafe.isTrue(tickBoxState.toBoolean()))
                        .build();
                replaceDataGridRow(row, newRow);
            }
        });

        dataGrid.addColumn(
                enabledColumn,
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Un-check to ignore this rule.")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);
    }

    private void addHideColumn() {
        final Column<ConditionalFormattingRule, TickBoxState> hideColumn =
                DataGridUtil.updatableTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(ConditionalFormattingRule::isHide))
                        .centerAligned()
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build();

        hideColumn.setFieldUpdater((index, row, tickBoxState) -> {
            if (row != null) {
                final ConditionalFormattingRule newRow = row
                        .copy()
                        .hide(GwtNullSafe.isTrue(tickBoxState.toBoolean()))
                        .build();
                replaceDataGridRow(row, newRow);
            }
        });

        dataGrid.addColumn(hideColumn,
                DataGridUtil.headingBuilder("Hide Row")
                        .withToolTip("When checked, matching rows are hidden.")
                        .build(),
                70);
    }

    private void replaceDataGridRow(final ConditionalFormattingRule oldRule,
                                    final ConditionalFormattingRule newRule) {
        final int idx = rules.indexOf(oldRule);
        rules.remove(idx);
        rules.add(idx, newRule);

        setDirty(true);
        dataGrid.setRowData(0, rules);
        final ConditionalFormattingRule selected = getSelectionModel().getSelected();
        if (Objects.equals(selected, oldRule)) {
            getSelectionModel().setSelected(newRule);
        }
        GwtNullSafe.run(dataChangeHandler);
    }

    public void setData(final List<ConditionalFormattingRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
        rules = data;
    }

    /**
     * A dataChangeHandler to be called if the dataGrid data is changed
     */
    public void setDataChangeHandler(final Runnable dataChangeHandler) {
        this.dataChangeHandler = dataChangeHandler;
    }

    public MultiSelectionModel<ConditionalFormattingRule> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
