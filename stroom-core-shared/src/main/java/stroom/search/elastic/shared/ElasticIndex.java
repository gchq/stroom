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

import stroom.datasource.api.v2.DataSourceField;
import stroom.docstore.shared.Doc;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "clusterRef", "indexName", "fields", "dataSourceFields", "retentionExpression"})
public class ElasticIndex extends Doc {
    public static final String ENTITY_TYPE = "ElasticIndex";

    private static final long serialVersionUID = 1L;

    /**
     * Reference to the `ElasticCluster` containing common Elasticsearch cluster connection properties
     */
    private DocRef clusterRef;

    private String description;
    private String indexName;
    private List<ElasticIndexField> fields;
    private List<DataSourceField> dataSourceFields;
    private ExpressionOperator retentionExpression;

    public ElasticIndex() {
        this.fields = new ArrayList<>();
        this.dataSourceFields = new ArrayList<>();
    }

    public String getDescription() { return description; }

    public void setDescription(final String description) { this.description = description; }

    @JsonProperty("clusterRef")
    public DocRef getClusterRef() { return clusterRef; }

    @JsonProperty("clusterRef")
    public void setClusterRef(final DocRef clusterRef) {
        this.clusterRef = clusterRef;
    }

    @JsonProperty("indexName")
    public String getIndexName() { return indexName; }

    @JsonProperty("indexName")
    public void setIndexName(final String indexName)
    {
        if (indexName == null || indexName.trim().isEmpty()) {
            this.indexName = null;
        }
        else {
            this.indexName = indexName;
        }
    }

    @JsonProperty("fields")
    public List<ElasticIndexField> getFields() { return fields; }

    @JsonProperty("fields")
    public void setFields(final List<ElasticIndexField> fields) {
        this.fields = fields;
    }

    @JsonProperty("dataSourceFields")
    public List<DataSourceField> getDataSourceFields() { return dataSourceFields; }

    @JsonProperty("dataSourceFields")
    public void setDataSourceFields(final List<DataSourceField> dataSourceFields) { this.dataSourceFields = dataSourceFields; }

    @JsonProperty("retentionExpression")
    public ExpressionOperator getRetentionExpression() { return retentionExpression; }

    @JsonProperty("retentionExpression")
    public void setRetentionExpression(final ExpressionOperator retentionExpression) { this.retentionExpression = retentionExpression; }

    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ElasticIndex)) return false;
        if (!super.equals(o)) return false;
        final ElasticIndex elasticIndex = (ElasticIndex) o;
        return Objects.equals(description, elasticIndex.description) &&
                Objects.equals(clusterRef, elasticIndex.clusterRef) &&
                Objects.equals(indexName, elasticIndex.indexName) &&
                Objects.equals(fields, elasticIndex.fields) &&
                Objects.equals(dataSourceFields, elasticIndex.dataSourceFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, indexName, clusterRef, fields, dataSourceFields);
    }

    @Override
    public String toString() {
        return "ElasticIndex{" +
                "description='" + description + '\'' +
                ", clusterRef='" + clusterRef + '\'' +
                ", indexName='" + indexName + '\'' +
                ", fields=" + fields +
                ", dataSourceFields=" + dataSourceFields +
                '}';
    }
}
