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

import stroom.cell.info.client.ActionCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.query.api.ExpressionOperator;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.HeadingBuilder;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// The data retention rules, a row each (whether it is enabled, its number, name, retention and
/// expression, and an action menu), faded where disabled; one can be selected.
public class DataRetentionPolicyList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<DataRetentionRule> dataGrid;

    private final MultiSelectionModelImpl<DataRetentionRule> selectionModel;
    private BiConsumer<DataRetentionRule, Boolean> onEnabledChange = (rule, enabled) -> {
    };
    private BiConsumer<DataRetentionRule, PopupPosition> onShowActionMenu = (rule, popupPosition) -> {
    };
    private String defaultRuleName;

    /// Makes an empty list.
    public DataRetentionPolicyList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by rule number (their position): a tick rebuilds them
        dataGrid.setKeyProvider(DataRetentionRule::getRuleNumber);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    /// @param defaultRuleName The name of the rule that keeps everything else, which can't be
    ///                        disabled.
    public void setDefaultRuleName(final String defaultRuleName) {
        this.defaultRuleName = defaultRuleName;
    }

    /// Puts the focus on the list.
    public void focus() {
        dataGrid.setFocus(true);
    }

    /// @param data The rules.
    public void setData(final List<DataRetentionRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    /// @return The selection, one rule at most.
    public MultiSelectionModel<DataRetentionRule> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @param onEnabledChange Given a rule and whether it is now enabled, when its tick box is
    ///                        pressed (not for the default rule).
    public void setOnEnabledChange(final BiConsumer<DataRetentionRule, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onShowActionMenu Given a rule and where to show its action menu, when its action button
    ///                         is pressed.
    public void setOnShowActionMenu(final BiConsumer<DataRetentionRule, PopupPosition> onShowActionMenu) {
        this.onShowActionMenu = onShowActionMenu;
    }

    private void addColumns() {

        addEnabledCol(DataRetentionRule::isEnabled);

        // Rule
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((DataRetentionRule row) ->
                                NullSafe.toString(row.getRuleNumber()))
                        .enabledWhen(DataRetentionRule::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Rule")
                        .rightAligned()
                        .withToolTip("The lower the rule number, the higher priority when matching streams. " +
                                     "A stream's retention will be governed by the matching rule with the " +
                                     "highest priority.")
                        .build(),
                50);

        // Name
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionRule::getName)
                        .enabledWhen(DataRetentionRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the rule.")
                        .build(),
                200);

        // Retention
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionRule::getAgeString)
                        .enabledWhen(DataRetentionRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Retention")
                        .withToolTip("The length of time streams matching this rule will be retained for.")
                        .build(),
                90);

        // Expression
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((DataRetentionRule row) ->
                                NullSafe.getOrElse(row, DataRetentionRule::getExpression,
                                        ExpressionOperator::toString, ""))
                        .enabledWhen(DataRetentionRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Expression")
                        .withToolTip("The rule expression used to match streams with.")
                        .build(),
                600);

        addActionButtonColumn(20);
    }

    private void addActionButtonColumn(final int width) {

        final ActionCell<DataRetentionRule> actionCell = new ActionCell<>(this::showActionMenu,
                DataRetentionPolicyList::getActionMenuName);

        final Column<DataRetentionRule, DataRetentionRule> expressionColumn =
                new Column<DataRetentionRule, DataRetentionRule>(actionCell) {

                    @Override
                    public DataRetentionRule getValue(final DataRetentionRule row) {
                        return row;
                    }

                };
        dataGrid.addResizableColumn(expressionColumn, "", width);
    }

    private void showActionMenu(final DataRetentionRule row, final NativeEvent event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        onShowActionMenu.accept(row, popupPosition);
    }

    /// @param row A rule.
    /// @return The name of its action menu, e.g. "Actions for rule 3 'My rule'".
    public static String getActionMenuName(final DataRetentionRule row) {
        final String name = row.getName();
        return "Actions for rule " + row.getRuleNumber()
               + (NullSafe.isBlankString(name)
                ? ""
                : " '" + name + "'");
    }

    private void addEnabledCol(final Function<DataRetentionRule, Boolean> valueFunc) {

        final Column<DataRetentionRule, TickBoxState> enabledColumn = new Column<DataRetentionRule, TickBoxState>(
                TickBoxCell.create(false, false)) {

            @Override
            public TickBoxState getValue(final DataRetentionRule rule) {
                if (rule != null && !isDefaultRule(rule)) {
                    return TickBoxState.fromBoolean(valueFunc.apply(rule));
                }
                return null;
            }
        };

        enabledColumn.setFieldUpdater((index, rule, value) -> {
            if (!isDefaultRule(rule)) {
                onEnabledChange.accept(rule, value.toBoolean());
            }
        });
        final Header<SafeHtml> heading = new HeadingBuilder("Enabled")
                .withToolTip("Disabled rules will not be considered when the 'Policy Based Data Retention' job runs.")
                .build();
        dataGrid.addColumn(enabledColumn, heading, ColumnSizeConstants.ENABLED_COL);
    }

    private boolean isDefaultRule(final DataRetentionRule rule) {
        return Objects.equals(defaultRuleName, rule.getName());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataRetentionPolicyList> {

    }
}
