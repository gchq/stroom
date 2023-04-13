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

package stroom.receive.rules.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Function;

public class RuleSetListPresenter extends MyPresenterWidget<PagerView> implements Focus {

    private final MyDataGrid<ReceiveDataRule> dataGrid;
    private final MultiSelectionModelImpl<ReceiveDataRule> selectionModel;

    @Inject
    public RuleSetListPresenter(final EventBus eventBus,
                                final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
    }

    @Override
    public void focus() {
        dataGrid.setFocus(true);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Rule.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                        getSafeHtml(row, row2 -> Integer.toString(row2.getRuleNumber())))
                        .rightAligned()
                        .build(),
                DataGridUtil.createRightAlignedHeader("Rule"),
                40);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                        getSafeHtml(row, ReceiveDataRule::getName))
                        .build(),
                "Name",
                ColumnSizeConstants.MEDIUM_COL);

        // Expression.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                        getSafeHtml(row, row2 -> row2.getExpression().toString()))
                        .build(),
                "Expression",
                500);

        // Action.
        dataGrid.addResizableColumn(
                DataGridUtil.safeHtmlColumn((ReceiveDataRule row) ->
                        getSafeHtml(row, row2 -> row2.getAction().getDisplayValue())),
                "Action",
                ColumnSizeConstants.SMALL_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public void setData(final List<ReceiveDataRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    public MultiSelectionModel<ReceiveDataRule> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    private SafeHtml getSafeHtml(final ReceiveDataRule row,
                                 final Function<ReceiveDataRule, String> valueFunc) {
        final String value = valueFunc.apply(row);
        if (row.isEnabled()) {
            return SafeHtmlUtils.fromString(value);
        } else {
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            builder.appendHtmlConstant("<span style=\"color:grey\">");
            builder.appendEscaped(value);
            builder.appendHtmlConstant("</span>");
            return builder.toSafeHtml();
        }
    }
}
