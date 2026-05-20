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

import stroom.docref.DocRef;
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
public class AskStroomAIConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_MODEL_REF = "modelRef";
    public static final String PROP_NAME_TABLE_SUMMARY = "tableSummary";

    @JsonProperty(PROP_NAME_MODEL_REF)
    private final DocRef modelRef;
    @JsonProperty(PROP_NAME_TABLE_SUMMARY)
    private final TableSummaryConfig tableSummary;

    public AskStroomAIConfig() {
        modelRef = null;
        tableSummary = new TableSummaryConfig();
    }

    @JsonCreator
    public AskStroomAIConfig(@JsonProperty(PROP_NAME_MODEL_REF) final DocRef modelRef,
                             @JsonProperty(PROP_NAME_TABLE_SUMMARY) final TableSummaryConfig tableSummary) {
        this.modelRef = modelRef;
        this.tableSummary = tableSummary;
    }

    @JsonPropertyDescription("The model to use.")
    public DocRef getModelRef() {
        return modelRef;
    }

    @JsonPropertyDescription("Settings to use for table summarisation.")
    public TableSummaryConfig getTableSummary() {
        return tableSummary;
    }

    @Override
    public String toString() {
        return "AskStroomAIConfig{" +
               "modelRef='" + modelRef + "'" +
               ", tableSummaryConfig=" + tableSummary +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<AskStroomAIConfig, AskStroomAIConfig.Builder> {

        private DocRef modelRef;
        private TableSummaryConfig tableSummaryConfig;

        private Builder() {
            modelRef = null;
            tableSummaryConfig = new TableSummaryConfig();
        }

        private Builder(final AskStroomAIConfig askStroomAIConfig) {
            modelRef = askStroomAIConfig.modelRef;
            tableSummaryConfig = askStroomAIConfig.tableSummary;
        }

        public Builder modelRef(final DocRef modelRef) {
            this.modelRef = modelRef;
            return self();
        }

        public Builder tableSummaryConfig(final TableSummaryConfig tableSummaryConfig) {
            this.tableSummaryConfig = tableSummaryConfig;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public AskStroomAIConfig build() {
            return new AskStroomAIConfig(modelRef, tableSummaryConfig);
        }
    }
}
