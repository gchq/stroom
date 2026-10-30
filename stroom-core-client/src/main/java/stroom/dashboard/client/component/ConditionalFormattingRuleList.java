/*
 * Copyright 2020 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.cell.tickbox.client.TickBoxState;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.query.api.ConditionalFormattingRule;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A table's conditional formatting rules, a row each (whether it is enabled, its expression, its
/// style and whether it hides matching rows), faded where disabled; one can be selected. Ticking a
/// rule's Enabled or Hide Row box changes the rule in the list given.
public class ConditionalFormattingRuleList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ConditionalFormattingRule> dataGrid;

    private final MultiSelectionModelImpl<ConditionalFormattingRule> selectionModel;
    private List<ConditionalFormattingRule> currentData;
    private Runnable onChange = () -> {
    };

    /// Makes an empty list.
    public ConditionalFormattingRuleList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(ConditionalFormattingRule::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    private void addColumns() {
        addEnabledColumn();

        // Expression.
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder((ConditionalFormattingRule rule) -> rule.getExpression().toString())
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Expression")
                        .withToolTip("Rows matching this expression will be formatted by this rule.")
                        .build(),
                200);
        // Style.
        // Had to put this here as GWT wasn't happy. I think it sometimes gets confused if generic types aren't
        // explicitly declared.
        final Function<ConditionalFormattingRule, SafeHtml> function = ConditionalFormattingSwatchUtil::createTableCell;
        dataGrid.addColumn(DataGridUtil.htmlColumnBuilder(function)
                        .enabledWhen(ConditionalFormattingRule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Style")
                        .withToolTip("The style of matching rows.")
                        .build(),
                200);
        // Hide.
        addHideColumn();
    }

    private void addEnabledColumn() {
        // Enabled.
        final Column<ConditionalFormattingRule, TickBoxState> enabledColumn =
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ConditionalFormattingRule::isEnabled))
                        .centerAligned()
                        .build();

        enabledColumn.setFieldUpdater((index, row, tickBoxState) -> {
            replaceRow(row, row
                    .copy()
                    .enabled(NullSafe.isTrue(tickBoxState.toBoolean()))
                    .build());
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
            replaceRow(row, row
                    .copy()
                    .hide(NullSafe.isTrue(tickBoxState.toBoolean()))
                    .build());
        });

        dataGrid.addColumn(hideColumn,
                DataGridUtil.headingBuilder("Hide Row")
                        .withToolTip("When checked, matching rows are hidden.")
                        .build(),
                70);
    }

    private void replaceRow(final ConditionalFormattingRule oldRow,
                            final ConditionalFormattingRule newRow) {
        final int i = currentData.indexOf(oldRow);
        if (i != -1) {
            currentData.set(i, newRow);
            final boolean selected = Objects.equals(selectionModel.getSelected(), oldRow);
            if (selected) {
                selectionModel.setSelected(newRow);
            }

            final int keyboardSelected = dataGrid.getKeyboardSelectedRow();
            refresh();
            dataGrid.setKeyboardSelectedRow(keyboardSelected);
            onChange.run();
        }
    }

    /// @param data The rules, changed in place when a rule's tick box is pressed.
    public void setData(final List<ConditionalFormattingRule> data) {
        this.currentData = data;
        refresh();
    }

    private void refresh() {
        dataGrid.setRowData(0, currentData);
        dataGrid.setRowCount(currentData.size());
    }

    /// @return The selection, one rule at most.
    public MultiSelectionModel<ConditionalFormattingRule> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @param onChange What to run when the user ticks or unticks a rule's Enabled or Hide Row box.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConditionalFormattingRuleList> {

    }
}
