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

package stroom.dashboard.client.table.cf;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.ConditionalFormattingRule;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class RuleListPresenter extends MyPresenterWidget<PagerView> implements HasDirtyHandlers {

    private final MyDataGrid<ConditionalFormattingRule> dataGrid;
    private final MultiSelectionModelImpl<ConditionalFormattingRule> selectionModel;
    private List<ConditionalFormattingRule> currentData;

    @Inject
    public RuleListPresenter(final EventBus eventBus,
                             final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
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
            setDirty(true);
        }
    }

    public void setData(final List<ConditionalFormattingRule> data) {
        this.currentData = data;
        refresh();
    }

    private void refresh() {
        dataGrid.setRowData(0, currentData);
        dataGrid.setRowCount(currentData.size());
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
