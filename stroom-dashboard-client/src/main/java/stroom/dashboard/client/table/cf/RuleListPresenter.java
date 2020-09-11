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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.shared.ConditionalFormattingRule;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.svg.client.SvgPreset;
import stroom.util.client.BorderUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class RuleListPresenter extends MyPresenterWidget<DataGridView<ConditionalFormattingRule>> {
    @Inject
    public RuleListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Expression.
        final Column<ConditionalFormattingRule, String> expressionColumn = new Column<ConditionalFormattingRule, String>(new TextCell()) {
            @Override
            public String getValue(final ConditionalFormattingRule row) {
                return row.getExpression().toString();
            }
        };
        getView().addResizableColumn(expressionColumn, "Expression", 200);

        // Background colour.
        final Column<ConditionalFormattingRule, String> backgroundColumn = new Column<ConditionalFormattingRule, String>(new TextCell()) {
            @Override
            public String getValue(final ConditionalFormattingRule row) {
                return row.getBackgroundColor();
            }
        };
        getView().addResizableColumn(backgroundColumn, "Background", ColumnSizeConstants.MEDIUM_COL);

        // Text colour.
        final Column<ConditionalFormattingRule, String> textColumn = new Column<ConditionalFormattingRule, String>(new TextCell()) {
            @Override
            public String getValue(final ConditionalFormattingRule row) {
                return row.getTextColor();
            }
        };
        getView().addResizableColumn(textColumn, "Text", ColumnSizeConstants.MEDIUM_COL);

        // Hide.
        final Column<ConditionalFormattingRule, TickBoxState> hideColumn = new Column<ConditionalFormattingRule, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.NoBorderAppearance(), false, false, false)) {
            @Override
            public TickBoxState getValue(final ConditionalFormattingRule row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.isHide());
            }
        };
        getView().addColumn(hideColumn, "Hide", 50);

        // Enabled.
        final Column<ConditionalFormattingRule, TickBoxState> enabledColumn = new Column<ConditionalFormattingRule, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.NoBorderAppearance(), false, false, false)) {
            @Override
            public TickBoxState getValue(final ConditionalFormattingRule row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.isEnabled());
            }
        };
        getView().addColumn(enabledColumn, "Enabled", 50);

        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<ConditionalFormattingRule> data) {
        getView().setRowData(0, data);
        getView().setRowCount(data.size());
    }

    public MultiSelectionModel<ConditionalFormattingRule> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }
}
