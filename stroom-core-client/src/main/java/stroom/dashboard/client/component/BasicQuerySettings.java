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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// A Query pane's Basic settings: its id and name, its data source (a picker its controller
/// attaches), and whether it runs when the dashboard opens and refreshes, and how often.
public class BasicQuerySettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    DropDownBox dataSource;
    @UiField
    TickBox queryOnOpen;
    @UiField
    TickBox autoRefresh;
    @UiField
    TextBox refreshInterval;

    /// Makes the tab, empty.
    public BasicQuerySettings() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setId(final String id) {
        this.id.setText(id);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return The data source's picker, for a controller to attach.
    public DropDownBox getDataSourceBox() {
        return dataSource;
    }

    /// @return Whether the query runs when the dashboard opens.
    public boolean isQueryOnOpen() {
        return queryOnOpen.getValue();
    }

    /// @param queryOnOpen Whether the query runs when the dashboard opens.
    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen.setValue(queryOnOpen);
    }

    /// @return Whether the query runs again regularly.
    public boolean isAutoRefresh() {
        return autoRefresh.getValue();
    }

    /// @param autoRefresh Whether the query runs again regularly.
    public void setAutoRefresh(final boolean autoRefresh) {
        this.autoRefresh.setValue(autoRefresh);
    }

    /// @return How often the query runs again, as typed (e.g. `30s`).
    public String getRefreshInterval() {
        return this.refreshInterval.getText();
    }

    /// @param refreshInterval How often the query runs again (e.g. `30s`).
    public void setRefreshInterval(final String refreshInterval) {
        this.refreshInterval.setText(refreshInterval);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicQuerySettings> {

    }
}
