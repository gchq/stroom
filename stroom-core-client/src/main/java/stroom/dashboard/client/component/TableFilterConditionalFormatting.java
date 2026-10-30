/*
 * Copyright 2018 Crown Copyright
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

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

/// A Table Filter's conditional formatting: side by side, a [TableFilterColumn] for each column it
/// filters, holding that column's rules.
public class TableFilterConditionalFormatting extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel panel;

    /// Makes the tab, with no columns.
    public TableFilterConditionalFormatting() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Takes off every column.
    public void clearColumns() {
        panel.clear();
    }

    /// Adds a column, after the others.
    ///
    /// @param name  The column's name.
    /// @param rules The column's rules.
    public void addColumn(final String name, final IsWidget rules) {
        final TableFilterColumn column = new TableFilterColumn();
        column.setName(name);
        column.setContent(rules);
        panel.add(column);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TableFilterConditionalFormatting> {

    }
}
