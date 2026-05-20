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
    public static final String DEFAULT_TABLE_QUERY_SYSTEM_PROMPT = """
                You are a data analysis AI. You will answer user questions \
                using ONLY the markdown-formatted DATA TABLE records provided. \
                If the records do not contain relevant details, say "No relevant information."
            """;
    public static final String DEFAULT_TABLE_QUERY_USER_PROMPT = """
                USER QUERY:
                {{query}}

                DATA TABLE:
                {{table}}

                Provide findings relevant only to these records, in a concise structured format.
            """;
    public static final String DEFAULT_SUMMARY_MERGE_PROMPT = """
                Merge the following TWO summaries into a single improved summary. \
                Preserve important details and remove duplicates.

                SUMMARY A:
                {{a}}

                SUMMARY B:
                {{b}}
            """;

    public static final String PROP_NAME_MAXIMUM_BATCH_SIZE = "maximumBatchSize";
    public static final String PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS = "maximumTableInputRows";
    public static final String PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT = "tableQuerySystemPrompt";
    public static final String PROP_NAME_TABLE_QUERY_USER_PROMPT = "tableQueryUserPrompt";
    public static final String PROP_NAME_SUMMARY_MERGE_PROMPT = "summaryMergePrompt";

    @JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE)
    private final int maximumBatchSize;
    @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS)
    private final int maximumTableInputRows;
    @JsonProperty(PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT)
    private final String tableQuerySystemPrompt;
    @JsonProperty(PROP_NAME_TABLE_QUERY_USER_PROMPT)
    private final String tableQueryUserPrompt;
    @JsonProperty(PROP_NAME_SUMMARY_MERGE_PROMPT)
    private final String summaryMergePrompt;

    public TableSummaryConfig() {
        maximumBatchSize = DEFAULT_MAXIMUM_BATCH_SIZE;
        maximumTableInputRows = DEFAULT_MAXIMUM_TABLE_INPUT_ROWS;
        tableQuerySystemPrompt = DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
        tableQueryUserPrompt = DEFAULT_TABLE_QUERY_USER_PROMPT;
        summaryMergePrompt = DEFAULT_SUMMARY_MERGE_PROMPT;
    }

    @JsonCreator
    public TableSummaryConfig(
            @JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE) final int maximumBatchSize,
            @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS) final int maximumTableInputRows,
            @JsonProperty(PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT) final String tableQuerySystemPrompt,
            @JsonProperty(PROP_NAME_TABLE_QUERY_USER_PROMPT) final String tableQueryUserPrompt,
            @JsonProperty(PROP_NAME_SUMMARY_MERGE_PROMPT) final String summaryMergePrompt) {
        this.maximumBatchSize = maximumBatchSize;
        this.maximumTableInputRows = maximumTableInputRows;
        this.tableQuerySystemPrompt = tableQuerySystemPrompt;
        this.tableQueryUserPrompt = tableQueryUserPrompt;
        this.summaryMergePrompt = summaryMergePrompt;
    }

    @JsonPropertyDescription("Maximum number of tokens to pass the AI service at a time")
    public int getMaximumBatchSize() {
        return maximumBatchSize;
    }

    @JsonPropertyDescription("Maximum number of table result rows to pass to the AI when making requests")
    public int getMaximumTableInputRows() {
        return maximumTableInputRows;
    }

    @JsonPropertyDescription("System prompt used when querying the AI about table data")
    public String getTableQuerySystemPrompt() {
        return tableQuerySystemPrompt;
    }

    @JsonPropertyDescription(
            "User prompt template for table queries. Use {{query}} for the user's question " +
            "and {{table}} for the markdown table data")
    public String getTableQueryUserPrompt() {
        return tableQueryUserPrompt;
    }

    @JsonPropertyDescription(
            "Prompt template for merging partial summaries. Use {{a}} and {{b}} " +
            "for the two summaries to merge")
    public String getSummaryMergePrompt() {
        return summaryMergePrompt;
    }

    @Override
    public String toString() {
        return "TableSummaryConfig{" +
               "maximumBatchSize=" + maximumBatchSize +
               ", maximumTableInputRows=" + maximumTableInputRows +
               ", tableQuerySystemPrompt='" + tableQuerySystemPrompt + '\'' +
               ", tableQueryUserPrompt='" + tableQueryUserPrompt + '\'' +
               ", summaryMergePrompt='" + summaryMergePrompt + '\'' +
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
        private String tableQuerySystemPrompt = DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
        private String tableQueryUserPrompt = DEFAULT_TABLE_QUERY_USER_PROMPT;
        private String summaryMergePrompt = DEFAULT_SUMMARY_MERGE_PROMPT;

        private Builder() {
        }

        private Builder(final TableSummaryConfig tableSummaryConfig) {
            maximumBatchSize = tableSummaryConfig.maximumBatchSize;
            maximumTableInputRows = tableSummaryConfig.maximumTableInputRows;
            tableQuerySystemPrompt = tableSummaryConfig.tableQuerySystemPrompt;
            tableQueryUserPrompt = tableSummaryConfig.tableQueryUserPrompt;
            summaryMergePrompt = tableSummaryConfig.summaryMergePrompt;
        }

        public Builder maximumBatchSize(final int maximumBatchSize) {
            this.maximumBatchSize = maximumBatchSize;
            return self();
        }

        public Builder maximumTableInputRows(final int maximumTableInputRows) {
            this.maximumTableInputRows = maximumTableInputRows;
            return self();
        }

        public Builder tableQuerySystemPrompt(final String tableQuerySystemPrompt) {
            this.tableQuerySystemPrompt = tableQuerySystemPrompt;
            return self();
        }

        public Builder tableQueryUserPrompt(final String tableQueryUserPrompt) {
            this.tableQueryUserPrompt = tableQueryUserPrompt;
            return self();
        }

        public Builder summaryMergePrompt(final String summaryMergePrompt) {
            this.summaryMergePrompt = summaryMergePrompt;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TableSummaryConfig build() {
            return new TableSummaryConfig(
                    maximumBatchSize,
                    maximumTableInputRows,
                    tableQuerySystemPrompt,
                    tableQueryUserPrompt,
                    summaryMergePrompt);
        }
    }
}
