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
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class RuleListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<ConditionalFormattingRule> dataGrid;
    private final MultiSelectionModelImpl<ConditionalFormattingRule> selectionModel;

//    private final RestSaveQueue<Integer, Boolean> ruleEnabledSaveQueue;
//    private final RestSaveQueue<Integer, Boolean> ruleEnabledSaveQueue;

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
                DataGridUtil.textColumnBuilder(ConditionalFormattingRule::getBackgroundColor)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Background Colour")
                        .withToolTip("The background colour of matching rows.")
                        .build(),
                130);
        // Text colour.
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(ConditionalFormattingRule::getTextColor)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Text Colour")
                        .withToolTip("The text colour of matching rows.")
                        .build(),
                100);
        // Hide.
        addHideColumn();

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addEnabledColumn() {
        // Enabled.
        final Column<ConditionalFormattingRule, TickBoxState> enabledColumn =
                DataGridUtil.updatableTickBoxColumnBuilder(ConditionalFormattingRule::isEnabled)
                        .centerAligned()
                        .build();

        enabledColumn.setFieldUpdater((index, row, value) -> {

        });

        dataGrid.addColumn(
                enabledColumn,
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Un-check to ignore this rule.")
                        .build(),
//                "Enabled",
                ColumnSizeConstants.ENABLED_COL);
    }

    private void addHideColumn() {
        final Column<ConditionalFormattingRule, TickBoxState> hideColumn =
                DataGridUtil.updatableTickBoxColumnBuilder(ConditionalFormattingRule::isHide)
                        .centerAligned()
                        .build();

        hideColumn.setFieldUpdater((index, row, value) -> {

        });

        dataGrid.addColumn(hideColumn,
                DataGridUtil.headingBuilder("Hide Row")
                        .withToolTip("When checked, matching rows are hidden.")
                        .build(),
                70);
    }

    public void setData(final List<ConditionalFormattingRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    public MultiSelectionModel<ConditionalFormattingRule> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }
}
