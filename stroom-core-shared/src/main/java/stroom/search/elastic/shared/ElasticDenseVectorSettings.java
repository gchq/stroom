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

package stroom.search.elastic.shared;

import stroom.query.api.datasource.DenseVectorFieldConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Query time settings for an Elasticsearch `dense_vector` field.
///
/// The embedding model, nearest neighbour count, rerank model, rerank batch size and rerank score minimum are held
/// in a shared [DenseVectorFieldConfig]. The names of the rerank text field and the rerank score field are specific
/// to Elasticsearch, so are held alongside it.
@JsonPropertyOrder({"config", "rerankTextField", "rerankScoreField"})
@JsonInclude(Include.NON_NULL)
public final class ElasticDenseVectorSettings {

    @JsonProperty
    private final DenseVectorFieldConfig config;
    @JsonProperty
    private final String rerankTextField;
    @JsonProperty
    private final String rerankScoreField;

    /// @param config           the embedding, kNN and rerank settings
    /// @param rerankTextField  the text field whose values are sent to the rerank model
    /// @param rerankScoreField the name of the output field holding each hit's rerank score
    @JsonCreator
    public ElasticDenseVectorSettings(@JsonProperty("config") final DenseVectorFieldConfig config,
                                      @JsonProperty("rerankTextField") final String rerankTextField,
                                      @JsonProperty("rerankScoreField") final String rerankScoreField) {
        this.config = config;
        this.rerankTextField = rerankTextField;
        this.rerankScoreField = rerankScoreField;
    }

    /// @return the embedding, kNN and rerank settings, or `null` if none are set
    public DenseVectorFieldConfig getConfig() {
        return config;
    }

    /// @return the text field whose values are sent to the rerank model, or `null` if not set
    public String getRerankTextField() {
        return rerankTextField;
    }

    /// @return the name of the output field holding each hit's rerank score, or `null` if not set
    public String getRerankScoreField() {
        return rerankScoreField;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticDenseVectorSettings that = (ElasticDenseVectorSettings) o;
        return Objects.equals(config, that.config) &&
               Objects.equals(rerankTextField, that.rerankTextField) &&
               Objects.equals(rerankScoreField, that.rerankScoreField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, rerankTextField, rerankScoreField);
    }

    @Override
    public String toString() {
        return "ElasticDenseVectorSettings{" +
               "config=" + config +
               ", rerankTextField='" + rerankTextField + '\'' +
               ", rerankScoreField='" + rerankScoreField + '\'' +
               '}';
    }
}
