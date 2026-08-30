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
import stroom.docref.HasDisplayValue;
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
        AskStroomAiConfig.PROP_NAME_MODEL_REF,
        AskStroomAiConfig.PROP_NAME_DOCK_TYPE,
        AskStroomAiConfig.PROP_NAME_DOCK_LOCATION,
        AskStroomAiConfig.PROP_NAME_DOCK_SIZE,
        AskStroomAiConfig.PROP_NAME_TABLE_SUMMARY,
        AskStroomAiConfig.PROP_NAME_CHAT_SYSTEM_PROMPT,
        AskStroomAiConfig.PROP_NAME_HISTORY_SUMMARY_PROMPT,
        AskStroomAiConfig.PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES,
        AskStroomAiConfig.PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS,
        AskStroomAiConfig.PROP_NAME_ENABLE_DEBUG_DETAIL
})
public class AskStroomAiConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_MODEL_REF = "modelRef";
    public static final String PROP_NAME_DOCK_TYPE = "dockType";
    public static final String PROP_NAME_DOCK_LOCATION = "dockLocation";
    public static final String PROP_NAME_DOCK_SIZE = "dockSize";
    public static final String PROP_NAME_TABLE_SUMMARY = "tableAnalysis";
    public static final String PROP_NAME_CHAT_SYSTEM_PROMPT = "chatSystemPrompt";
    public static final String PROP_NAME_HISTORY_SUMMARY_PROMPT = "historySummaryPrompt";
    public static final String PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES = "maxHistorySafetyCapMessages";
    public static final String PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS = "attachmentDownloadTimeoutMs";
    public static final String PROP_NAME_ENABLE_DEBUG_DETAIL = "enableDebugDetail";

    public static final DockType DEFAULT_DOCK_TYPE = DockType.DIALOG;
    public static final DockLocation DEFAULT_DOCK_LOCATION = DockLocation.RIGHT;
    public static final Integer DEFAULT_DOCK_SIZE = 300;
    public static final String DEFAULT_CHAT_SYSTEM_PROMPT = """
            You are a helpful data analysis assistant within the Stroom data platform. \
            When table data is attached to the conversation, it appears as markdown \
            tables prefixed with [Attached Table: ...] labels identifying the source. \
            Use data from all relevant attached tables to answer the user's questions. \
            If multiple tables are present, cite the source table name in your answer. \
            If you don't have enough information, say so.\
            """;
    public static final String DEFAULT_HISTORY_SUMMARY_PROMPT = """
            Summarise the following conversation history in 2-3 concise sentences. \
            Preserve key facts, decisions, data findings, and any table names or \
            sources referenced. Do not include greetings or filler.\
            """;
    public static final int DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES = 200;
    public static final long DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS = 60_000L;
    public static final boolean DEFAULT_ENABLE_DEBUG_DETAIL = true;

    @JsonProperty(PROP_NAME_MODEL_REF)
    private final DocRef modelRef;
    @JsonProperty(PROP_NAME_DOCK_TYPE)
    private final DockType dockType;
    @JsonProperty(PROP_NAME_DOCK_LOCATION)
    private final DockLocation dockLocation;
    @JsonProperty(PROP_NAME_DOCK_SIZE)
    private final Integer dockSize;
    @JsonProperty(PROP_NAME_TABLE_SUMMARY)
    private final TableAnalysisConfig tableAnalysis;
    @JsonProperty(PROP_NAME_CHAT_SYSTEM_PROMPT)
    private final String chatSystemPrompt;
    @JsonProperty(PROP_NAME_HISTORY_SUMMARY_PROMPT)
    private final String historySummaryPrompt;
    @JsonProperty(PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES)
    private final int maxHistorySafetyCapMessages;
    @JsonProperty(PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS)
    private final long attachmentDownloadTimeoutMs;
    @JsonProperty(PROP_NAME_ENABLE_DEBUG_DETAIL)
    private final boolean enableDebugDetail;

    public AskStroomAiConfig() {
        modelRef = null;
        dockType = DEFAULT_DOCK_TYPE;
        dockLocation = DEFAULT_DOCK_LOCATION;
        dockSize = DEFAULT_DOCK_SIZE;
        tableAnalysis = new TableAnalysisConfig();
        chatSystemPrompt = DEFAULT_CHAT_SYSTEM_PROMPT;
        historySummaryPrompt = DEFAULT_HISTORY_SUMMARY_PROMPT;
        maxHistorySafetyCapMessages = DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES;
        attachmentDownloadTimeoutMs = DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS;
        enableDebugDetail = DEFAULT_ENABLE_DEBUG_DETAIL;
    }

    @JsonCreator
    public AskStroomAiConfig(
            @JsonProperty(PROP_NAME_MODEL_REF) final DocRef modelRef,
            @JsonProperty(PROP_NAME_DOCK_TYPE) final DockType dockType,
            @JsonProperty(PROP_NAME_DOCK_LOCATION) final DockLocation dockLocation,
            @JsonProperty(PROP_NAME_DOCK_SIZE) final Integer dockSize,
            @JsonProperty(PROP_NAME_TABLE_SUMMARY) final TableAnalysisConfig tableAnalysis,
            @JsonProperty(PROP_NAME_CHAT_SYSTEM_PROMPT) final String chatSystemPrompt,
            @JsonProperty(PROP_NAME_HISTORY_SUMMARY_PROMPT) final String historySummaryPrompt,
            @JsonProperty(PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES) final Integer maxHistorySafetyCapMessages,
            @JsonProperty(PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS) final Long attachmentDownloadTimeoutMs,
            @JsonProperty(PROP_NAME_ENABLE_DEBUG_DETAIL) final Boolean enableDebugDetail) {
        this.modelRef = modelRef;
        this.dockType = dockType;
        this.dockLocation = dockLocation;
        this.dockSize = dockSize;
        this.tableAnalysis = tableAnalysis;
        this.chatSystemPrompt = chatSystemPrompt;
        this.historySummaryPrompt = historySummaryPrompt;
        this.maxHistorySafetyCapMessages = Objects.requireNonNullElse(maxHistorySafetyCapMessages,
                DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES);
        this.attachmentDownloadTimeoutMs = Objects.requireNonNullElse(attachmentDownloadTimeoutMs,
                DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS);
        this.enableDebugDetail = Objects.requireNonNullElse(enableDebugDetail,
                DEFAULT_ENABLE_DEBUG_DETAIL);
    }

    @JsonPropertyDescription("The model to use.")
    public DocRef getModelRef() {
        return modelRef;
    }

    @JsonPropertyDescription("AI panel dock type.")
    public DockType getDockType() {
        return dockType;
    }

    @JsonPropertyDescription("AI panel dock location.")
    public DockLocation getDockLocation() {
        return dockLocation;
    }

    @JsonPropertyDescription("AI panel dock size in pixels.")
    public Integer getDockSize() {
        return dockSize;
    }

    @JsonPropertyDescription("Settings to use for table summarisation.")
    public TableAnalysisConfig getTableAnalysis() {
        return tableAnalysis;
    }

    @JsonPropertyDescription("System prompt used for the AI chat assistant.")
    public String getChatSystemPrompt() {
        return chatSystemPrompt;
    }

    @JsonPropertyDescription("System prompt used to summarise older conversation history when "
            + "progressive trimming is needed to fit the model's context window.")
    public String getHistorySummaryPrompt() {
        return historySummaryPrompt;
    }

    @JsonPropertyDescription("Safety cap on the maximum number of history messages loaded from "
            + "the database. The actual context boundary is determined by the model's context "
            + "window via progressive trimming.")
    public int getMaxHistorySafetyCapMessages() {
        return maxHistorySafetyCapMessages;
    }

    @JsonPropertyDescription("Timeout in milliseconds when waiting for attachment downloads to complete.")
    public long getAttachmentDownloadTimeoutMs() {
        return attachmentDownloadTimeoutMs;
    }

    @JsonPropertyDescription("When enabled, the full LLM exchange (prompts sent and responses received) "
            + "is stored and displayed as an expandable detail section beneath each AI response.")
    public boolean isEnableDebugDetail() {
        return enableDebugDetail;
    }

    @Override
    public String toString() {
        return "AskStroomAIConfig{" +
               "modelRef='" + modelRef + "'" +
               ", dockType='" + dockType + "'" +
               ", dockLocation='" + dockLocation + "'" +
               ", dockSize=" + dockSize +
               ", tableAnalysisConfig=" + tableAnalysis +
               ", chatSystemPrompt='" + chatSystemPrompt + "'" +
               ", historySummaryPrompt='" + historySummaryPrompt + "'" +
               ", maxHistorySafetyCapMessages=" + maxHistorySafetyCapMessages +
               ", attachmentDownloadTimeoutMs=" + attachmentDownloadTimeoutMs +
               ", enableDebugDetail=" + enableDebugDetail +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<AskStroomAiConfig, AskStroomAiConfig.Builder> {

        private DocRef modelRef;
        private DockType dockType;
        private DockLocation dockLocation;
        private Integer dockSize;
        private TableAnalysisConfig tableAnalysisConfig;
        private String chatSystemPrompt;
        private String historySummaryPrompt;
        private int maxHistorySafetyCapMessages;
        private long attachmentDownloadTimeoutMs;
        private boolean enableDebugDetail;

        private Builder() {
            modelRef = null;
            dockType = DEFAULT_DOCK_TYPE;
            dockLocation = DEFAULT_DOCK_LOCATION;
            dockSize = DEFAULT_DOCK_SIZE;
            tableAnalysisConfig = new TableAnalysisConfig();
            chatSystemPrompt = DEFAULT_CHAT_SYSTEM_PROMPT;
            historySummaryPrompt = DEFAULT_HISTORY_SUMMARY_PROMPT;
            maxHistorySafetyCapMessages = DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES;
            attachmentDownloadTimeoutMs = DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS;
            enableDebugDetail = DEFAULT_ENABLE_DEBUG_DETAIL;
        }

        private Builder(final AskStroomAiConfig askStroomAiConfig) {
            modelRef = askStroomAiConfig.modelRef;
            dockType = askStroomAiConfig.dockType;
            dockLocation = askStroomAiConfig.dockLocation;
            dockSize = askStroomAiConfig.dockSize;
            tableAnalysisConfig = askStroomAiConfig.tableAnalysis;
            chatSystemPrompt = askStroomAiConfig.chatSystemPrompt;
            historySummaryPrompt = askStroomAiConfig.historySummaryPrompt;
            maxHistorySafetyCapMessages = askStroomAiConfig.maxHistorySafetyCapMessages;
            attachmentDownloadTimeoutMs = askStroomAiConfig.attachmentDownloadTimeoutMs;
            enableDebugDetail = askStroomAiConfig.enableDebugDetail;
        }

        public Builder modelRef(final DocRef modelRef) {
            this.modelRef = modelRef;
            return self();
        }

        public Builder dockType(final DockType dockType) {
            this.dockType = dockType;
            return self();
        }

        public Builder dockLocation(final DockLocation dockLocation) {
            this.dockLocation = dockLocation;
            return self();
        }

        public Builder dockSize(final Integer dockSize) {
            this.dockSize = dockSize;
            return self();
        }

        public Builder tableAnalysisConfig(final TableAnalysisConfig tableAnalysisConfig) {
            this.tableAnalysisConfig = tableAnalysisConfig;
            return self();
        }

        public Builder chatSystemPrompt(final String chatSystemPrompt) {
            this.chatSystemPrompt = chatSystemPrompt;
            return self();
        }

        public Builder historySummaryPrompt(final String historySummaryPrompt) {
            this.historySummaryPrompt = historySummaryPrompt;
            return self();
        }

        public Builder maxHistorySafetyCapMessages(final int maxHistorySafetyCapMessages) {
            this.maxHistorySafetyCapMessages = maxHistorySafetyCapMessages;
            return self();
        }

        public Builder attachmentDownloadTimeoutMs(final long attachmentDownloadTimeoutMs) {
            this.attachmentDownloadTimeoutMs = attachmentDownloadTimeoutMs;
            return self();
        }

        public Builder enableDebugDetail(final boolean enableDebugDetail) {
            this.enableDebugDetail = enableDebugDetail;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public AskStroomAiConfig build() {
            return new AskStroomAiConfig(
                    modelRef,
                    dockType, dockLocation, dockSize,
                    tableAnalysisConfig, chatSystemPrompt,
                    historySummaryPrompt, maxHistorySafetyCapMessages, attachmentDownloadTimeoutMs,
                    enableDebugDetail);
        }
    }

    public enum DockType implements HasDisplayValue {
        DIALOG("Dialog"),
        TAB("Tab"),
        FLOAT("Float"),
        DOCK("Dock");

        private final String displayValue;

        DockType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public enum DockLocation implements HasDisplayValue {
        TOP("Top"),
        LEFT("Left"),
        BOTTOM("Bottom"),
        RIGHT("Right");

        private final String displayValue;

        DockLocation(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
