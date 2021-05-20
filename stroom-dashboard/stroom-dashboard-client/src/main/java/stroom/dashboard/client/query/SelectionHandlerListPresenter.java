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

package stroom.dashboard.client.query;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;

public class SelectionHandlerListPresenter
        extends MyPresenterWidget<DataGridView<ComponentSelectionHandler>> {

    @Inject
    public SelectionHandlerListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Component.
        final Column<ComponentSelectionHandler, String> componentColumn =
                new Column<ComponentSelectionHandler, String>(new TextCell()) {
                    @Override
                    public String getValue(final ComponentSelectionHandler row) {
                        return row.getComponentId();
                    }
                };
        getView().addResizableColumn(componentColumn, "Component", 200);

        // Expression.
        final Column<ComponentSelectionHandler, String> expressionColumn =
                new Column<ComponentSelectionHandler, String>(new TextCell()) {
                    @Override
                    public String getValue(final ComponentSelectionHandler row) {
                        return row.getExpression().toString();
                    }
                };
        getView().addResizableColumn(expressionColumn, "Expression", 200);

        // Enabled.
        final Column<ComponentSelectionHandler, TickBoxState> enabledColumn =
                new Column<ComponentSelectionHandler, TickBoxState>(
                        TickBoxCell.create(
                                new TickBoxCell.NoBorderAppearance(),
                                false,
                                false,
                                false)) {
                    @Override
                    public TickBoxState getValue(final ComponentSelectionHandler row) {
                        if (row == null) {
                            return null;
                        }
                        return TickBoxState.fromBoolean(row.isEnabled());
                    }
                };
        getView().addColumn(enabledColumn, "Enabled", 50);

        getView().addEndColumn(new EndColumn<>());
    }

    public void setData(final List<ComponentSelectionHandler> data) {
        getView().setRowData(0, data);
        getView().setRowCount(data.size());
    }

    public MultiSelectionModel<ComponentSelectionHandler> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }
}
