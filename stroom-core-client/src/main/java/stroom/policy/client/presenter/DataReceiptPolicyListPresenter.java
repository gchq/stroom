/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.policy.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.policy.shared.DataReceiptRule;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.svg.client.SvgPreset;
import stroom.util.client.BorderUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;

public class DataReceiptPolicyListPresenter extends MyPresenterWidget<DataGridView<DataReceiptRule>> {
    @Inject
    public DataReceiptPolicyListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Rule.
        final Column<DataReceiptRule, String> ruleColumn = new Column<DataReceiptRule, String>(new TextCell()) {
            @Override
            public String getValue(final DataReceiptRule row) {
                return String.valueOf(row.getRuleNumber());
            }
        };
        getView().addResizableColumn(ruleColumn, "Rule", 40);

        // Name.
        final Column<DataReceiptRule, String> nameColumn = new Column<DataReceiptRule, String>(new TextCell()) {
            @Override
            public String getValue(final DataReceiptRule row) {
                return String.valueOf(row.getName());
            }
        };
        getView().addResizableColumn(nameColumn, "Name", ColumnSizeConstants.MEDIUM_COL);

        // Expression.
        final Column<DataReceiptRule, String> expressionColumn = new Column<DataReceiptRule, String>(new TextCell()) {
            @Override
            public String getValue(final DataReceiptRule row) {
                return row.getExpression().toString();
            }
        };
        getView().addResizableColumn(expressionColumn, "Expression", 500);

        // Action.
        final Column<DataReceiptRule, String> actionColumn = new Column<DataReceiptRule, String>(new TextCell()) {
            @Override
            public String getValue(final DataReceiptRule row) {
                return row.getAction().getDisplayValue();
            }
        };
        getView().addResizableColumn(actionColumn, "Action", ColumnSizeConstants.SMALL_COL);


        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<DataReceiptRule> data) {
        getView().setRowData(0, data);
        getView().setRowCount(data.size());
    }

    public MultiSelectionModel<DataReceiptRule> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }
}
