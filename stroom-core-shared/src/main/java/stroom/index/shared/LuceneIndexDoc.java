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

package stroom.index.shared;

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
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

import java.util.List;
import java.util.Objects;

@Description(
        "Lucene Index is the standard built-in index within Stroom and is one of may data sources.\n" +
        "An index is like a catalog in a library and provides a very fast way to access " +
        "documents/records/events when searching using fields that have been indexed.\n" +
        "The index stores the field values and pointers to the document they came from " +
        "(the Stream and Event IDs).\n" +
        "Data can be indexed using multiple indexes to allow fast access in different ways.\n" +
        "\n" +
        "The Lucene Index Document optionally defines the fields that will be indexed (" +
        "it is possible to define the fields dynamically) and their types.\n" +
        "It also allows for configuration of the way the data in the index will be stored, " +
        "partitioned and retained.\n" +
        "\n" +
        "The Lucene Index Document is used by the {{< pipe-elm \\\"IndexingFilter\\\" >}} " +
        "and {{< pipe-elm \"DynamicIndexingFilter\" >}} pipeline elements.\n" +
        "\n" +
        "{{% see-also %}}" +
        "[Lucene Indexes]({{< relref \"docs/user-guide/indexing/lucene\" >}})" +
        "{{% /see-also %}}")
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
        "maxDocsPerShard",
        "partitionBy",
        "partitionSize",
        "shardsPerPartition",
        "retentionDayAge",
        "fields",
        "timeField",
        "volumeGroupName",
        "defaultExtractionPipeline"})
@JsonInclude(Include.NON_NULL)
public class LuceneIndexDoc extends AbstractDoc {

    public static final int DEFAULT_MAX_DOCS_PER_SHARD = 1000000000;
    private static final int DEFAULT_SHARDS_PER_PARTITION = 1;
    private static final PartitionBy DEFAULT_PARTITION_BY = PartitionBy.MONTH;
    private static final int DEFAULT_PARTITION_SIZE = 1;
    private static final String DEFAULT_TIME_FIELD = "EventTime";

    public static final String TYPE = "Index";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.LUCENE_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final Integer maxDocsPerShard;
    @JsonProperty
    private final PartitionBy partitionBy;
    @JsonProperty
    private final Integer partitionSize;
    @JsonProperty
    private final Integer shardsPerPartition;
    @JsonProperty
    private final Integer retentionDayAge;
    @JsonProperty
    private final List<LuceneIndexField> fields;
    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final String volumeGroupName;
    @JsonProperty
    private final DocRef defaultExtractionPipeline;

