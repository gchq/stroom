/*
 * Copyright 2025 Crown Copyright
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

package stroom.index.client.component;

import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.DenseVectorFieldConfig.RerankModelType;
import stroom.query.api.datasource.DenseVectorFieldConfig.VectorSimilarityFunctionType;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.InputChecks;
import stroom.widget.form.client.TextBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A dense vector index field's options: the models that make and rerank its vectors, how its text
/// is split into segments, and how vector queries on it are answered.
public class DenseVectorField extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox embeddingModel;
    @UiField
    SelectionBox<VectorSimilarityFunctionType> vectorSimilarityFunctionType;
    @UiField
    ValueSpinner segmentSize;
    @UiField
    ValueSpinner overlapSize;
    @UiField
    ValueSpinner nearestNeighbourCount;
    @UiField
    DropDownBox rerankModel;
    @UiField
    SelectionBox<RerankModelType> rerankModelType;
    @UiField
    ValueSpinner rerankBatchSize;
    @UiField
    TextBox rerankScoreMinimum;

    /// Makes empty options.
    public DenseVectorField() {
        initWidget(BINDER.createAndBindUi(this));
        vectorSimilarityFunctionType.addItems(VectorSimilarityFunctionType.values());
        rerankModelType.addItems(RerankModelType.values());
        // The field's dialog refuses OK while it isn't a score. Before, other text became the
        // default, 0.8
        rerankScoreMinimum.setInvalidReason(InputChecks.numberFrom(0, 1));
    }

    /// @return The embedding model's picker, for its controller to attach to.
    public DropDownBox getEmbeddingModelBox() {
        return embeddingModel;
    }

    /// @return The reranking model's picker, for its controller to attach to.
    public DropDownBox getRerankModelBox() {
        return rerankModel;
    }

    /// Shows the options, but for the models.
    ///
    /// @param config The field's options.
    public void read(final DenseVectorFieldConfig config) {
        vectorSimilarityFunctionType.setValue(config.getVectorSimilarityFunction());
        segmentSize.setValue(config.getSegmentSize());
        overlapSize.setValue(config.getOverlapSize());
        nearestNeighbourCount.setValue(config.getNearestNeighbourCount());
        rerankModelType.setValue(config.getRerankModelType());
        rerankBatchSize.setValue(config.getRerankBatchSize());
        rerankScoreMinimum.setValue(String.valueOf(config.getRerankScoreMinimum()));
    }

    /// Writes the options shown, but for the models, into the options' builder.
    ///
    /// @param builder The builder.
    public void write(final DenseVectorFieldConfig.Builder builder) {
        builder
                .vectorSimilarityFunction(vectorSimilarityFunctionType.getValue())
                .segmentSize(segmentSize.getIntValue())
                .overlapSize(overlapSize.getIntValue())
                .nearestNeighbourCount(nearestNeighbourCount.getIntValue())
                .rerankModelType(rerankModelType.getValue())
                .rerankBatchSize(rerankBatchSize.getIntValue())
                .rerankScoreMinimum(getRerankScoreMinimum());
    }

    // The minimum rerank score entered, or the default if it isn't a number (only before OK, which
    // refuses such text)
    private float getRerankScoreMinimum() {
        try {
            return Float.parseFloat(rerankScoreMinimum.getValue());
        } catch (final NumberFormatException e) {
            // Ignore.
        }
        return DenseVectorFieldConfig.DEFAULT_RERANK_SCORE_MINIMUM;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DenseVectorField> {

    }
}
