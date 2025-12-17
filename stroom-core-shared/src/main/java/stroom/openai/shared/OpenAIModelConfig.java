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

package stroom.openai.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class OpenAIModelConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAXIMUM_BATCH_SIZE = 1024;
    private static final int DEFAULT_MAXIMUM_TABLE_INPUT_ROWS = 100;

    private final String baseUrl;
    private final String apiKey;
    private final String modelId;
    private final int maximumBatchSize;
    private final int maximumTableInputRows;

    public OpenAIModelConfig() {
        baseUrl = null;
        apiKey = null;
        modelId = null;
        maximumBatchSize = DEFAULT_MAXIMUM_BATCH_SIZE;
        maximumTableInputRows = DEFAULT_MAXIMUM_TABLE_INPUT_ROWS;
    }

    @JsonCreator
    public OpenAIModelConfig(@JsonProperty("baseUrl") final String baseUrl,
                             @JsonProperty("apiKey") final String apiKey,
                             @JsonProperty("modelId") final String modelId,
                             @JsonProperty("maximumBatchSize") final int maximumBatchSize,
                             @JsonProperty("maximumTableInputRows") final int maximumTableInputRows) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.maximumBatchSize = maximumBatchSize;
        this.maximumTableInputRows = maximumTableInputRows;
    }

    @JsonPropertyDescription("Override the base URL for the OpenAI API endpoint. Example: https://api.example.com/v1")
    public String getBaseUrl() {
        return baseUrl;
    }

    @JsonPropertyDescription("Optional API key for authenticating with the OpenAI API")
    public String getApiKey() {
        return apiKey;
    }

    @JsonPropertyDescription("ID of the OpenAI model. Example: meta-llama/Llama-3.1-8B-Instruct")
    public String getModelId() {
        return modelId;
    }

    @JsonPropertyDescription("Maximum number of tokens to pass the AI service at a time")
    public int getMaximumBatchSize() {
        return maximumBatchSize;
    }

    @JsonPropertyDescription("Maximum number of table result rows to pass to the AI when making requests")
    public int getMaximumTableInputRows() {
        return maximumTableInputRows;
    }

    @Override
    public String toString() {
        return "OpenAIModelConfig{" +
               "baseUrl='" + baseUrl + "'" +
               ", modelId='" + modelId + "'" +
               ", maximumBatchSize=" + maximumBatchSize +
               ", maximumTableInputRows=" + maximumTableInputRows +
               '}';
    }
}
