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

package stroom.query.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A query's results table, beside the source of the row selected when the row has one (both shown
/// here by its controller).
public class QueryResultTableSplit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SplitPane split;
    @UiField
    SimplePanel table;
    @UiField
    SimplePanel source;

    /// Makes it, with the source hidden.
    public QueryResultTableSplit() {
        initWidget(BINDER.createAndBindUi(this));
        split.setPaneHidden(source, true);
    }

    /// @param table The results table.
    public void setTable(final IsWidget table) {
        this.table.setWidget(table);
    }

    /// @param source The source of the row selected.
    public void setSource(final IsWidget source) {
        this.source.setWidget(source);
    }

    /// @param shown Whether the source shows beside the table.
    public void setSourceShown(final boolean shown) {
        split.setPaneHidden(source, !shown);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryResultTableSplit> {

    }
}
