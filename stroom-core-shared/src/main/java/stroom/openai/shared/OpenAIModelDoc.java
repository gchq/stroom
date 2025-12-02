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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "Defines the settings required to connect to an OpenAI-compatible API and interact with a model.")
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
        "apiKey",
        "modelId",
        "maxContextWindowTokens"})
@JsonInclude(Include.NON_NULL)
public class OpenAIModelDoc extends AbstractDoc {

    public static final String TYPE = "OpenAIModel";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.OPENAI_MODEL_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private String baseUrl;
    @JsonProperty
    private String apiKey;
    @JsonProperty
    private String modelId;
    @JsonProperty
    private int maxContextWindowTokens;

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
            @JsonProperty("apiKey") final String apiKey,
            @JsonProperty("modelId") final String modelId,
            @JsonProperty("maxContextWindowTokens") final int maxContextWindowTokens) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.maxContextWindowTokens = maxContextWindowTokens;
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

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(final String modelId) {
        this.modelId = modelId;
    }

    public int getMaxContextWindowTokens() {
        return maxContextWindowTokens;
    }

    public void setMaxContextWindowTokens(final int maxContextWindowTokens) {
        this.maxContextWindowTokens = maxContextWindowTokens;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenAIModelDoc)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final OpenAIModelDoc model = (OpenAIModelDoc) o;
        return Objects.equals(description, model.description) &&
               Objects.equals(baseUrl, model.baseUrl) &&
               Objects.equals(apiKey, model.apiKey) &&
               Objects.equals(modelId, model.modelId) &&
               Objects.equals(maxContextWindowTokens, model.maxContextWindowTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                description,
                baseUrl,
                apiKey,
                modelId,
                maxContextWindowTokens);
    }

    @Override
    public String toString() {
        return "OpenAIModel{" +
               "description='" + description + '\'' +
               ", baseUrl='" + baseUrl + '\'' +
               ", modelId='" + apiKey + '\'' +
               ", maxContextWindowTokens=" + maxContextWindowTokens +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDoc.AbstractBuilder<OpenAIModelDoc, OpenAIModelDoc.Builder> {

        private String description;
        private String baseUrl;
        private String apiKey;
        private String modelId;
        private int maxContextWindowTokens;

        private Builder() {
        }

        private Builder(final OpenAIModelDoc openAIModelDoc) {
            super(openAIModelDoc);
            this.description = openAIModelDoc.description;
            this.baseUrl = openAIModelDoc.baseUrl;
            this.apiKey = openAIModelDoc.apiKey;
            this.modelId = openAIModelDoc.modelId;
            this.maxContextWindowTokens = openAIModelDoc.maxContextWindowTokens;
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
                    maxContextWindowTokens);
        }
    }
}
