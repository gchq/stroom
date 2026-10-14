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

package stroom.openai.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@Description(
        "Defines the settings required to connect to an OpenAI-compatible API and interact with a model.\n\n" +
        "May also define one or more HTTP MCP (Model Context Protocol) tool servers that the "
        + "\"Ask Stroom AI\" chat agent can use to call external tools and read external resources "
        + "(for example to query Elasticsearch) while answering a question.")
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "baseUrl",
        "apiKeyName",
        "modelId",
        "maxContextWindowTokens",
        "reasoningEffort",
        "embeddingModelDimensions",
        "httpClientConfiguration",
        "mcpToolServers",
        "toolsEnabled",
        "maxToolDefinitions",
        "showReasoning"
})
@JsonInclude(Include.NON_NULL)
public class OpenAIModelDoc extends AbstractDoc {

    private static final int DEFAULT_MAX_CONTEXT_WINDOW_TOKENS = 0;
    // Lucene can only support 1024 dimensions but Elastic can deal with 4096.
    private static final int DEFAULT_EMBEDDING_MODEL_DIMENSIONS = 1024;

    public static final String TYPE = "OpenAIModel";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.OPENAI_MODEL_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String baseUrl;
    @JsonProperty
    private final String apiKeyName;
    @JsonProperty
    private final String modelId;
    @JsonProperty
    private final int maxContextWindowTokens;
    @JsonProperty
    private final String reasoningEffort;
    @JsonProperty
    private final int embeddingModelDimensions;
    @JsonProperty
    private final HttpClientConfig httpClientConfiguration;
    @JsonProperty
    private final List<McpToolServerConfig> mcpToolServers;
    @JsonProperty
    private final boolean toolsEnabled;
    @JsonProperty
    private final int maxToolDefinitions;
    @JsonProperty
    private final boolean showReasoning;

