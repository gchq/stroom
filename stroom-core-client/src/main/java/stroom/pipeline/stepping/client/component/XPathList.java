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

package stroom.pipeline.stepping.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.pipeline.shared.XPathFilter;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;

/// The XPath filters a stepping element's output must match, a row each (its XPath, condition, value
/// and whether it ignores case); several can be selected.
public class XPathList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<XPathFilter> dataGrid;

    private final MultiSelectionModelImpl<XPathFilter> selectionModel;
    private final ListDataProvider<XPathFilter> dataProvider;

    /// Makes an empty list.
    public XPathList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
        dataProvider = new ListDataProvider<>(new ArrayList<>());
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    private void addColumns() {
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

        // Ignore case: shown, not changed here (the filter's dialog changes it), so read only, as
        // DataGridUtil.readOnlyTickBoxColumnBuilder makes them
        final Column<XPathFilter, TickBoxState> ignoreCaseColumn = new Column<XPathFilter, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.NoBorderAppearance(), false, false, false)) {
            @Override
            public TickBoxState getValue(final XPathFilter filter) {
                return TickBoxState.fromBoolean(filter.isIgnoreCase());
            }
        };
        dataGrid.addResizableColumn(ignoreCaseColumn, "Ignore Case", 100);
    }

    /// Shows the filters again, after its data provider's list has changed.
    public void refresh() {
        dataProvider.refresh();
    }

    /// @return The data provider, whose list holds the filters shown.
    public ListDataProvider<XPathFilter> getDataProvider() {
        return dataProvider;
    }

    /// @return The selection.
    public MultiSelectionModel<XPathFilter> getSelectionModel() {
        return selectionModel;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XPathList> {

    }
}
