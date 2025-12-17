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

import stroom.docref.DocRef;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class OpenAIModelConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAXIMUM_BATCH_SIZE = 16384;
    private static final int DEFAULT_MAXIMUM_TABLE_INPUT_ROWS = 100;

    @JsonProperty
    private final DocRef defaultApiConfig;
    @JsonProperty
    private final int maximumBatchSize;
    @JsonProperty
    private final int maximumTableInputRows;

    public OpenAIModelConfig() {
        defaultApiConfig = null;
        maximumBatchSize = DEFAULT_MAXIMUM_BATCH_SIZE;
        maximumTableInputRows = DEFAULT_MAXIMUM_TABLE_INPUT_ROWS;
    }

    @JsonCreator
    public OpenAIModelConfig(@JsonProperty("defaultApiConfig") final DocRef defaultApiConfig,
                             @JsonProperty("maximumBatchSize") final int maximumBatchSize,
                             @JsonProperty("maximumTableInputRows") final int maximumTableInputRows) {
        this.defaultApiConfig = defaultApiConfig;
        this.maximumBatchSize = maximumBatchSize;
        this.maximumTableInputRows = maximumTableInputRows;
    }

    @JsonPropertyDescription("The default API to use.")
    public DocRef getDefaultApiConfig() {
        return defaultApiConfig;
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
               "defaultApiConfig='" + defaultApiConfig + "'" +
               ", maximumBatchSize=" + maximumBatchSize +
               ", maximumTableInputRows=" + maximumTableInputRows +
               '}';
    }
}
