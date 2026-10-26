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

package stroom.search.elastic.client.component;

import stroom.search.elastic.shared.ElasticIndexField;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Function;

/// An Elastic index's fields, a row each (its name, field type, native type and whether it is
/// indexed), and when they were last read.
public class ElasticIndexFieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<ElasticIndexField> dataGrid;
    @UiField
    SpanElement status;
    @UiField
    SpanElement updatedAt;

    /// Makes an empty list.
    public ElasticIndexFieldList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by name, so a row whose values change is still the same row
        dataGrid.setKeyProvider(ElasticIndexField::getFldName);
        dataGrid.addDefaultSelectionModel(false);
        addStringColumn("Name", 300, ElasticIndexField::getFldName);
        addStringColumn("Field Type", 150, row -> row.getFldType().getDisplayValue());
        addStringColumn("Native Type", 150, ElasticIndexField::getNativeType);
        addStringColumn("Indexed", 100, row -> row.isIndexed()
                ? "Yes"
                : "No");
        UIObject.setVisible(status, false);
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<ElasticIndexField> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @param indexName The index's name, to name the list by.
    public void setIndexName(final String indexName) {
        dataGrid.setTableName("Elastic Index '" + indexName + "' Fields");
    }

    /// @param time When the fields were last read, formatted.
    public void setUpdatedAt(final String time) {
        updatedAt.setInnerText(time);
        UIObject.setVisible(status, true);
    }

    private void addStringColumn(final String name,
                                 final int width,
                                 final Function<ElasticIndexField, String> function) {
        dataGrid.addResizableColumn(new Column<ElasticIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final ElasticIndexField row) {
                return function.apply(row);
            }
        }, name, width);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticIndexFieldList> {

    }
}
