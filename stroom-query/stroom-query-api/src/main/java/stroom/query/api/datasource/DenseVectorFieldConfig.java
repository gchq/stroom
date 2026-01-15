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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DenseVectorFieldConfig {

    @JsonProperty
    private final DocRef modelRef;
    @JsonProperty
    private final VectorSimilarityFunctionType vectorSimilarityFunction;

    // The maximum size of the segment, defined in tokens.
    @JsonProperty
    private final int segmentSize;

    // The maximum size of the overlap, defined in tokens. Only full sentences are considered for the overlap.
    @JsonProperty
    private final int overlapSize;

    // The number of nearest neighbors to gather when querying
    @JsonProperty
    private final int nearestNeighbourCount;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public DenseVectorFieldConfig(@JsonProperty("modelRef") final DocRef modelRef,
                                  @JsonProperty("vectorSimilarityFunction") final VectorSimilarityFunctionType vectorSimilarityFunction,
                                  @JsonProperty("segmentSize") final int segmentSize,
                                  @JsonProperty("overlapSize") final int overlapSize,
                                  @JsonProperty("nearestNeighbourCount") final int nearestNeighbourCount) {
        this.modelRef = modelRef;
        this.vectorSimilarityFunction = vectorSimilarityFunction;
        this.segmentSize = segmentSize;
        this.nearestNeighbourCount = nearestNeighbourCount;
        this.overlapSize = overlapSize;
    }

    public DocRef getModelRef() {
        return modelRef;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DenseVectorFieldConfig that = (DenseVectorFieldConfig) o;
        return segmentSize == that.segmentSize &&
               overlapSize == that.overlapSize &&
               nearestNeighbourCount == that.nearestNeighbourCount &&
               Objects.equals(modelRef, that.modelRef) &&
               vectorSimilarityFunction == that.vectorSimilarityFunction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modelRef,
                vectorSimilarityFunction,
                segmentSize,
                overlapSize,
                nearestNeighbourCount);
    }

    @Override
    public String toString() {
        return "DenseVectorFieldConfig{" +
               "modelRef=" + modelRef +
               ", vectorSimilarityFunction=" + vectorSimilarityFunction +
               ", segmentSize=" + segmentSize +
               ", overlapSize=" + overlapSize +
               ", nearestNeighbourCount=" + nearestNeighbourCount +
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private DocRef modelRef;
        private VectorSimilarityFunctionType vectorSimilarityFunction = VectorSimilarityFunctionType.DOT_PRODUCT;
        private int segmentSize = 2000;
        private int overlapSize = 200;
        private int nearestNeighbourCount = 10;

        private Builder() {
        }

        public Builder(final DenseVectorFieldConfig indexField) {
            this.modelRef = indexField.modelRef;
            this.vectorSimilarityFunction = indexField.vectorSimilarityFunction;
            this.segmentSize = indexField.segmentSize;
            this.overlapSize = indexField.overlapSize;
            this.nearestNeighbourCount = indexField.nearestNeighbourCount;
        }

        public Builder modelRef(final DocRef modelRef) {
            this.modelRef = modelRef;
            return this;
        }

        public Builder vectorSimilarityFunction(final VectorSimilarityFunctionType vectorSimilarityFunction) {
            this.vectorSimilarityFunction = vectorSimilarityFunction;
            return this;
        }

        public Builder segmentSize(final int segmentSize) {
            this.segmentSize = segmentSize;
            return this;
        }

        public Builder overlapSize(final int overlapSize) {
            this.overlapSize = overlapSize;
            return this;
        }

        public Builder nearestNeighbourCount(final int nearestNeighbourCount) {
            this.nearestNeighbourCount = nearestNeighbourCount;
            return this;
        }

        public DenseVectorFieldConfig build() {
            return new DenseVectorFieldConfig(
                    modelRef,
                    vectorSimilarityFunction,
                    segmentSize,
                    overlapSize,
                    nearestNeighbourCount);
        }
    }
}
