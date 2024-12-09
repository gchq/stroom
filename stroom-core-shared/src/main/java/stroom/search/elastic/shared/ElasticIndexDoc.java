/*
 * Copyright 2017 Crown Copyright
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
import stroom.query.api.v2.ExpressionOperator;
import stroom.svg.shared.SvgImage;

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
        "uniqueName",
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
        "defaultExtractionPipeline",
        "retentionExpression"
})
@JsonInclude(Include.NON_NULL)
public class ElasticIndexDoc extends AbstractDoc {

    public static final int DEFAULT_SEARCH_SLICES = 1;
    public static final int DEFAULT_SEARCH_SCROLL_SIZE = 1000;
    public static final String DOCUMENT_TYPE = "ElasticIndex";
    public static final SvgImage ICON = SvgImage.DOCUMENT_ELASTIC_INDEX;
    private static final String DEFAULT_TIME_FIELD = "@timestamp";

    /**
     * Reference to the `ElasticCluster` containing common Elasticsearch cluster connection properties
     */
    @JsonProperty
    private DocRef clusterRef;

    @JsonProperty
    private String description;

    /**
     * Name or pattern of the Elasticsearch index(es) to query
     */
    @JsonProperty
    private String indexName;

    /**
     * Number of slices to query concurrently when searching
     */
    @JsonProperty
    private Integer searchSlices;

    /**
     * Number of documents to retrieve at a time in each search scroll batch request
     */
    @JsonProperty
    private Integer searchScrollSize;

    /**
     * Array of fields, populated at query time
     */
    @JsonProperty
    private List<ElasticIndexField> fields;
    @JsonProperty
    private String timeField;
    @JsonProperty
    private DocRef defaultExtractionPipeline;

    /**
     * Criteria determining which documents should be deleted periodically by the `Elastic Index Retention`
     * server task
     */
    @JsonProperty
    private ExpressionOperator retentionExpression;

    public ElasticIndexDoc() {
        searchSlices = DEFAULT_SEARCH_SLICES;
        searchScrollSize = DEFAULT_SEARCH_SCROLL_SIZE;
        fields = new ArrayList<>();
        timeField = DEFAULT_TIME_FIELD;
    }

    @JsonCreator
    public ElasticIndexDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("uniqueName") final String uniqueName,
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
            @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline,
            @JsonProperty("retentionExpression") final ExpressionOperator retentionExpression) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.clusterRef = clusterRef;
        this.indexName = indexName;
        this.searchSlices = searchSlices;
        this.searchScrollSize = searchScrollSize;
        this.fields = fields;
        this.timeField = timeField;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
        this.retentionExpression = retentionExpression;

        if (this.searchSlices == null) {
            this.searchSlices = DEFAULT_SEARCH_SLICES;
        }
        if (this.searchScrollSize == null) {
            this.searchScrollSize = DEFAULT_SEARCH_SCROLL_SIZE;
        }
        if (this.timeField == null || this.timeField.isEmpty()) {
            this.timeField = DEFAULT_TIME_FIELD;
        }
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public DocRef getClusterRef() {
        return clusterRef;
    }

    public void setClusterRef(final DocRef clusterRef) {
        this.clusterRef = clusterRef;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(final String indexName) {
        if (indexName == null || indexName.trim().isEmpty()) {
            this.indexName = null;
        } else {
            this.indexName = indexName;
        }
    }

    public Integer getSearchSlices() {
        return searchSlices;
    }

    public void setSearchSlices(final Integer searchSlices) {
        this.searchSlices = searchSlices;
    }

    public Integer getSearchScrollSize() {
        return searchScrollSize;
    }

    public void setSearchScrollSize(final Integer searchScrollSize) {
        this.searchScrollSize = searchScrollSize;
    }

    public List<ElasticIndexField> getFields() {
        return fields;
    }

    public void setFields(final List<ElasticIndexField> fields) {
        this.fields = fields;
    }

    public String getTimeField() {
        return timeField;
    }

    public void setTimeField(final String timeField) {
        this.timeField = timeField;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
    }

    public void setDefaultExtractionPipeline(final DocRef defaultExtractionPipeline) {
        this.defaultExtractionPipeline = defaultExtractionPipeline;
    }

    public ExpressionOperator getRetentionExpression() {
        return retentionExpression;
    }

    public void setRetentionExpression(final ExpressionOperator retentionExpression) {
        this.retentionExpression = retentionExpression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElasticIndexDoc)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ElasticIndexDoc elasticIndex = (ElasticIndexDoc) o;
        return Objects.equals(description, elasticIndex.description) &&
               Objects.equals(clusterRef, elasticIndex.clusterRef) &&
               Objects.equals(indexName, elasticIndex.indexName) &&
               Objects.equals(searchSlices, elasticIndex.searchSlices) &&
               Objects.equals(searchScrollSize, elasticIndex.searchScrollSize) &&
               Objects.equals(fields, elasticIndex.fields) &&
               Objects.equals(timeField, elasticIndex.timeField) &&
               Objects.equals(defaultExtractionPipeline, elasticIndex.defaultExtractionPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                description,
                indexName,
                searchSlices,
                searchScrollSize,
                clusterRef,
                fields,
                timeField,
                defaultExtractionPipeline);
    }

    @Override
    public String toString() {
        return "ElasticIndex{" +
               "description='" + description + '\'' +
               ", clusterRef='" + clusterRef + '\'' +
               ", indexName='" + indexName + '\'' +
               ", searchSlices=" + searchSlices +
               ", searchScrollSize=" + searchScrollSize +
               ", fields=" + fields +
               ", timeField=" + timeField +
               ", defaultExtractionPipeline=" + defaultExtractionPipeline +
               '}';
    }
}
