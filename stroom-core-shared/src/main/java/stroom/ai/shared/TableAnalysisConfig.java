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
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        TableAnalysisConfig.PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS,
        TableAnalysisConfig.PROP_NAME_MAXIMUM_BATCH_SIZE,
        TableAnalysisConfig.PROP_NAME_MAX_PARALLEL_BATCHES,
        TableAnalysisConfig.PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT,
        TableAnalysisConfig.PROP_NAME_TABLE_QUERY_USER_PROMPT,
        TableAnalysisConfig.PROP_NAME_MULTI_SUMMARY_MERGE_PROMPT
})
public class TableAnalysisConfig extends AbstractConfig implements IsStroomConfig {

    public static final int DEFAULT_MAX_TOTAL_ROWS = 10000;
    public static final int DEFAULT_MAX_ROWS_PER_BATCH = 1000;
    public static final int DEFAULT_MAX_PARALLEL_BATCHES = 4;
    public static final String DEFAULT_TABLE_QUERY_SYSTEM_PROMPT = """
                You are a data analysis AI. You will answer user questions \
                using ONLY the markdown-formatted DATA TABLE records provided. \
                If the records do not contain relevant details, say "No relevant information."
            """;
    public static final String DEFAULT_TABLE_QUERY_USER_PROMPT = """
                CONVERSATION CONTEXT:
                {{context}}

                USER QUERY:
                {{query}}

                DATA TABLE:
                {{table}}

                Provide findings relevant only to these records, in a concise structured format. \
                Use the conversation context to understand what has been previously discussed.
            """;
    public static final String DEFAULT_MULTI_SUMMARY_MERGE_PROMPT = """
                Merge the following summaries into a single unified, concise summary. \
                Preserve important details, numerical findings, and remove duplicates.

                {{summaries}}
            """;

    public static final String PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS = "maxTotalRows";
    public static final String PROP_NAME_MAXIMUM_BATCH_SIZE = "maxRowsPerBatch";
    public static final String PROP_NAME_MAX_PARALLEL_BATCHES = "maxParallelBatches";
    public static final String PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT = "tableQuerySystemPrompt";
    public static final String PROP_NAME_TABLE_QUERY_USER_PROMPT = "tableQueryUserPrompt";
    public static final String PROP_NAME_MULTI_SUMMARY_MERGE_PROMPT = "multiSummaryMergePrompt";

