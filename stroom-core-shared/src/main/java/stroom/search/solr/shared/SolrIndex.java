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

package stroom.search.solr.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "collection", "connection", "indexBatchSize", "fields"})
public class SolrIndex extends Doc {
    public static final String ENTITY_TYPE = "SolrIndex";

    private static final long serialVersionUID = 2648729644398564919L;

    private String description;
    private String collection;
    private SolrConnectionConfig solrConnectionConfig = new SolrConnectionConfig();
    private int indexBatchSize = 1000;

    private List<SolrIndexField> fields;
    private List<SolrIndexField> deletedFields;

    public SolrIndex() {
        fields = new ArrayList<>();
        // Always add standard id fields for now.
        fields.add(SolrIndexField.createIdField(SolrIndexConstants.STREAM_ID));
        fields.add(SolrIndexField.createIdField(SolrIndexConstants.EVENT_ID));
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getCollection() {
        if (collection == null || collection.trim().length() == 0) {
            return null;
        }
        return collection;
    }

    public void setCollection(final String collection) {
        this.collection = collection;
    }

    @JsonProperty("connection")
    public SolrConnectionConfig getSolrConnectionConfig() {
        return solrConnectionConfig;
    }

    @JsonProperty("connection")
    public void setSolrConnectionConfig(final SolrConnectionConfig solrConnectionConfig) {
        this.solrConnectionConfig = solrConnectionConfig;
    }

    public int getIndexBatchSize() {
        return indexBatchSize;
    }

    public void setIndexBatchSize(final int indexBatchSize) {
        this.indexBatchSize = indexBatchSize;
    }

    @JsonProperty("fields")
    public List<SolrIndexField> getFields() {
        return fields;
    }

    @JsonProperty("fields")
    public void setFields(final List<SolrIndexField> fields) {
        this.fields = fields;
    }

    @JsonIgnore
    public List<SolrIndexField> getDeletedFields() {
        return deletedFields;
    }

    @JsonIgnore
    public void setDeletedFields(final List<SolrIndexField> deletedFields) {
        this.deletedFields = deletedFields;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SolrIndex)) return false;
        if (!super.equals(o)) return false;
        final SolrIndex solrIndex = (SolrIndex) o;
        return indexBatchSize == solrIndex.indexBatchSize &&
                Objects.equals(description, solrIndex.description) &&
                Objects.equals(collection, solrIndex.collection) &&
                Objects.equals(solrConnectionConfig, solrIndex.solrConnectionConfig) &&
                Objects.equals(fields, solrIndex.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, collection, solrConnectionConfig, indexBatchSize, fields);
    }

    @Override
    public String toString() {
        return "SolrIndex{" +
                "description='" + description + '\'' +
                ", collection='" + collection + '\'' +
                ", solrConnectionConfig=" + solrConnectionConfig +
                ", indexBatchSize=" + indexBatchSize +
                ", fields=" + fields +
                '}';
    }
}
