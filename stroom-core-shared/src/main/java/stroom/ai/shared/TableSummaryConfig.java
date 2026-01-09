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

package stroom.ai.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TableSummaryConfig extends AbstractConfig implements IsStroomConfig {

    public static final int DEFAULT_MAXIMUM_BATCH_SIZE = 16384;
    public static final int DEFAULT_MAXIMUM_TABLE_INPUT_ROWS = 100;

    public static final String PROP_NAME_MAXIMUM_BATCH_SIZE = "maximumBatchSize";
    public static final String PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS = "maximumTableInputRows";

    @JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE)
    private final int maximumBatchSize;
    @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS)
    private final int maximumTableInputRows;

    public TableSummaryConfig() {
        maximumBatchSize = DEFAULT_MAXIMUM_BATCH_SIZE;
        maximumTableInputRows = DEFAULT_MAXIMUM_TABLE_INPUT_ROWS;
    }

    @JsonCreator
    public TableSummaryConfig(@JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE) final int maximumBatchSize,
                              @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS) final int maximumTableInputRows) {
        this.maximumBatchSize = maximumBatchSize;
        this.maximumTableInputRows = maximumTableInputRows;
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
        return "TableSummaryConfig{" +
               "maximumBatchSize=" + maximumBatchSize +
               ", maximumTableInputRows=" + maximumTableInputRows +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<TableSummaryConfig, TableSummaryConfig.Builder> {

        private int maximumBatchSize = DEFAULT_MAXIMUM_BATCH_SIZE;
        private int maximumTableInputRows = DEFAULT_MAXIMUM_TABLE_INPUT_ROWS;

        private Builder() {
        }

        private Builder(final TableSummaryConfig tableSummaryConfig) {
            maximumBatchSize = tableSummaryConfig.maximumBatchSize;
            maximumTableInputRows = tableSummaryConfig.maximumTableInputRows;
        }

        public Builder maximumBatchSize(final int maximumBatchSize) {
            this.maximumBatchSize = maximumBatchSize;
            return self();
        }

        public Builder maximumTableInputRows(final int maximumTableInputRows) {
            this.maximumTableInputRows = maximumTableInputRows;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TableSummaryConfig build() {
            return new TableSummaryConfig(maximumBatchSize, maximumTableInputRows);
        }
    }
}
