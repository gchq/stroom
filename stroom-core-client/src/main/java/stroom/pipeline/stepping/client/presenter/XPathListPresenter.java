/*
 * Copyright 2016 Crown Copyright
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

import java.util.ArrayList;

import stroom.data.grid.client.DoubleClickEvent;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SelectionChangeEvent.HasSelectionChangedHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.pipeline.shared.XPathFilter;
import stroom.widget.util.client.MySingleSelectionModel;

public class XPathListPresenter extends MyPresenterWidget<DataGridView<XPathFilter>>
        implements Refreshable, HasSelectionChangedHandlers {
    private final ListDataProvider<XPathFilter> dataProvider;
    private final MySingleSelectionModel<XPathFilter> selectionModel;

    @Inject
    public XPathListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<XPathFilter>(true));
        initTableColumns();

        selectionModel = new MySingleSelectionModel<XPathFilter>();
        getView().setSelectionModel(selectionModel);

        dataProvider = new ListDataProvider<XPathFilter>(new ArrayList<XPathFilter>());
        dataProvider.addDataDisplay(getView());
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // XPath.
        final Column<XPathFilter, String> xPathColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getXPath();
            }
        };
        getView().addResizableColumn(xPathColumn, "XPath", 200);

        // Condition.
        final Column<XPathFilter, String> conditionColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getMatchType().getDisplayValue();
            }
        };
        getView().addResizableColumn(conditionColumn, "Condition", 80);

        // Value.
        final Column<XPathFilter, String> valueColumn = new Column<XPathFilter, String>(new TextCell()) {
            @Override
            public String getValue(final XPathFilter filter) {
                return filter.getValue();
            }
        };
        getView().addResizableColumn(valueColumn, "Value", 150);

        // Ignore case.
        final Column<XPathFilter, TickBoxState> ignoreCaseColumn = new Column<XPathFilter, TickBoxState>(
                new TickBoxCell(false, false)) {
            @Override
            public TickBoxState getValue(final XPathFilter filter) {
                return TickBoxState.fromBoolean(filter.isIgnoreCase());
            }
        };
        getView().addColumn(ignoreCaseColumn, "Ignore Case", 80);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    public ListDataProvider<XPathFilter> getDataProvider() {
        return dataProvider;
    }

    @Override
    public HandlerRegistration addSelectionChangeHandler(final Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }

    public MySingleSelectionModel<XPathFilter> getSelectionModel() {
        return selectionModel;
    }

    public HandlerRegistration addDoubleClickHandler(final DoubleClickEvent.Handler handler) {
        return getView().addDoubleClickHandler(handler);
    }
}
