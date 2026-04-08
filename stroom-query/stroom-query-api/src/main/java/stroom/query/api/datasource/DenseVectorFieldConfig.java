/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.api.datasource;

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DenseVectorFieldConfig {

    public static final Float DEFAULT_RERANK_SCORE_MINIMUM = 0.8f;
    private static final RerankModelType DEFAULT_RERANK_MODEL_TYPE = RerankModelType.OPEN_AI;
    private static final int DEFAULT_RERANK_BATCH_SIZE = 1000;

    @JsonProperty
    private final DocRef embeddingModelRef;
    @JsonProperty
    private final VectorSimilarityFunctionType vectorSimilarityFunction;

    // The maximum size of the segment, defined in tokens.
    @JsonProperty
    private final int segmentSize;

    // The maximum size of the overlap, defined in tokens. Only full sentences are considered for the overlap.
    @JsonProperty
    private final int overlapSize;

    // The number of nearest neighbours to gather when querying
    @JsonProperty
    private final int nearestNeighbourCount;


    @JsonProperty
    private final DocRef rerankModelRef;
    @JsonProperty
    private final RerankModelType rerankModelType;
    @JsonProperty
    private final int rerankBatchSize;
    @JsonProperty
    private final Float rerankScoreMinimum;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public DenseVectorFieldConfig(@JsonProperty("embeddingModelRef") final DocRef embeddingModelRef,
                                  @JsonProperty("vectorSimilarityFunction") final VectorSimilarityFunctionType vectorSimilarityFunction,
                                  @JsonProperty("segmentSize") final int segmentSize,
                                  @JsonProperty("overlapSize") final int overlapSize,
                                  @JsonProperty("nearestNeighbourCount") final int nearestNeighbourCount,
                                  @JsonProperty("rerankModelRef") final DocRef rerankModelRef,
                                  @JsonProperty("rerankModelType") final RerankModelType rerankModelType,
                                  @JsonProperty("rerankBatchSize") final Integer rerankBatchSize,
                                  @JsonProperty("rerankScoreMinimum") final Float rerankScoreMinimum) {
        this.embeddingModelRef = embeddingModelRef;
        this.vectorSimilarityFunction = vectorSimilarityFunction;
        this.segmentSize = segmentSize;
        this.nearestNeighbourCount = nearestNeighbourCount;
        this.overlapSize = overlapSize;
        this.rerankModelRef = rerankModelRef;
        this.rerankModelType = NullSafe.requireNonNullElse(rerankModelType, DEFAULT_RERANK_MODEL_TYPE);
        this.rerankBatchSize = NullSafe.requireNonNullElse(rerankBatchSize, DEFAULT_RERANK_BATCH_SIZE);
        this.rerankScoreMinimum = NullSafe.requireNonNullElse(rerankScoreMinimum, DEFAULT_RERANK_SCORE_MINIMUM);
    }

    public DocRef getEmbeddingModelRef() {
        return embeddingModelRef;
    }

    public VectorSimilarityFunctionType getVectorSimilarityFunction() {
        return vectorSimilarityFunction;
    }

    public int getSegmentSize() {
        return segmentSize;
    }

    public int getOverlapSize() {
        return overlapSize;
    }

    public int getNearestNeighbourCount() {
        return nearestNeighbourCount;
    }

    public DocRef getRerankModelRef() {
        return rerankModelRef;
    }

    public RerankModelType getRerankModelType() {
        return rerankModelType;
    }

    public int getRerankBatchSize() {
        return rerankBatchSize;
    }

    public Float getRerankScoreMinimum() {
        return rerankScoreMinimum;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DenseVectorFieldConfig that = (DenseVectorFieldConfig) o;
        return segmentSize == that.segmentSize &&
               overlapSize == that.overlapSize &&
               nearestNeighbourCount == that.nearestNeighbourCount &&
               Objects.equals(embeddingModelRef, that.embeddingModelRef) &&
               vectorSimilarityFunction == that.vectorSimilarityFunction &&
               Objects.equals(rerankModelRef, that.rerankModelRef) &&
               rerankModelType == that.rerankModelType &&
               rerankBatchSize == that.rerankBatchSize &&
               Objects.equals(rerankScoreMinimum, that.rerankScoreMinimum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embeddingModelRef,
                vectorSimilarityFunction,
                segmentSize,
                overlapSize,
                nearestNeighbourCount,
                rerankModelRef,
                rerankModelType,
                rerankBatchSize,
                rerankScoreMinimum);
    }

    @Override
    public String toString() {
        return "DenseVectorFieldConfig{" +
               "embeddingModelRef=" + embeddingModelRef +
               ", vectorSimilarityFunction=" + vectorSimilarityFunction +
               ", segmentSize=" + segmentSize +
               ", overlapSize=" + overlapSize +
               ", nearestNeighbourCount=" + nearestNeighbourCount +
               ", rerankModelRef=" + rerankModelRef +
               ", rerankModelType=" + rerankModelType +
               ", rerankBatchSize=" + rerankBatchSize +
               ", rerankScoreMinimum=" + rerankScoreMinimum +
               '}';
    }

    public enum VectorSimilarityFunctionType implements HasDisplayValue {

        /**
         * Euclidean distance
         */
        EUCLIDEAN("Euclidean distance"),

        /**
         * Dot product. NOTE: this similarity is intended as an optimized way to perform cosine
         * similarity. In order to use it, all vectors must be normalized, including both document and
         * query vectors. Using dot product with vectors that are not normalized can result in errors or
         * poor search results. Floating point vectors must be normalized to be of unit length, while byte
         * vectors should simply all have the same norm.
         */
        DOT_PRODUCT("Dot product"),

        /**
         * Cosine similarity. NOTE: the preferred way to perform cosine similarity is to normalize all
         * vectors to unit length, and instead use DOT_PRODUCT. You
         * should only use this function if you need to preserve the original vectors and cannot normalize
         * them in advance. The similarity score is normalised to assure it is positive.
         */
        COSINE("Cosine similarity"),

        /**
         * Maximum inner product. This is like DOT_PRODUCT, but does not
         * require normalization of the inputs. Should be used when the embedding vectors store useful
         * information within the vector magnitude
         */
        MAXIMUM_INNER_PRODUCT("Maximum inner product");

        private final String displayValue;

        VectorSimilarityFunctionType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public enum RerankModelType implements HasDisplayValue {
        JINA("Jina"),
        COHERE("Cohere"),
        OPEN_AI("Open AI");

        private final String displayValue;

        RerankModelType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<DenseVectorFieldConfig, DenseVectorFieldConfig.Builder> {

        private DocRef embeddingModelRef;
        private VectorSimilarityFunctionType vectorSimilarityFunction = VectorSimilarityFunctionType.DOT_PRODUCT;
        private int segmentSize = 2000;
        private int overlapSize = 200;
        private int nearestNeighbourCount = 10;
        private DocRef rerankModelRef;
        private RerankModelType rerankModelType = RerankModelType.OPEN_AI;
        private int rerankBatchSize = DEFAULT_RERANK_BATCH_SIZE;
        private Float rerankScoreMinimum = DEFAULT_RERANK_SCORE_MINIMUM;

        private Builder() {
        }

        public Builder(final DenseVectorFieldConfig config) {
            this.embeddingModelRef = config.embeddingModelRef;
            this.vectorSimilarityFunction = config.vectorSimilarityFunction;
            this.segmentSize = config.segmentSize;
            this.overlapSize = config.overlapSize;
            this.nearestNeighbourCount = config.nearestNeighbourCount;
            this.rerankModelRef = config.rerankModelRef;
            this.rerankModelType = config.rerankModelType;
            this.rerankBatchSize = config.rerankBatchSize;
            this.rerankScoreMinimum = config.rerankScoreMinimum;
        }

        public Builder embeddingModelRef(final DocRef embeddingModelRef) {
            this.embeddingModelRef = embeddingModelRef;
            return self();
        }

        public Builder vectorSimilarityFunction(final VectorSimilarityFunctionType vectorSimilarityFunction) {
            this.vectorSimilarityFunction = vectorSimilarityFunction;
            return self();
        }

        public Builder segmentSize(final int segmentSize) {
            this.segmentSize = segmentSize;
            return self();
        }

        public Builder overlapSize(final int overlapSize) {
            this.overlapSize = overlapSize;
            return self();
        }

        public Builder nearestNeighbourCount(final int nearestNeighbourCount) {
            this.nearestNeighbourCount = nearestNeighbourCount;
            return self();
        }

        public Builder rerankModelRef(final DocRef rerankModelRef) {
            this.rerankModelRef = rerankModelRef;
            return self();
        }

        public Builder rerankModelType(final RerankModelType rerankModelType) {
            this.rerankModelType = rerankModelType;
            return self();
        }

        public Builder rerankBatchSize(final int rerankBatchSize) {
            this.rerankBatchSize = rerankBatchSize;
            return self();
        }

        public Builder rerankScoreMinimum(final Float rerankScoreMinimum) {
            this.rerankScoreMinimum = rerankScoreMinimum;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public DenseVectorFieldConfig build() {
            return new DenseVectorFieldConfig(
                    embeddingModelRef,
                    vectorSimilarityFunction,
                    segmentSize,
                    overlapSize,
                    nearestNeighbourCount,
                    rerankModelRef,
                    rerankModelType,
                    rerankBatchSize,
                    rerankScoreMinimum);
        }
    }
}
