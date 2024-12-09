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
        "Solr Index represents an index on a Solr cluster.\n" +
                "It defines the connection details for connecting to that cluster and the structure of the index.\n" +
                "It is used by the {{< pipe-elm \"SolrIndexingFilter\" >}} pipeline element." +
                "\n" +
                "{{% see-also %}}" +
                "[Solr Integration]({{< relref \"docs/user-guide/indexing/solr\" >}})" +
                "{{% /see-also %}}"
)
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
        "collection",
        "connection",
        "indexBatchSize",
        "fields",
        "timeField",
        "defaultExtractionPipeline",
        "state",
        "retentionExpression"
})
@JsonInclude(Include.NON_NULL)
public class SolrIndexDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "SolrIndex";
    public static final SvgImage ICON = SvgImage.DOCUMENT_SOLR_INDEX;

    private static final String DEFAULT_TIME_FIELD = "EventTime";

    @JsonProperty
    private String description;
    @JsonProperty
    private String collection;
    @JsonProperty
    private SolrConnectionConfig solrConnectionConfig;

    @JsonProperty
    private List<SolrIndexField> fields;
    @JsonProperty
    private String timeField;

    @JsonProperty
    private DocRef defaultExtractionPipeline;
    @JsonProperty
    private List<SolrIndexField> deletedFields;
    @JsonProperty
    private SolrSynchState solrSynchState;

    @JsonProperty
    private ExpressionOperator retentionExpression;

    public SolrIndexDoc() {
        solrConnectionConfig = new SolrConnectionConfig();

        fields = new ArrayList<>();
        // Always add standard id fields for now.
        fields.add(SolrIndexField.createIdField(SolrIndexConstants.STREAM_ID));
        fields.add(SolrIndexField.createIdField(SolrIndexConstants.EVENT_ID));
        timeField = DEFAULT_TIME_FIELD;
    }

    @JsonCreator
    public SolrIndexDoc(@JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("uniqueName") final String uniqueName,
                        @JsonProperty("version") final String version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("description") final String description,
                        @JsonProperty("collection") final String collection,
                        @JsonProperty("solrConnectionConfig") final SolrConnectionConfig solrConnectionConfig,
                        @JsonProperty("fields") final List<SolrIndexField> fields,
                        @JsonProperty("timeField") final String timeField,
                        @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline,
                        @JsonProperty("deletedFields") final List<SolrIndexField> deletedFields,
                        @JsonProperty("solrSynchState") final SolrSynchState solrSynchState,
                        @JsonProperty("retentionExpression") final ExpressionOperator retentionExpression) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.collection = collection;
        this.solrConnectionConfig = solrConnectionConfig;
        this.fields = fields;
        this.timeField = timeField;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
        this.deletedFields = deletedFields;
        this.solrSynchState = solrSynchState;
        this.retentionExpression = retentionExpression;

        if (this.solrConnectionConfig == null) {
            this.solrConnectionConfig = new SolrConnectionConfig();
        }
        if (this.fields == null) {
            this.fields = new ArrayList<>();
            // Always add standard id fields for now.
            this.fields.add(SolrIndexField.createIdField(SolrIndexConstants.STREAM_ID));
            this.fields.add(SolrIndexField.createIdField(SolrIndexConstants.EVENT_ID));
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

    public String getCollection() {
        if (collection == null || collection.trim().length() == 0) {
            return null;
        }
        return collection;
    }

    public void setCollection(final String collection) {
        this.collection = collection;
    }

    public SolrConnectionConfig getSolrConnectionConfig() {
        return solrConnectionConfig;
    }

    public void setSolrConnectionConfig(final SolrConnectionConfig solrConnectionConfig) {
        this.solrConnectionConfig = solrConnectionConfig;
    }

    public List<SolrIndexField> getFields() {
        return fields;
    }

    public void setFields(final List<SolrIndexField> fields) {
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

    public List<SolrIndexField> getDeletedFields() {
        return deletedFields;
    }

    public void setDeletedFields(final List<SolrIndexField> deletedFields) {
        this.deletedFields = deletedFields;
    }

    public SolrSynchState getSolrSynchState() {
        return solrSynchState;
    }

    public void setSolrSynchState(final SolrSynchState solrSynchState) {
        this.solrSynchState = solrSynchState;
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
        if (!(o instanceof SolrIndexDoc)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SolrIndexDoc solrIndexDoc = (SolrIndexDoc) o;
        return Objects.equals(description, solrIndexDoc.description) &&
                Objects.equals(collection, solrIndexDoc.collection) &&
                Objects.equals(solrConnectionConfig, solrIndexDoc.solrConnectionConfig) &&
                Objects.equals(fields, solrIndexDoc.fields) &&
                Objects.equals(timeField, solrIndexDoc.timeField) &&
                Objects.equals(defaultExtractionPipeline, solrIndexDoc.defaultExtractionPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                collection,
                solrConnectionConfig,
                fields,
                timeField,
                defaultExtractionPipeline);
    }

    @Override
    public String toString() {
        return "SolrIndex{" +
                "description='" + description + '\'' +
                ", collection='" + collection + '\'' +
                ", solrConnectionConfig=" + solrConnectionConfig +
                ", fields=" + fields +
                ", timeField=" + timeField +
                ", defaultExtractionPipeline=" + defaultExtractionPipeline +
                '}';
    }
}