    @JsonCreator
    public OpenAIModelDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("baseUrl") final String baseUrl,
            @JsonProperty("apiKeyName") final String apiKeyName,
            @JsonProperty("modelId") final String modelId,
            @JsonProperty("maxContextWindowTokens") final Integer maxContextWindowTokens,
            @JsonProperty("reasoningEffort") final String reasoningEffort,
            @JsonProperty("embeddingModelDimensions") final Integer embeddingModelDimensions,
            @JsonProperty("httpClientConfiguration") final HttpClientConfig httpClientConfiguration,
            @JsonProperty("mcpToolServers") final List<McpToolServerConfig> mcpToolServers,
            @JsonProperty("toolsEnabled") final Boolean toolsEnabled,
            @JsonProperty("maxToolDefinitions") final Integer maxToolDefinitions,
            @JsonProperty("showReasoning") final Boolean showReasoning) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.baseUrl = baseUrl;
        this.apiKeyName = apiKeyName;
        this.modelId = modelId;
        this.maxContextWindowTokens =
                Objects.requireNonNullElse(maxContextWindowTokens, DEFAULT_MAX_CONTEXT_WINDOW_TOKENS);
        this.reasoningEffort = reasoningEffort;
        this.embeddingModelDimensions =
                Objects.requireNonNullElse(embeddingModelDimensions, DEFAULT_EMBEDDING_MODEL_DIMENSIONS);
        this.httpClientConfiguration = httpClientConfiguration;
        this.mcpToolServers = mcpToolServers;
        this.toolsEnabled = Objects.requireNonNullElse(toolsEnabled, true);
        this.maxToolDefinitions = Math.max(0, Objects.requireNonNullElse(maxToolDefinitions, 0));
        this.showReasoning = Objects.requireNonNullElse(showReasoning, true);
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMaxContextWindowTokens() {
        return maxContextWindowTokens;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public int getEmbeddingModelDimensions() {
        return embeddingModelDimensions;
    }

    public HttpClientConfig getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    /**
     * @return The configured HTTP MCP tool servers, never null.
     */
    public List<McpToolServerConfig> getMcpToolServers() {
        return NullSafe.list(mcpToolServers);
    }

    /**
     * @return Whether the model may be offered tools (built-in and MCP). Turn off for models that do not
     * support tool calling, e.g. some small local models.
     */
    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    /**
     * @return The most tool definitions to offer the model in one request, or 0 for no limit. Tools past the
     * limit are left out, built-in tools first.
     */
    public int getMaxToolDefinitions() {
        return maxToolDefinitions;
    }

    /**
     * @return Whether the model's reasoning, if it returns any, is shown to users (collapsed, under the answer's
     * activity). On by default.
     */
    public boolean isShowReasoning() {
        return showReasoning;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final OpenAIModelDoc that = (OpenAIModelDoc) o;
        return maxContextWindowTokens == that.maxContextWindowTokens &&
               Objects.equals(description, that.description) &&
               Objects.equals(baseUrl, that.baseUrl) &&
               Objects.equals(apiKeyName, that.apiKeyName) &&
               Objects.equals(modelId, that.modelId) &&
               Objects.equals(reasoningEffort, that.reasoningEffort) &&
               Objects.equals(embeddingModelDimensions, that.embeddingModelDimensions) &&
               Objects.equals(httpClientConfiguration, that.httpClientConfiguration) &&
               Objects.equals(getMcpToolServers(), that.getMcpToolServers()) &&
               toolsEnabled == that.toolsEnabled &&
               maxToolDefinitions == that.maxToolDefinitions &&
               showReasoning == that.showReasoning;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                baseUrl,
                apiKeyName,
                modelId,
                maxContextWindowTokens,
                reasoningEffort,
                embeddingModelDimensions,
                httpClientConfiguration,
                getMcpToolServers(),
                toolsEnabled,
                maxToolDefinitions,
                showReasoning);
    }

    @Override
    public String toString() {
        return "OpenAIModel{" +
               "description='" + description + '\'' +
               ", baseUrl='" + baseUrl + '\'' +
               ", modelId='" + apiKeyName + '\'' +
               ", maxContextWindowTokens=" + maxContextWindowTokens +
               ", reasoningEffort='" + reasoningEffort + '\'' +
               ", embeddingModelDimensions=" + embeddingModelDimensions +
               ", httpClientConfiguration=" + httpClientConfiguration +
               ", mcpToolServers=" + mcpToolServers +
               ", toolsEnabled=" + toolsEnabled +
               ", maxToolDefinitions=" + maxToolDefinitions +
               ", showReasoning=" + showReasoning +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractBuilder<OpenAIModelDoc, Builder> {

        private String description;
        private String baseUrl;
        private String apiKey;
        private String modelId;
        private int maxContextWindowTokens;
        private String reasoningEffort;
        private int embeddingModelDimensions = DEFAULT_EMBEDDING_MODEL_DIMENSIONS;
        private HttpClientConfig httpClientConfiguration;
        private List<McpToolServerConfig> mcpToolServers;
        private boolean toolsEnabled = true;
        private int maxToolDefinitions;
        private boolean showReasoning = true;

        private Builder() {
        }

        private Builder(final OpenAIModelDoc openAIModelDoc) {
            super(openAIModelDoc);
            this.description = openAIModelDoc.description;
            this.baseUrl = openAIModelDoc.baseUrl;
            this.apiKey = openAIModelDoc.apiKeyName;
            this.modelId = openAIModelDoc.modelId;
            this.maxContextWindowTokens = openAIModelDoc.maxContextWindowTokens;
            this.reasoningEffort = openAIModelDoc.reasoningEffort;
            this.embeddingModelDimensions = openAIModelDoc.embeddingModelDimensions;
            this.httpClientConfiguration = openAIModelDoc.httpClientConfiguration;
            this.mcpToolServers = openAIModelDoc.mcpToolServers;
            this.toolsEnabled = openAIModelDoc.toolsEnabled;
            this.maxToolDefinitions = openAIModelDoc.maxToolDefinitions;
            this.showReasoning = openAIModelDoc.showReasoning;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder baseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
            return self();
        }

        public Builder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return self();
        }

        public Builder modelId(final String modelId) {
            this.modelId = modelId;
            return self();
        }

        public Builder maxContextWindowTokens(final int maxContextWindowTokens) {
            this.maxContextWindowTokens = maxContextWindowTokens;
            return self();
        }

        public Builder reasoningEffort(final String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return self();
        }

        public Builder embeddingModelDimensions(final int embeddingModelDimensions) {
            this.embeddingModelDimensions = embeddingModelDimensions;
            return self();
        }

        public Builder httpClientConfiguration(final HttpClientConfig httpClientConfiguration) {
            this.httpClientConfiguration = httpClientConfiguration;
            return self();
        }

        public Builder mcpToolServers(final List<McpToolServerConfig> mcpToolServers) {
            this.mcpToolServers = mcpToolServers;
            return self();
        }

        public Builder toolsEnabled(final boolean toolsEnabled) {
            this.toolsEnabled = toolsEnabled;
            return self();
        }

        public Builder maxToolDefinitions(final int maxToolDefinitions) {
            this.maxToolDefinitions = maxToolDefinitions;
            return self();
        }

        public Builder showReasoning(final boolean showReasoning) {
            this.showReasoning = showReasoning;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public OpenAIModelDoc build() {
            return new OpenAIModelDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    baseUrl,
                    apiKey,
                    modelId,
                    maxContextWindowTokens,
                    reasoningEffort,
                    embeddingModelDimensions,
                    httpClientConfiguration,
                    mcpToolServers,
                    toolsEnabled,
                    maxToolDefinitions,
                    showReasoning);
        }
    }
}
