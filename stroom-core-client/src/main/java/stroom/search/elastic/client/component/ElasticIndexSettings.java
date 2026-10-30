/*
 * Copyright 2019 Crown Copyright
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

import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.InputChecks;
import stroom.widget.form.client.TextBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// An Elasticsearch index's Settings tab: its cluster, index name and date field, how it is
/// searched, its default extraction pipeline, and its vector search's models and fields, with a
/// button to test the connection. Its controller ([ElasticIndexSettingsController]) attaches the
/// pickers.
public class ElasticIndexSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox indexName;
    @UiField
    ValueSpinner searchSlices;
    @UiField
    ValueSpinner searchScrollSize;
    @UiField
    DropDownBox cluster;
    @UiField
    Button testConnection;
    @UiField
    TextBox timeField;
    @UiField
    DropDownBox vectorGenerationModel;
    @UiField
    DropDownBox rerankModel;
    @UiField
    TextBox rerankTextFieldSuffix;
    @UiField
    TextBox rerankScoreFieldSuffix;
    @UiField
    TextBox rerankScoreMinimum;
    @UiField
    DropDownBox defaultExtractionPipeline;

    // The minimum rerank score read, kept while the one entered isn't a score
    private Float rerankScoreMinimumRead;
    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public ElasticIndexSettings() {
        initWidget(BINDER.createAndBindUi(this));
        indexName.addValueChangeHandler(e -> onChange.run());
        searchSlices.addValueChangeHandler(e -> onChange.run());
        searchScrollSize.addValueChangeHandler(e -> onChange.run());
        timeField.addValueChangeHandler(e -> onChange.run());
        rerankTextFieldSuffix.addValueChangeHandler(e -> onChange.run());
        rerankScoreFieldSuffix.addValueChangeHandler(e -> onChange.run());
        rerankScoreMinimum.addValueChangeHandler(e -> onChange.run());
        // Save refuses while it isn't a score (see write)
        rerankScoreMinimum.setInvalidReason(InputChecks.numberFrom(0, 1));
    }

    /// @return The cluster's picker, for the controller to attach to.
    public DropDownBox getClusterBox() {
        return cluster;
    }

    /// @return The default extraction pipeline's picker, for the controller to attach to.
    public DropDownBox getDefaultExtractionPipelineBox() {
        return defaultExtractionPipeline;
    }

    /// @return The vector generation model's picker, for the controller to attach to.
    public DropDownBox getVectorGenerationModelBox() {
        return vectorGenerationModel;
    }

    /// @return The rerank model's picker, for the controller to attach to.
    public DropDownBox getRerankModelBox() {
        return rerankModel;
    }

    /// Shows an index's settings, but for its pickers.
    ///
    /// @param index The index.
    public void read(final ElasticIndexDoc index) {
        indexName.setText(index.getIndexName());
        searchSlices.setValue(index.getSearchSlices());
        searchScrollSize.setValue(index.getSearchScrollSize());
        timeField.setValue(index.getTimeField());
        rerankTextFieldSuffix.setValue(index.getRerankTextFieldSuffix());
        rerankScoreFieldSuffix.setValue(index.getRerankScoreFieldSuffix());
        rerankScoreMinimumRead = index.getRerankScoreMinimum();
        rerankScoreMinimum.setValue(String.valueOf(rerankScoreMinimumRead));
    }

    /// Writes the settings shown, but for the pickers, into an index's builder.
    ///
    /// @param builder The index's builder.
    /// @return The builder.
    public ElasticIndexDoc.Builder write(final ElasticIndexDoc.Builder builder) {
        return builder
                .indexName(indexName.getText().trim())
                .searchSlices(searchSlices.getIntValue())
                .searchScrollSize(searchScrollSize.getIntValue())
                .timeField(timeField.getValue())
                .rerankTextFieldSuffix(rerankTextFieldSuffix.getValue())
                .rerankScoreFieldSuffix(rerankScoreFieldSuffix.getValue())
                .rerankScoreMinimum(getRerankScoreMinimum());
    }

    // The score entered or, while it isn't a score (when Save refuses, so it is never saved), the one
    // read. Before, text that wasn't a number threw as soon as the field was left
    private Float getRerankScoreMinimum() {
        final Double score = InputChecks.parseNumber(rerankScoreMinimum.getValue());
        return score == null
                ? rerankScoreMinimumRead
                : Float.valueOf(score.floatValue());
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onTestConnection What to run when Test Connection is pressed.
    public void setOnTestConnection(final Runnable onTestConnection) {
        testConnection.setOnClick(onTestConnection);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        indexName.setReadOnly(readOnly);
        searchSlices.setReadOnly(readOnly);
        searchScrollSize.setReadOnly(readOnly);
        timeField.setReadOnly(readOnly);
        rerankTextFieldSuffix.setReadOnly(readOnly);
        rerankScoreFieldSuffix.setReadOnly(readOnly);
        rerankScoreMinimum.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticIndexSettings> {

    }
}
