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

package stroom.pipeline.stepping.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.pipeline.shared.XPathFilter;
import stroom.svg.client.Preset;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.ListDataProvider;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;

public class XPathListPresenter extends MyPresenterWidget<PagerView>
        implements Refreshable {

    private final MyDataGrid<XPathFilter> dataGrid;
    private final MultiSelectionModelImpl<XPathFilter> selectionModel;
    private final ListDataProvider<XPathFilter> dataProvider;

    @Inject
    public XPathListPresenter(final EventBus eventBus,
                              final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        initTableColumns();

        dataProvider = new ListDataProvider<>(new ArrayList<>());
        dataProvider.addDataDisplay(dataGrid);
    }

    ButtonView addButton(final Preset preset) {
        return getView().addButton(preset);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // XPath.
        final Column<XPathFilter, String> xPathColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getPath();
            }
        };
        dataGrid.addResizableColumn(xPathColumn, "XPath", 200);

        // Condition.
        final Column<XPathFilter, String> conditionColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getMatchType().getDisplayValue();
            }
        };
        dataGrid.addResizableColumn(conditionColumn, "Condition", 80);

        // Value.
        final Column<XPathFilter, String> valueColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getValue();
            }
        };
        dataGrid.addResizableColumn(valueColumn, "Value", 150);

        // Ignore case.
        final Column<XPathFilter, TickBoxState> ignoreCaseColumn = new Column<XPathFilter, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final XPathFilter filter) {
                return TickBoxState.fromBoolean(filter.isIgnoreCase());
            }
        };
        dataGrid.addResizableColumn(ignoreCaseColumn, "Ignore Case", 100);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    public ListDataProvider<XPathFilter> getDataProvider() {
        return dataProvider;
    }

    public MultiSelectionModel<XPathFilter> getSelectionModel() {
        return selectionModel;
    }
}