    @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS)
    private final int maxTotalRows;
    @JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE)
    private final int maxRowsPerBatch;
    @JsonProperty(PROP_NAME_MAX_PARALLEL_BATCHES)
    private final int maxParallelBatches;
    @JsonProperty(PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT)
    private final String tableQuerySystemPrompt;
    @JsonProperty(PROP_NAME_TABLE_QUERY_USER_PROMPT)
    private final String tableQueryUserPrompt;
    @JsonProperty(PROP_NAME_MULTI_SUMMARY_MERGE_PROMPT)
    private final String multiSummaryMergePrompt;

    public TableAnalysisConfig() {
        maxTotalRows = DEFAULT_MAX_TOTAL_ROWS;
        maxRowsPerBatch = DEFAULT_MAX_ROWS_PER_BATCH;
        maxParallelBatches = DEFAULT_MAX_PARALLEL_BATCHES;
        tableQuerySystemPrompt = DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
        tableQueryUserPrompt = DEFAULT_TABLE_QUERY_USER_PROMPT;
        multiSummaryMergePrompt = DEFAULT_MULTI_SUMMARY_MERGE_PROMPT;
    }

    @JsonCreator
    public TableAnalysisConfig(
            @JsonProperty(PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS) final Integer maxTotalRows,
            @JsonProperty(PROP_NAME_MAXIMUM_BATCH_SIZE) final Integer maxRowsPerBatch,
            @JsonProperty(PROP_NAME_MAX_PARALLEL_BATCHES) final Integer maxParallelBatches,
            @JsonProperty(PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT) final String tableQuerySystemPrompt,
            @JsonProperty(PROP_NAME_TABLE_QUERY_USER_PROMPT) final String tableQueryUserPrompt,
            @JsonProperty(PROP_NAME_MULTI_SUMMARY_MERGE_PROMPT) final String multiSummaryMergePrompt) {
        this.maxTotalRows = Objects.requireNonNullElse(maxTotalRows, DEFAULT_MAX_TOTAL_ROWS);
        this.maxRowsPerBatch = Objects.requireNonNullElse(maxRowsPerBatch, DEFAULT_MAX_ROWS_PER_BATCH);
        this.maxParallelBatches = Objects.requireNonNullElse(maxParallelBatches, DEFAULT_MAX_PARALLEL_BATCHES);
        this.tableQuerySystemPrompt = tableQuerySystemPrompt;
        this.tableQueryUserPrompt = tableQueryUserPrompt;
        this.multiSummaryMergePrompt = multiSummaryMergePrompt;
    }

    @JsonPropertyDescription("Maximum total number of rows to download from the table for AI analysis.")
    public int getMaxTotalRows() {
        return maxTotalRows;
    }

    @JsonPropertyDescription("Maximum number of table rows to include in a single AI prompt batch")
    public int getMaxRowsPerBatch() {
        return maxRowsPerBatch;
    }

    @JsonPropertyDescription("Maximum number of concurrent LLM calls when processing batches in parallel")
    public int getMaxParallelBatches() {
        return maxParallelBatches;
    }

    @JsonPropertyDescription("System prompt used when querying the AI about table data")
    public String getTableQuerySystemPrompt() {
        return tableQuerySystemPrompt;
    }

    @JsonPropertyDescription(
            "User prompt template for table queries. Use {{query}} for the user's question, " +
            "{{table}} for the markdown table data, and {{context}} for prior conversation history")
    public String getTableQueryUserPrompt() {
        return tableQueryUserPrompt;
    }

    @JsonPropertyDescription(
            "Prompt template for merging multiple summaries at once. Use {{summaries}} " +
            "for the numbered list of summaries to merge")
    public String getMultiSummaryMergePrompt() {
        return multiSummaryMergePrompt;
    }

    @Override
    public String toString() {
        return "TableAnalysisConfig{" +
               "maxTotalRows=" + maxTotalRows +
               ", maxRowsPerBatch=" + maxRowsPerBatch +
               ", maxParallelBatches=" + maxParallelBatches +
               ", tableQuerySystemPrompt='" + tableQuerySystemPrompt + '\'' +
               ", tableQueryUserPrompt='" + tableQueryUserPrompt + '\'' +
               ", multiSummaryMergePrompt='" + multiSummaryMergePrompt + '\'' +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<TableAnalysisConfig, TableAnalysisConfig.Builder> {

        private int maxTotalRows = DEFAULT_MAX_TOTAL_ROWS;
        private int maxRowsPerBatch = DEFAULT_MAX_ROWS_PER_BATCH;
        private int maxParallelBatches = DEFAULT_MAX_PARALLEL_BATCHES;
        private String tableQuerySystemPrompt = DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
        private String tableQueryUserPrompt = DEFAULT_TABLE_QUERY_USER_PROMPT;
        private String multiSummaryMergePrompt = DEFAULT_MULTI_SUMMARY_MERGE_PROMPT;

        private Builder() {
        }

        private Builder(final TableAnalysisConfig tableAnalysisConfig) {
            maxTotalRows = tableAnalysisConfig.maxTotalRows;
            maxRowsPerBatch = tableAnalysisConfig.maxRowsPerBatch;
            maxParallelBatches = tableAnalysisConfig.maxParallelBatches;
            tableQuerySystemPrompt = tableAnalysisConfig.tableQuerySystemPrompt;
            tableQueryUserPrompt = tableAnalysisConfig.tableQueryUserPrompt;
            multiSummaryMergePrompt = tableAnalysisConfig.multiSummaryMergePrompt;
        }

        public Builder maxTotalRows(final int maxTotalRows) {
            this.maxTotalRows = maxTotalRows;
            return self();
        }

        public Builder maxRowsPerBatch(final int maxRowsPerBatch) {
            this.maxRowsPerBatch = maxRowsPerBatch;
            return self();
        }

        public Builder maxParallelBatches(final int maxParallelBatches) {
            this.maxParallelBatches = maxParallelBatches;
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

        public Builder multiSummaryMergePrompt(final String multiSummaryMergePrompt) {
            this.multiSummaryMergePrompt = multiSummaryMergePrompt;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TableAnalysisConfig build() {
            return new TableAnalysisConfig(
                    maxTotalRows,
                    maxRowsPerBatch,
                    maxParallelBatches,
                    tableQuerySystemPrompt,
                    tableQueryUserPrompt,
                    multiSummaryMergePrompt);
        }
    }
}
