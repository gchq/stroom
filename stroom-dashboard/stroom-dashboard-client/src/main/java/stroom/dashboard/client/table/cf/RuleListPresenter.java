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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.svg.client.Preset;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class RuleListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<ConditionalFormattingRule> dataGrid;
    private final MultiSelectionModelImpl<ConditionalFormattingRule> selectionModel;

    @Inject
    public RuleListPresenter(final EventBus eventBus,
                             final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Expression.
        final Column<ConditionalFormattingRule, String> expressionColumn =
                new Column<ConditionalFormattingRule, String>(new TextCell()) {
                    @Override
                    public String getValue(final ConditionalFormattingRule row) {
                        return row.getExpression().toString();
                    }
                };
        dataGrid.addResizableColumn(expressionColumn, "Expression", 200);

        // Background colour.
        final Column<ConditionalFormattingRule, String> backgroundColumn =
                new Column<ConditionalFormattingRule, String>(new TextCell()) {
                    @Override
                    public String getValue(final ConditionalFormattingRule row) {
                        return row.getBackgroundColor();
                    }
                };
        dataGrid.addResizableColumn(backgroundColumn, "Background", ColumnSizeConstants.MEDIUM_COL);

        // Text colour.
        final Column<ConditionalFormattingRule, String> textColumn =
                new Column<ConditionalFormattingRule, String>(new TextCell()) {
                    @Override
                    public String getValue(final ConditionalFormattingRule row) {
                        return row.getTextColor();
                    }
                };
        dataGrid.addResizableColumn(textColumn, "Text", ColumnSizeConstants.MEDIUM_COL);

        // Hide.
        final Column<ConditionalFormattingRule, TickBoxState> hideColumn =
                new Column<ConditionalFormattingRule, TickBoxState>(
                        TickBoxCell.create(
                                new TickBoxCell.NoBorderAppearance(),
                                false,
                                false,
                                false)) {
                    @Override
                    public TickBoxState getValue(final ConditionalFormattingRule row) {
                        if (row == null) {
                            return null;
                        }
                        return TickBoxState.fromBoolean(row.isHide());
                    }
                };
        dataGrid.addColumn(hideColumn, "Hide", 50);

        // Enabled.
        final Column<ConditionalFormattingRule, TickBoxState> enabledColumn =
                new Column<ConditionalFormattingRule, TickBoxState>(
                        TickBoxCell.create(
                                new TickBoxCell.NoBorderAppearance(),
                                false,
                                false,
                                false)) {
                    @Override
                    public TickBoxState getValue(final ConditionalFormattingRule row) {
                        if (row == null) {
                            return null;
                        }
                        return TickBoxState.fromBoolean(row.isEnabled());
                    }
                };
        dataGrid.addColumn(enabledColumn, "Enabled", 50);

        dataGrid.addEndColumn(new EndColumn<>());
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
