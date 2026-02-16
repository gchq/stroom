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

package stroom.search.elastic.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        "rerankScoreMinimum",
        "defaultExtractionPipeline",
        "retentionExpression"
})
@JsonInclude(Include.NON_NULL)
public class ElasticIndexDoc extends AbstractDoc {

    public static final int DEFAULT_SEARCH_SLICES = 1;
    public static final int DEFAULT_SEARCH_SCROLL_SIZE = 1000;
    public static final String TYPE = "ElasticIndex";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ELASTIC_INDEX_DOCUMENT_TYPE;
    private static final String DEFAULT_TIME_FIELD = "@timestamp";
    private static final String DEFAULT_TEXT_FIELD_SUFFIX = ".text";
    private static final Float DEFAULT_RERANK_SCORE_MINIMUM = 0.8f;

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

    /**
     * Reference to the `OpenAIModel` used to generate vector embeddings from search query expressions
     */
    @JsonProperty
    private final DocRef vectorGenerationModelRef;

    /**
     * Reference to the `OpenAIModel` used for reranking vector search results
     */
    @JsonProperty
    private final DocRef rerankModelRef;

    /**
     * Suffix used to identify the original text equivalent of dense_vector fields.
     * This by convention allows us to determine the name of the text field by stripping the trailing .suffix from the
     * vector field and replacing it with the specified suffix.
     * Example: `.text`
     */
    @JsonProperty
    private final String rerankTextFieldSuffix;

    /**
     * Minimum rerank score for documents to be included in search hits
     */
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
            @JsonProperty("rerankScoreMinimum") final Float rerankScoreMinimum,
            @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.clusterRef = clusterRef;
        if (NullSafe.isBlankString(indexName)) {
            this.indexName = null;
        } else {
            this.indexName = indexName;
        }
        this.searchSlices = NullSafe.requireNonNullElse(searchSlices, DEFAULT_SEARCH_SLICES);
        this.searchScrollSize = NullSafe.requireNonNullElse(searchScrollSize, DEFAULT_SEARCH_SCROLL_SIZE);
        this.fields = fields;
        if (NullSafe.isEmptyString(timeField)) {
            this.timeField = DEFAULT_TIME_FIELD;
        } else {
            this.timeField = timeField;
        }
        this.vectorGenerationModelRef = vectorGenerationModelRef;
        this.rerankModelRef = rerankModelRef;
        if (NullSafe.isEmptyString(rerankTextFieldSuffix)) {
            this.rerankTextFieldSuffix = DEFAULT_TEXT_FIELD_SUFFIX;
        } else {
            this.rerankTextFieldSuffix = rerankTextFieldSuffix;
        }
        this.rerankScoreMinimum = NullSafe.requireNonNullElse(rerankScoreMinimum, DEFAULT_RERANK_SCORE_MINIMUM);
        this.defaultExtractionPipeline = defaultExtractionPipeline;
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

    public DocRef getVectorGenerationModelRef() {
        return vectorGenerationModelRef;
    }

    public DocRef getRerankModelRef() {
        return rerankModelRef;
    }

    public String getRerankTextFieldSuffix() {
        return rerankTextFieldSuffix;
    }

    public Float getRerankScoreMinimum() {
        return rerankScoreMinimum;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
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
               Objects.equals(rerankScoreMinimum, that.rerankScoreMinimum) &&
               Objects.equals(fields, that.fields) &&
               Objects.equals(timeField, that.timeField) &&
               Objects.equals(defaultExtractionPipeline, that.defaultExtractionPipeline);
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
                rerankScoreMinimum,
                fields,
                timeField,
                defaultExtractionPipeline);
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
               ", rerankScoreMinimum=" + rerankScoreMinimum +
               ", fields=" + fields +
               ", timeField='" + timeField + '\'' +
               ", defaultExtractionPipeline=" + defaultExtractionPipeline +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDoc.AbstractBuilder<ElasticIndexDoc, ElasticIndexDoc.Builder> {

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
        private String rerankTextFieldSuffix = DEFAULT_TEXT_FIELD_SUFFIX;
        private Float rerankScoreMinimum = DEFAULT_RERANK_SCORE_MINIMUM;

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
            this.rerankScoreMinimum = elasticIndexDoc.rerankScoreMinimum;
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

        public Builder vectorGenerationModelRef(final DocRef vectorGenerationModelRef) {
            this.vectorGenerationModelRef = vectorGenerationModelRef;
            return self();
        }

        public Builder rerankModelRef(final DocRef rerankModelRef) {
            this.rerankModelRef = rerankModelRef;
            return self();
        }

        public Builder rerankTextFieldSuffix(final String rerankTextFieldSuffix) {
            this.rerankTextFieldSuffix = rerankTextFieldSuffix;
            return self();
        }

        public Builder rerankScoreMinimum(final Float rerankScoreMinimum) {
            this.rerankScoreMinimum = rerankScoreMinimum;
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
                    rerankScoreMinimum,
                    defaultExtractionPipeline);
        }
    }
}
