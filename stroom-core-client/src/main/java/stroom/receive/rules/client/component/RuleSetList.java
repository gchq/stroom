/*
 * Copyright 2026 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Function;

/// A rule set's data receipt rules, a row each (its number, name, expression, action and state),
/// faded where disabled; several can be selected.
public class RuleSetList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ReceiveDataRule> dataGrid;

    private final MultiSelectionModelImpl<ReceiveDataRule> selectionModel;

    /// Makes an empty list.
    public RuleSetList() {
        initWidget(BINDER.createAndBindUi(this));
        // A rule has no id: its number is its position, which an edit keeps but a move changes
        dataGrid.setKeyProvider(ReceiveDataRule::getRuleNumber);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
    }

    /// Puts the focus on the list.
    public void focus() {
        dataGrid.setFocus(true);
    }

    /// @param data The rules.
    public void setData(final List<ReceiveDataRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    /// @return The selection.
    public MultiSelectionModel<ReceiveDataRule> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    private void addColumns() {
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
    }

    private SafeHtml getSafeHtml(final ReceiveDataRule row,
                                 final Function<ReceiveDataRule, String> valueFunc) {
        return SafeHtmlUtils.fromString(valueFunc.apply(row));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RuleSetList> {

    }
}
