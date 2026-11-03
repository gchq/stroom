/*
 * Copyright 2021 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Description(
        "Defines an index that exists within an Elasticsearch cluster.\n" +
        "This Document is used in the configuration of the {{< pipe-elm \"ElasticIndexingFilter\" >}} " +
        "pipeline element.\n" +
        "\n" +
        "{{% see-also %}}" +
        "[Elasticsearch]({{< relref \"docs/user-guide/indexing/elasticsearch\" >}})" +
        "{{% /see-also %}}")
@JsonPropertyOrder({
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "clusterRef",
        "indexName",
        "searchSlices",
        "searchScrollSize",
        "fields",
        "timeField",
        "vectorGenerationModelRef",
        "rerankModelRef",
        "rerankTextFieldSuffix",
        "rerankScoreFieldSuffix",
        "rerankScoreMinimum",
        "defaultExtractionPipeline",
        "fieldSettings",
        "retentionExpression"
})
@JsonInclude(Include.NON_NULL)
public class ElasticIndexDoc extends AbstractDoc {

    public static final int DEFAULT_SEARCH_SLICES = 1;
    public static final int DEFAULT_SEARCH_SCROLL_SIZE = 1000;
    public static final String TYPE = "ElasticIndex";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ELASTIC_INDEX_DOCUMENT_TYPE;
    private static final String DEFAULT_TIME_FIELD = "@timestamp";
    /// The rerank text field suffix applied to legacy docs that did not set one
    public static final String LEGACY_DEFAULT_RERANK_TEXT_FIELD_SUFFIX = ".text";
    /// The rerank score field suffix applied to legacy docs that did not set one
    public static final String LEGACY_DEFAULT_RERANK_SCORE_FIELD_SUFFIX = ".score";

    /**
     * Reference to the `ElasticCluster` containing common Elasticsearch cluster connection properties
     */
    @JsonProperty
    private final DocRef clusterRef;

    @JsonProperty
    private final String description;

    /**
     * Name or pattern of the Elasticsearch index(es) to query
     */
    @JsonProperty
    private final String indexName;

    /**
     * Number of slices to query concurrently when searching
     */
    @JsonProperty
    private final Integer searchSlices;

    /**
     * Number of documents to retrieve at a time in each search scroll batch request
     */
    @JsonProperty
    private final Integer searchScrollSize;

    /// Legacy reference to the `OpenAIModel` used to generate vector embeddings from search query expressions.
    /// Replaced by per-field `dense_vector` settings in [#fieldSettings], to which it is migrated on read.
    @Deprecated
    @JsonProperty
    private final DocRef vectorGenerationModelRef;

    /**
     * Reference to the `OpenAIModel` used for reranking vector search results
     */
    @Deprecated
    @JsonProperty
    private final DocRef rerankModelRef;

    /**
     * Suffix used to identify the original text equivalent of dense_vector fields.
     * This by convention allows us to determine the name of the text field by stripping the trailing .suffix from the
     * vector field and replacing it with the specified suffix.
     * For example, with a vector field `Content.vector` and a text field suffix of `.text`, the source text used for
     * reranking will be `Content.text`.
     */
    @Deprecated
    @JsonProperty
    private final String rerankTextFieldSuffix;

    /**
     * Suffix appended to the base field name (after removing the vector field name .suffix), to name the new field
     * containing the rerank score.
     * For example, with a vector field `Content.vector`, the rerank score will be stored in new field `Content.score`.
     */
    @Deprecated
    @JsonProperty
    private final String rerankScoreFieldSuffix;

    /**
     * Minimum rerank score for documents to be included in search hits
     */
    @Deprecated
    @JsonProperty
    private final Float rerankScoreMinimum;

    /**
     * Array of fields, populated at query time
     */
    @JsonProperty
    private final List<ElasticIndexField> fields;
    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final DocRef defaultExtractionPipeline;

    /// User-defined settings for individual fields, including virtual fields
    @JsonProperty
    private final List<ElasticIndexFieldSettings> fieldSettings;

    @JsonCreator
    public ElasticIndexDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("clusterRef") final DocRef clusterRef,
            @JsonProperty("indexName") final String indexName,
            @JsonProperty("searchSlices") final Integer searchSlices,
            @JsonProperty("searchScrollSize") final Integer searchScrollSize,
            @JsonProperty("fields") final List<ElasticIndexField> fields,
            @JsonProperty("timeField") final String timeField,
            @JsonProperty("vectorGenerationModelRef") final DocRef vectorGenerationModelRef,
            @JsonProperty("rerankModelRef") final DocRef rerankModelRef,
            @JsonProperty("rerankTextFieldSuffix") final String rerankTextFieldSuffix,
            @JsonProperty("rerankScoreFieldSuffix") final String rerankScoreFieldSuffix,
            @JsonProperty("rerankScoreMinimum") final Float rerankScoreMinimum,
            @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline,
            @JsonProperty("fieldSettings") final List<ElasticIndexFieldSettings> fieldSettings) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.clusterRef = clusterRef;
        if (NullSafe.isBlankString(indexName)) {
            this.indexName = null;
        } else {
            this.indexName = indexName;
        }
        this.searchSlices = Objects.requireNonNullElse(searchSlices, DEFAULT_SEARCH_SLICES);
        this.searchScrollSize = Objects.requireNonNullElse(searchScrollSize, DEFAULT_SEARCH_SCROLL_SIZE);
        this.fields = fields;
        if (NullSafe.isEmptyString(timeField)) {
            this.timeField = DEFAULT_TIME_FIELD;
        } else {
            this.timeField = timeField;
        }
        // Legacy vector settings are held exactly as given, so that they drop out of the doc once migrated
        this.vectorGenerationModelRef = vectorGenerationModelRef;
        this.rerankModelRef = rerankModelRef;
        this.rerankTextFieldSuffix = NullSafe.isEmptyString(rerankTextFieldSuffix)
                ? null
                : rerankTextFieldSuffix;
        this.rerankScoreFieldSuffix = NullSafe.isEmptyString(rerankScoreFieldSuffix)
                ? null
                : rerankScoreFieldSuffix;
        this.rerankScoreMinimum = rerankScoreMinimum;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
        this.fieldSettings = NullSafe.isEmptyCollection(fieldSettings)
                ? null
                : fieldSettings;
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

    public DocRef getClusterRef() {
        return clusterRef;
    }

    public String getIndexName() {
        return indexName;
    }

    public Integer getSearchSlices() {
        return searchSlices;
    }

    public Integer getSearchScrollSize() {
        return searchScrollSize;
    }

    public List<ElasticIndexField> getFields() {
        return fields;
    }

    public String getTimeField() {
        return timeField;
    }

    @Deprecated
    public DocRef getVectorGenerationModelRef() {
        return vectorGenerationModelRef;
    }

    @Deprecated
    public DocRef getRerankModelRef() {
        return rerankModelRef;
    }

    @Deprecated
    public String getRerankTextFieldSuffix() {
        return rerankTextFieldSuffix;
    }

    @Deprecated
    public String getRerankScoreFieldSuffix() {
        return rerankScoreFieldSuffix;
    }

    @Deprecated
    public Float getRerankScoreMinimum() {
        return rerankScoreMinimum;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
    }

    /// @return the user-defined field settings, or an empty list if there are none
    public List<ElasticIndexFieldSettings> getFieldSettings() {
        return NullSafe.list(fieldSettings);
    }

    /// @param fieldName the full name of a field
    /// @return the settings for the named field, or `null` if it has none
    @JsonIgnore
    public ElasticIndexFieldSettings findFieldSettings(final String fieldName) {
        if (fieldName == null || fieldSettings == null) {
            return null;
        }
        for (final ElasticIndexFieldSettings settings : fieldSettings) {
            if (fieldName.equals(settings.getFieldName())) {
                return settings;
            }
        }
        return null;
    }

    /// @param fieldName the full name of a field
    /// @return `false` if the field has been disabled, otherwise `true`
    @JsonIgnore
    public boolean isFieldEnabled(final String fieldName) {
        final ElasticIndexFieldSettings settings = findFieldSettings(fieldName);
        return settings == null || settings.isEnabled();
    }

    /// @return `true` if the doc still holds any of the legacy, doc-level vector settings
    @JsonIgnore
    public boolean hasLegacyVectorSettings() {
        return vectorGenerationModelRef != null ||
               rerankModelRef != null ||
               rerankTextFieldSuffix != null ||
               rerankScoreFieldSuffix != null ||
               rerankScoreMinimum != null;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ElasticIndexDoc that = (ElasticIndexDoc) o;
        return Objects.equals(clusterRef, that.clusterRef) &&
               Objects.equals(description, that.description) &&
               Objects.equals(indexName, that.indexName) &&
               Objects.equals(searchSlices, that.searchSlices) &&
               Objects.equals(searchScrollSize, that.searchScrollSize) &&
               Objects.equals(vectorGenerationModelRef, that.vectorGenerationModelRef) &&
               Objects.equals(rerankModelRef, that.rerankModelRef) &&
               Objects.equals(rerankTextFieldSuffix, that.rerankTextFieldSuffix) &&
               Objects.equals(rerankScoreFieldSuffix, that.rerankScoreFieldSuffix) &&
               Objects.equals(rerankScoreMinimum, that.rerankScoreMinimum) &&
               Objects.equals(fields, that.fields) &&
               Objects.equals(timeField, that.timeField) &&
               Objects.equals(defaultExtractionPipeline, that.defaultExtractionPipeline) &&
               Objects.equals(fieldSettings, that.fieldSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                clusterRef,
                description,
                indexName,
                searchSlices,
                searchScrollSize,
                vectorGenerationModelRef,
                rerankModelRef,
                rerankTextFieldSuffix,
                rerankScoreFieldSuffix,
                rerankScoreMinimum,
                fields,
                timeField,
                defaultExtractionPipeline,
                fieldSettings);
    }

    @Override
    public String toString() {
        return "ElasticIndexDoc{" +
               "clusterRef=" + clusterRef +
               ", description='" + description + '\'' +
               ", indexName='" + indexName + '\'' +
               ", searchSlices=" + searchSlices +
               ", searchScrollSize=" + searchScrollSize +
               ", vectorGenerationModelRef=" + vectorGenerationModelRef +
               ", rerankModelRef=" + rerankModelRef +
               ", rerankTextFieldSuffix='" + rerankTextFieldSuffix + '\'' +
               ", rerankScoreFieldSuffix='" + rerankScoreFieldSuffix + '\'' +
               ", rerankScoreMinimum=" + rerankScoreMinimum +
               ", fields=" + fields +
               ", timeField='" + timeField + '\'' +
               ", defaultExtractionPipeline=" + defaultExtractionPipeline +
               ", fieldSettings=" + fieldSettings +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractBuilder<ElasticIndexDoc, Builder> {

        private DocRef clusterRef;
        private String description;
        private String indexName;
        private Integer searchSlices = ElasticIndexDoc.DEFAULT_SEARCH_SLICES;
        private Integer searchScrollSize = ElasticIndexDoc.DEFAULT_SEARCH_SCROLL_SIZE;
        private List<ElasticIndexField> fields = new ArrayList<>();
        private String timeField = ElasticIndexDoc.DEFAULT_TIME_FIELD;
        private DocRef defaultExtractionPipeline;
        private DocRef vectorGenerationModelRef;
        private DocRef rerankModelRef;
        private String rerankTextFieldSuffix;
        private String rerankScoreFieldSuffix;
        private Float rerankScoreMinimum;
        private List<ElasticIndexFieldSettings> fieldSettings;

        private Builder() {
        }

        private Builder(final ElasticIndexDoc elasticIndexDoc) {
            super(elasticIndexDoc);
            this.clusterRef = elasticIndexDoc.clusterRef;
            this.description = elasticIndexDoc.description;
            this.indexName = elasticIndexDoc.indexName;
            this.searchSlices = elasticIndexDoc.searchSlices;
            this.searchScrollSize = elasticIndexDoc.searchScrollSize;
            this.fields = elasticIndexDoc.fields;
            this.timeField = elasticIndexDoc.timeField;
            this.defaultExtractionPipeline = elasticIndexDoc.defaultExtractionPipeline;
            this.vectorGenerationModelRef = elasticIndexDoc.vectorGenerationModelRef;
            this.rerankModelRef = elasticIndexDoc.rerankModelRef;
            this.rerankTextFieldSuffix = elasticIndexDoc.rerankTextFieldSuffix;
            this.rerankScoreFieldSuffix = elasticIndexDoc.rerankScoreFieldSuffix;
            this.rerankScoreMinimum = elasticIndexDoc.rerankScoreMinimum;
            this.fieldSettings = elasticIndexDoc.fieldSettings;
        }

        public Builder clusterRef(final DocRef clusterRef) {
            this.clusterRef = clusterRef;
            return self();
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder indexName(final String indexName) {
            if (indexName == null || indexName.trim().isEmpty()) {
                this.indexName = null;
            } else {
                this.indexName = indexName;
            }
            return self();
        }

        public Builder searchSlices(final Integer searchSlices) {
            this.searchSlices = searchSlices;
            return self();
        }

        public Builder searchScrollSize(final Integer searchScrollSize) {
            this.searchScrollSize = searchScrollSize;
            return self();
        }

        public Builder fields(final List<ElasticIndexField> fields) {
            this.fields = fields;
            return self();
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return self();
        }

        public Builder defaultExtractionPipeline(final DocRef defaultExtractionPipeline) {
            this.defaultExtractionPipeline = defaultExtractionPipeline;
            return self();
        }

        @Deprecated
        public Builder vectorGenerationModelRef(final DocRef vectorGenerationModelRef) {
            this.vectorGenerationModelRef = vectorGenerationModelRef;
            return self();
        }

        @Deprecated
        public Builder rerankModelRef(final DocRef rerankModelRef) {
            this.rerankModelRef = rerankModelRef;
            return self();
        }

        @Deprecated
        public Builder rerankTextFieldSuffix(final String rerankTextFieldSuffix) {
            this.rerankTextFieldSuffix = rerankTextFieldSuffix;
            return self();
        }

        @Deprecated
        public Builder rerankScoreFieldSuffix(final String rerankScoreFieldSuffix) {
            this.rerankScoreFieldSuffix = rerankScoreFieldSuffix;
            return self();
        }

        @Deprecated
        public Builder rerankScoreMinimum(final Float rerankScoreMinimum) {
            this.rerankScoreMinimum = rerankScoreMinimum;
            return self();
        }

        /// @param fieldSettings the user-defined field settings
        /// @return this builder
        public Builder fieldSettings(final List<ElasticIndexFieldSettings> fieldSettings) {
            this.fieldSettings = fieldSettings;
            return self();
        }

        /// Removes all legacy, doc-level vector settings.
        ///
        /// @return this builder
        public Builder clearLegacyVectorSettings() {
            this.vectorGenerationModelRef = null;
            this.rerankModelRef = null;
            this.rerankTextFieldSuffix = null;
            this.rerankScoreFieldSuffix = null;
            this.rerankScoreMinimum = null;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ElasticIndexDoc build() {
            return new ElasticIndexDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    clusterRef,
                    indexName,
                    searchSlices,
                    searchScrollSize,
                    fields,
                    timeField,
                    vectorGenerationModelRef,
                    rerankModelRef,
                    rerankTextFieldSuffix,
                    rerankScoreFieldSuffix,
                    rerankScoreMinimum,
                    defaultExtractionPipeline,
                    fieldSettings);
        }
    }
}
