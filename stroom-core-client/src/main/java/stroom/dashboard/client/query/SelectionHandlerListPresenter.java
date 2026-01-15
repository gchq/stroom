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

package stroom.dashboard.client.query;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class SelectionHandlerListPresenter
        extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<ComponentSelectionHandler> dataGrid;
    private final MultiSelectionModelImpl<ComponentSelectionHandler> selectionModel;
    private Components components;

    @Inject
    public SelectionHandlerListPresenter(final EventBus eventBus,
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
        // Enabled.
        final Column<ComponentSelectionHandler, TickBoxState> enabledColumn =
                DataGridUtil.readOnlyTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ComponentSelectionHandler::isEnabled))
                        .centerAligned()
                        .build();
        dataGrid.addColumn(
                enabledColumn,
                DataGridUtil.headingBuilder("Enabled")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        // Expression.
        final Column<ComponentSelectionHandler, String> expressionColumn =
                new Column<ComponentSelectionHandler, String>(new TextCell()) {
                    @Override
                    public String getValue(final ComponentSelectionHandler row) {
                        return row.getExpression().toString();
                    }
                };
        dataGrid.addResizableColumn(expressionColumn, "Expression", 500);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setData(final List<ComponentSelectionHandler> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    public MultiSelectionModel<ComponentSelectionHandler> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public void setComponents(final Components components) {
        this.components = components;
    }
}
