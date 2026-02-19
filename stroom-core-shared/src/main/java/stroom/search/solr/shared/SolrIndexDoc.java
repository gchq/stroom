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

package stroom.search.solr.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.ExpressionOperator;
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
        "Solr Index represents an index on a Solr cluster.\n" +
        "It defines the connection details for connecting to that cluster and the structure of the index.\n" +
        "It is used by the {{< pipe-elm \"SolrIndexingFilter\" >}} pipeline element." +
        "\n" +
        "{{% see-also %}}" +
        "[Solr Integration]({{< relref \"docs/user-guide/indexing/solr\" >}})" +
        "{{% /see-also %}}"
)
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

    public static final String TYPE = "SolrIndex";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.SOLR_INDEX_DOCUMENT_TYPE;

    private static final String DEFAULT_TIME_FIELD = "EventTime";

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String collection;
    @JsonProperty
    private final SolrConnectionConfig solrConnectionConfig;

    @JsonProperty
    private final List<SolrIndexField> fields;
    @JsonProperty
    private final String timeField;

    @JsonProperty
    private final DocRef defaultExtractionPipeline;
    @JsonProperty
    private final List<SolrIndexField> deletedFields;
    @JsonProperty
    private final SolrSynchState solrSynchState;

    @JsonProperty
    private final ExpressionOperator retentionExpression;

    @JsonCreator
    public SolrIndexDoc(@JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
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
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        if (collection == null || collection.trim().length() == 0) {
            this.collection = null;
        } else {
            this.collection = collection;
        }
        this.solrConnectionConfig = NullSafe.requireNonNullElse(
                solrConnectionConfig,
                SolrConnectionConfig.builder().build());
        this.fields = NullSafe.requireNonNullElseGet(fields, () -> {
            final List<SolrIndexField> list = new ArrayList<>();
            // Always add standard id fields for now.
            list.add(SolrIndexField.createIdField(SolrIndexConstants.STREAM_ID));
            list.add(SolrIndexField.createIdField(SolrIndexConstants.EVENT_ID));
            return list;
        });
        this.timeField = timeField;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
        this.deletedFields = deletedFields;
        this.solrSynchState = solrSynchState;
        this.retentionExpression = retentionExpression;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
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

    public String getCollection() {
        return collection;
    }

    public SolrConnectionConfig getSolrConnectionConfig() {
        return solrConnectionConfig;
    }

    public List<SolrIndexField> getFields() {
        return fields;
    }

    public String getTimeField() {
        return timeField;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
    }

    public List<SolrIndexField> getDeletedFields() {
        return deletedFields;
    }

    public SolrSynchState getSolrSynchState() {
        return solrSynchState;
    }

    public ExpressionOperator getRetentionExpression() {
        return retentionExpression;
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDocBuilder<SolrIndexDoc, Builder> {

        private String description;
        private String collection;
        private SolrConnectionConfig solrConnectionConfig = SolrConnectionConfig.builder().build();
        private List<SolrIndexField> fields = new ArrayList<>();
        private String timeField = DEFAULT_TIME_FIELD;
        private DocRef defaultExtractionPipeline;
        private List<SolrIndexField> deletedFields;
        private SolrSynchState solrSynchState;
        private ExpressionOperator retentionExpression;

        private Builder() {
            // Always add standard id fields for now.
            fields.add(SolrIndexField.createIdField(SolrIndexConstants.STREAM_ID));
            fields.add(SolrIndexField.createIdField(SolrIndexConstants.EVENT_ID));
        }

        private Builder(final SolrIndexDoc solrIndexDoc) {
            super(solrIndexDoc);
            this.description = solrIndexDoc.description;
            this.collection = solrIndexDoc.collection;
            this.solrConnectionConfig = solrIndexDoc.solrConnectionConfig;
            this.fields = solrIndexDoc.fields;
            this.timeField = solrIndexDoc.timeField;
            this.defaultExtractionPipeline = solrIndexDoc.defaultExtractionPipeline;
            this.deletedFields = solrIndexDoc.deletedFields;
            this.solrSynchState = solrIndexDoc.solrSynchState;
            this.retentionExpression = solrIndexDoc.retentionExpression;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder collection(final String collection) {
            this.collection = collection;
            return self();
        }

        public Builder solrConnectionConfig(final SolrConnectionConfig solrConnectionConfig) {
            this.solrConnectionConfig = solrConnectionConfig;
            return self();
        }

        public Builder fields(final List<SolrIndexField> fields) {
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

        public Builder deletedFields(final List<SolrIndexField> deletedFields) {
            this.deletedFields = deletedFields;
            return self();
        }

        public Builder solrSynchState(final SolrSynchState solrSynchState) {
            this.solrSynchState = solrSynchState;
            return self();
        }

        public Builder retentionExpression(final ExpressionOperator retentionExpression) {
            this.retentionExpression = retentionExpression;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public SolrIndexDoc build() {
            return new SolrIndexDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    collection,
                    solrConnectionConfig,
                    fields,
                    timeField,
                    defaultExtractionPipeline,
                    deletedFields,
                    solrSynchState,
                    retentionExpression);
        }
    }
}
