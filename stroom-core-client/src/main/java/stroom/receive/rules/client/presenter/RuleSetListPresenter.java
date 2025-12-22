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

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

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
                        .enabledWhen(ReceiveDataRule::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Rule")
                        .withToolTip("The number of the rule. Rules are evaluated in ascending order of rule number.")
                        .rightAligned()
                        .build(),
                60);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                                getSafeHtml(row, ReceiveDataRule::getName))
                        .enabledWhen(ReceiveDataRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The optional name of the rule. The name is only an aid in distinguishing rules.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        // Expression.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                                getSafeHtml(row, row2 -> row2.getExpression().toString()))
                        .enabledWhen(ReceiveDataRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Expression")
                        .withToolTip("The expression to evaluate against the received meta entries.")
                        .build(),
                500);

        // Action.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                                getSafeHtml(row, row2 -> row2.getAction().getDisplayValue()))
                        .enabledWhen(ReceiveDataRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Action")
                        .withToolTip("The action to perform if the rule matches against the received meta entries.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder((ReceiveDataRule row) ->
                                getSafeHtml(row,
                                        row2 -> (row2.isEnabled()
                                                ? "Enabled"
                                                : "Disabled")))
                        .enabledWhen(ReceiveDataRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("State")
                        .withToolTip("Whether this rule is enabled or disabled. A disabled rule will be ignored " +
                                     "when checking incoming data.")
                        .build(),
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
        return SafeHtmlUtils.fromString(value);
//        if (row.isEnabled()) {
//            return SafeHtmlUtils.fromString(value);
//        } else {
//            return new SafeHtmlBuilder()
//                    .appendHtmlConstant("<span title=\"")
//                    .appendEscaped(value)
//                    .appendHtmlConstant("\">")
//                    .appendEscaped(value)
//                    .appendHtmlConstant("</span>")
//                    .toSafeHtml();
//        }
    }
}
