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

import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// An Embedded Query pane's Basic settings: its id and name, whether it runs a saved query (a picker
/// its controller attaches) or a copy of one (Copy Query), whether it runs when the dashboard opens
/// and refreshes, and its table's page size.
public class BasicEmbeddedQuerySettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    TickBox referenceExistingQuery;
    @UiField
    FormGroup queryRefFormGroup;
    @UiField
    DropDownBox queryRef;
    @UiField
    Button copyQuery;
    @UiField
    TickBox queryOnOpen;
    @UiField
    TickBox autoRefresh;
    @UiField
    TextBox refreshInterval;
    @UiField
    ValueSpinner pageSize;

    private Consumer<Boolean> onReferenceChange = reference -> {
    };
    private Runnable onCopyQuery = () -> {
    };

    /// Makes the tab, showing a page size of 100.
    public BasicEmbeddedQuerySettings() {
        initWidget(BINDER.createAndBindUi(this));
        pageSize.setValue(100);
        referenceExistingQuery.addValueChangeHandler(event -> {
            onReferenceChange.accept(isReference());
            updateEnabledState();
        });
        copyQuery.setOnClick(() -> onCopyQuery.run());
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

    /// @return The saved query's picker, for a controller to attach.
    public DropDownBox getQueryRefBox() {
        return queryRef;
    }

    /// @return Whether it runs a saved query, rather than a copy.
    public boolean isReference() {
        return referenceExistingQuery.getValue();
    }

    /// @param reference Whether it runs a saved query, rather than a copy.
    public void setReference(final boolean reference) {
        this.referenceExistingQuery.setValue(reference);
        updateEnabledState();
    }

    private void updateEnabledState() {
        final boolean reference = referenceExistingQuery.getValue();
        queryRefFormGroup.setStyleName("section--disabled", !reference);
        copyQuery.setEnabled(!reference);
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

    /// @return The rows on each page of the results.
    public int getPageSize() {
        return pageSize.getIntValue();
    }

    /// @param pageSize The rows on each page of the results.
    public void setPageSize(final int pageSize) {
        this.pageSize.setValue(pageSize);
    }

    /// @param onReferenceChange Given whether it runs a saved query, when the user ticks or unticks
    ///                          Reference Existing Query.
    public void setOnReferenceChange(final Consumer<Boolean> onReferenceChange) {
        this.onReferenceChange = onReferenceChange;
    }

    /// @param onCopyQuery Run when Copy Query is pressed.
    public void setOnCopyQuery(final Runnable onCopyQuery) {
        this.onCopyQuery = onCopyQuery;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicEmbeddedQuerySettings> {

    }
}
