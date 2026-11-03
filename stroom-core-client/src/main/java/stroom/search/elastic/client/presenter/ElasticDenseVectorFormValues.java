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

package stroom.search.elastic.client.presenter;

import stroom.docref.DocRef;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.util.shared.NullSafe;

import java.util.Objects;

/// The values of the `dense_vector` settings form on the Settings tab, and their conversion to and from
/// [ElasticDenseVectorSettings].
public final class ElasticDenseVectorFormValues {

    /// The nearest neighbour count shown for a field with no settings; 0 leaves `k` to the Elasticsearch default.
    public static final int DEFAULT_NEAREST_NEIGHBOUR_COUNT = 0;
    /// The rerank batch size shown for a field with no settings.
    public static final int DEFAULT_RERANK_BATCH_SIZE = 1000;
    /// The rerank score minimum shown for a field with no settings.
    public static final float DEFAULT_RERANK_SCORE_MINIMUM = DenseVectorFieldConfig.DEFAULT_RERANK_SCORE_MINIMUM;

    private final DocRef embeddingModelRef;
    private final int nearestNeighbourCount;
    private final DocRef rerankModelRef;
    private final int rerankBatchSize;
    private final String rerankTextField;
    private final String rerankScoreField;
    private final float rerankScoreMinimum;

    /// @param embeddingModelRef     the model that turns query text into a vector, or `null`
    /// @param nearestNeighbourCount the number of nearest neighbours to return; 0 for the Elasticsearch default
    /// @param rerankModelRef        the rerank model, or `null` to skip reranking
    /// @param rerankBatchSize       the maximum number of hits sent to the rerank model in one call
    /// @param rerankTextField       the field whose values are sent to the rerank model, or `null`/blank
    /// @param rerankScoreField      the name of the field holding each hit's rerank score, or `null`/blank
    /// @param rerankScoreMinimum    hits with a rerank score below this value are dropped
    public ElasticDenseVectorFormValues(final DocRef embeddingModelRef,
                                        final int nearestNeighbourCount,
                                        final DocRef rerankModelRef,
                                        final int rerankBatchSize,
                                        final String rerankTextField,
                                        final String rerankScoreField,
                                        final float rerankScoreMinimum) {
        this.embeddingModelRef = embeddingModelRef;
        this.nearestNeighbourCount = nearestNeighbourCount;
        this.rerankModelRef = rerankModelRef;
        this.rerankBatchSize = rerankBatchSize;
        this.rerankTextField = blankToNull(rerankTextField);
        this.rerankScoreField = blankToNull(rerankScoreField);
        this.rerankScoreMinimum = rerankScoreMinimum;
    }

    /// @param settings the stored settings, or `null` if the field has none
    /// @return the form values for the settings, using the defaults when `settings` or its config is `null`
    public static ElasticDenseVectorFormValues fromSettings(final ElasticDenseVectorSettings settings) {
        final DenseVectorFieldConfig config = NullSafe.get(settings, ElasticDenseVectorSettings::getConfig);
        final String textField = NullSafe.get(settings, ElasticDenseVectorSettings::getRerankTextField);
        final String scoreField = NullSafe.get(settings, ElasticDenseVectorSettings::getRerankScoreField);
        if (config == null) {
            return new ElasticDenseVectorFormValues(null,
                    DEFAULT_NEAREST_NEIGHBOUR_COUNT,
                    null,
                    DEFAULT_RERANK_BATCH_SIZE,
                    textField,
                    scoreField,
                    DEFAULT_RERANK_SCORE_MINIMUM);
        }
        return new ElasticDenseVectorFormValues(config.getEmbeddingModelRef(),
                config.getNearestNeighbourCount(),
                config.getRerankModelRef(),
                config.getRerankBatchSize(),
                textField,
                scoreField,
                Objects.requireNonNullElse(config.getRerankScoreMinimum(), DEFAULT_RERANK_SCORE_MINIMUM));
    }

    /// Converts the form values to settings. Settings not shown on the form are kept from `existing`.
    ///
    /// @param existing the stored settings, or `null` if the field has none
    /// @return the settings, or `null` if every value is the default so nothing needs storing
    public ElasticDenseVectorSettings toSettings(final ElasticDenseVectorSettings existing) {
        if (isDefault()) {
            return null;
        }
        final DenseVectorFieldConfig existingConfig = NullSafe.get(existing, ElasticDenseVectorSettings::getConfig);
        final DenseVectorFieldConfig.Builder builder = existingConfig == null
                ? DenseVectorFieldConfig.builder()
                : existingConfig.copy();
        final DenseVectorFieldConfig config = builder
                .embeddingModelRef(embeddingModelRef)
                .nearestNeighbourCount(nearestNeighbourCount)
                .rerankModelRef(rerankModelRef)
                .rerankBatchSize(rerankBatchSize)
                .rerankScoreMinimum(rerankScoreMinimum)
                .build();
        return new ElasticDenseVectorSettings(config, rerankTextField, rerankScoreField);
    }

    /// @return `true` if every value is the default shown for a field with no settings
    public boolean isDefault() {
        return embeddingModelRef == null
               && nearestNeighbourCount == DEFAULT_NEAREST_NEIGHBOUR_COUNT
               && rerankModelRef == null
               && rerankBatchSize == DEFAULT_RERANK_BATCH_SIZE
               && rerankTextField == null
               && rerankScoreField == null
               && rerankScoreMinimum == DEFAULT_RERANK_SCORE_MINIMUM;
    }

    /// @return the model that turns query text into a vector, or `null`
    public DocRef getEmbeddingModelRef() {
        return embeddingModelRef;
    }

    /// @return the number of nearest neighbours to return; 0 for the Elasticsearch default
    public int getNearestNeighbourCount() {
        return nearestNeighbourCount;
    }

    /// @return the rerank model, or `null` to skip reranking
    public DocRef getRerankModelRef() {
        return rerankModelRef;
    }

    /// @return the maximum number of hits sent to the rerank model in one call
    public int getRerankBatchSize() {
        return rerankBatchSize;
    }

    /// @return the field whose values are sent to the rerank model, or `null`
    public String getRerankTextField() {
        return rerankTextField;
    }

    /// @return the name of the field holding each hit's rerank score, or `null`
    public String getRerankScoreField() {
        return rerankScoreField;
    }

    /// @return hits with a rerank score below this value are dropped
    public float getRerankScoreMinimum() {
        return rerankScoreMinimum;
    }

    private static String blankToNull(final String value) {
        return NullSafe.isBlankString(value)
                ? null
                : value.trim();
    }
}