    @JsonCreator
    public LuceneIndexDoc(@JsonProperty("uuid") final String uuid,
                          @JsonProperty("name") final String name,
                          @JsonProperty("version") final String version,
                          @JsonProperty("createTimeMs") final Long createTimeMs,
                          @JsonProperty("updateTimeMs") final Long updateTimeMs,
                          @JsonProperty("createUser") final String createUser,
                          @JsonProperty("updateUser") final String updateUser,
                          @JsonProperty("description") final String description,
                          @JsonProperty("maxDocsPerShard") final Integer maxDocsPerShard,
                          @JsonProperty("partitionBy") final PartitionBy partitionBy,
                          @JsonProperty("partitionSize") final Integer partitionSize,
                          @JsonProperty("shardsPerPartition") final Integer shardsPerPartition,
                          @JsonProperty("retentionDayAge") final Integer retentionDayAge,
                          @JsonProperty("fields") final List<LuceneIndexField> fields,
                          @JsonProperty("timeField") final String timeField,
                          @JsonProperty("volumeGroupName") final String volumeGroupName,
                          @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.maxDocsPerShard = NullSafe.requireNonNullElse(maxDocsPerShard, DEFAULT_MAX_DOCS_PER_SHARD);
        this.partitionBy = NullSafe.requireNonNullElse(partitionBy, DEFAULT_PARTITION_BY);
        this.partitionSize = NullSafe.requireNonNullElse(partitionSize, DEFAULT_PARTITION_SIZE);
        this.shardsPerPartition = NullSafe.requireNonNullElse(shardsPerPartition, DEFAULT_SHARDS_PER_PARTITION);
        this.retentionDayAge = retentionDayAge;
        this.fields = fields;
        this.timeField = timeField;
        this.volumeGroupName = volumeGroupName;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
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

    public Integer getMaxDocsPerShard() {
        return maxDocsPerShard;
    }

    public PartitionBy getPartitionBy() {
        return partitionBy;
    }

    public Integer getPartitionSize() {
        return partitionSize;
    }

    public Integer getShardsPerPartition() {
        return shardsPerPartition;
    }

    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public List<LuceneIndexField> getFields() {
        return fields;
    }

    public String getTimeField() {
        return timeField;
    }

    public String getVolumeGroupName() {
        return volumeGroupName;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final LuceneIndexDoc indexDoc = (LuceneIndexDoc) o;
        return Objects.equals(maxDocsPerShard, indexDoc.maxDocsPerShard) &&
               Objects.equals(partitionSize, indexDoc.partitionSize) &&
               Objects.equals(shardsPerPartition, indexDoc.shardsPerPartition) &&
               Objects.equals(description, indexDoc.description) &&
               partitionBy == indexDoc.partitionBy &&
               Objects.equals(timeField, indexDoc.timeField) &&
               Objects.equals(retentionDayAge, indexDoc.retentionDayAge) &&
               Objects.equals(fields, indexDoc.fields) &&
               Objects.equals(volumeGroupName, indexDoc.volumeGroupName) &&
               Objects.equals(defaultExtractionPipeline, indexDoc.defaultExtractionPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                description,
                maxDocsPerShard,
                partitionBy,
                partitionSize,
                shardsPerPartition,
                retentionDayAge,
                fields,
                timeField,
                volumeGroupName,
                defaultExtractionPipeline);
    }

    public enum PartitionBy implements HasDisplayValue {
        DAY("Day"),
        WEEK("Week"),
        MONTH("Month"),
        YEAR("Year");

        private final String displayValue;

        PartitionBy(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractBuilder<LuceneIndexDoc, Builder> {

        private String description;
        private Integer maxDocsPerShard = DEFAULT_MAX_DOCS_PER_SHARD;
        private PartitionBy partitionBy = DEFAULT_PARTITION_BY;
        private Integer partitionSize = DEFAULT_PARTITION_SIZE;
        private Integer shardsPerPartition = DEFAULT_SHARDS_PER_PARTITION;
        private Integer retentionDayAge;
        private List<LuceneIndexField> fields;
        private String timeField = DEFAULT_TIME_FIELD;
        private String volumeGroupName;
        private DocRef defaultExtractionPipeline;

        private Builder() {
        }

        private Builder(final LuceneIndexDoc luceneIndexDoc) {
            super(luceneIndexDoc);
            this.description = luceneIndexDoc.description;
            this.maxDocsPerShard = luceneIndexDoc.maxDocsPerShard;
            this.partitionBy = luceneIndexDoc.partitionBy;
            this.partitionSize = luceneIndexDoc.partitionSize;
            this.shardsPerPartition = luceneIndexDoc.shardsPerPartition;
            this.retentionDayAge = luceneIndexDoc.retentionDayAge;
            this.fields = luceneIndexDoc.fields;
            this.timeField = luceneIndexDoc.timeField;
            this.volumeGroupName = luceneIndexDoc.volumeGroupName;
            this.defaultExtractionPipeline = luceneIndexDoc.defaultExtractionPipeline;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder maxDocsPerShard(final Integer maxDocsPerShard) {
            this.maxDocsPerShard = maxDocsPerShard;
            return self();
        }

        public Builder partitionBy(final PartitionBy partitionBy) {
            this.partitionBy = partitionBy;
            return self();
        }

        public Builder partitionSize(final Integer partitionSize) {
            this.partitionSize = partitionSize;
            return self();
        }

        public Builder shardsPerPartition(final Integer shardsPerPartition) {
            this.shardsPerPartition = shardsPerPartition;
            return self();
        }

        public Builder retentionDayAge(final Integer retentionDayAge) {
            this.retentionDayAge = retentionDayAge;
            return self();
        }

        public Builder fields(final List<LuceneIndexField> fields) {
            this.fields = fields;
            return self();
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return self();
        }

        public Builder volumeGroupName(final String volumeGroupName) {
            this.volumeGroupName = volumeGroupName;
            return self();
        }

        public Builder defaultExtractionPipeline(final DocRef defaultExtractionPipeline) {
            this.defaultExtractionPipeline = defaultExtractionPipeline;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public LuceneIndexDoc build() {
            return new LuceneIndexDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    maxDocsPerShard,
                    partitionBy,
                    partitionSize,
                    shardsPerPartition,
                    retentionDayAge,
                    fields,
                    timeField,
                    volumeGroupName,
                    defaultExtractionPipeline);
        }
    }
}
