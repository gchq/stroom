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

import stroom.dashboard.client.main.DashboardPane;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.InputChecks;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A Table pane's Basic settings: its id and name, the query that supplies its results, whether and
/// with which pipeline (a picker its controller attaches) values are extracted, how many results it
/// keeps and shows, and how long a text value may be.
public class BasicTableSettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<DashboardPane> query;
    @UiField
    TickBox extractValues;
    @UiField
    TickBox useDefaultExtractionPipeline;
    @UiField
    DropDownBox pipeline;
    @UiField
    TextBox maxResults;
    @UiField
    ValueSpinner pageSize;
    @UiField
    TickBox showDetail;
    @UiField
    TickBox overrideMaxStringFieldLength;
    @UiField
    ValueSpinner maxStringFieldLength;

    private Consumer<Boolean> onUseDefaultExtractionPipelineChange = useDefault -> {
    };
    private Consumer<Boolean> onOverrideMaxStringFieldLengthChange = override -> {
    };

    /// Makes the tab, showing a page size of 100.
    public BasicTableSettings() {
        initWidget(BINDER.createAndBindUi(this));
        pageSize.setValue(100);
        // OK refuses while it isn't a list of numbers. Before, the parts that weren't were dropped
        maxResults.setInvalidReason(InputChecks.wholeNumberList("1000000,100,10"));
        useDefaultExtractionPipeline.addValueChangeHandler(event ->
                onUseDefaultExtractionPipelineChange.accept(useDefaultExtractionPipeline.getValue()));
        overrideMaxStringFieldLength.addValueChangeHandler(event ->
                onOverrideMaxStringFieldLengthChange.accept(overrideMaxStringFieldLength.getValue()));
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

    /// Lists the queries to choose from, keeping the one chosen.
    ///
    /// @param queryList The dashboard's queries.
    public void setQueryList(final List<DashboardPane> queryList) {
        final DashboardPane query = getQuery();

        this.query.clear();
        this.query.addItems(queryList);

        // Reselect query id.
        setQuery(query);
    }

    /// @return The query chosen, if any.
    public DashboardPane getQuery() {
        return this.query.getValue();
    }

    /// @param query The query to show as chosen.
    public void setQuery(final DashboardPane query) {
        this.query.setValue(query);
    }

    /// @return Whether values are extracted from the original events.
    public boolean isExtractValues() {
        return this.extractValues.getValue();
    }

    /// @param extractValues Whether values are extracted from the original events.
    public void setExtractValues(final boolean extractValues) {
        this.extractValues.setValue(extractValues);
    }

    /// @return Whether the data source's default extraction pipeline is used.
    public boolean isUseDefaultExtractionPipeline() {
        return useDefaultExtractionPipeline.getValue();
    }

    /// @param useDefaultExtractionPipeline Whether the data source's default extraction pipeline is
    ///                                     used.
    public void setUseDefaultExtractionPipeline(final boolean useDefaultExtractionPipeline) {
        this.useDefaultExtractionPipeline.setValue(useDefaultExtractionPipeline);
    }

    /// @return The extraction pipeline's picker, for a controller to attach.
    public DropDownBox getPipelineBox() {
        return pipeline;
    }

    /// @return The maximum results at each grouping level, as typed (comma separated).
    public String getMaxResults() {
        return this.maxResults.getValue();
    }

    /// @param maxResults The maximum results at each grouping level, comma separated.
    public void setMaxResults(final String maxResults) {
        this.maxResults.setText(maxResults);
    }

    /// @return The rows on each page.
    public int getPageSize() {
        return pageSize.getIntValue();
    }

    /// @param pageSize The rows on each page.
    public void setPageSize(final int pageSize) {
        this.pageSize.setValue(pageSize);
    }

    /// @return Whether a group's detail rows are shown.
    public boolean isShowDetail() {
        return this.showDetail.getValue();
    }

    /// @param showDetail Whether a group's detail rows are shown.
    public void setShowDetail(final boolean showDetail) {
        this.showDetail.setValue(showDetail);
    }

    /// @param maxStringFieldLength How many characters of each text value are kept.
    public void setMaxStringFieldLength(final Integer maxStringFieldLength) {
        this.maxStringFieldLength.setValue(maxStringFieldLength);
    }

    /// @return How many characters of each text value are kept.
    public Integer getMaxStringFieldLength() {
        return maxStringFieldLength.getIntValue();
    }

    /// @param overrideMaxStringFieldLength Whether the table sets its own limit on text values.
    public void setOverrideMaxStringFieldLength(final boolean overrideMaxStringFieldLength) {
        this.overrideMaxStringFieldLength.setValue(overrideMaxStringFieldLength);
    }

    /// @return Whether the table sets its own limit on text values.
    public boolean isOverrideMaxStringFieldLength() {
        return overrideMaxStringFieldLength.getValue();
    }

    /// @param enable Whether the limit on text values can be changed.
    public void enableMaxStringFieldLength(final boolean enable) {
        maxStringFieldLength.setEnabled(enable);
    }

    /// @param onUseDefaultExtractionPipelineChange Given whether Use Default Extraction Pipeline is
    ///                                             ticked, when the user ticks or unticks it.
    public void setOnUseDefaultExtractionPipelineChange(final Consumer<Boolean> onUseDefaultExtractionPipelineChange) {
        this.onUseDefaultExtractionPipelineChange = onUseDefaultExtractionPipelineChange;
    }

    /// @param onOverrideMaxStringFieldLengthChange Given whether Override Maximum String Field Length
    ///                                             is ticked, when the user ticks or unticks it.
    public void setOnOverrideMaxStringFieldLengthChange(final Consumer<Boolean> onOverrideMaxStringFieldLengthChange) {
        this.onOverrideMaxStringFieldLengthChange = onOverrideMaxStringFieldLengthChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicTableSettings> {

    }
}
