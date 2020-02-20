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

package stroom.index.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.docstore.shared.Doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "maxDocsPerShard", "partitionBy", "partitionSize", "shardsPerPartition", "retentionDayAge", "fields", "volumeGroupName"})
@JsonInclude(Include.NON_DEFAULT)
public class IndexDoc extends Doc {
    public static final int DEFAULT_MAX_DOCS_PER_SHARD = 1000000000;
    private static final int DEFAULT_SHARDS_PER_PARTITION = 1;
    private static final PartitionBy DEFAULT_PARTITION_BY = PartitionBy.MONTH;
    private static final int DEFAULT_PARTITION_SIZE = 1;

    public static final String DOCUMENT_TYPE = "Index";

    @JsonProperty
    private String description;
    @JsonProperty
    private int maxDocsPerShard = DEFAULT_MAX_DOCS_PER_SHARD;
    @JsonProperty
    private PartitionBy partitionBy = DEFAULT_PARTITION_BY;
    @JsonProperty
    private int partitionSize = DEFAULT_PARTITION_SIZE;
    @JsonProperty
    private int shardsPerPartition = DEFAULT_SHARDS_PER_PARTITION;
    @JsonProperty
    private Integer retentionDayAge;
    @JsonProperty
    private List<IndexField> fields;
    @JsonProperty
    private String volumeGroupName;

    public IndexDoc() {
    }

    @JsonCreator
    public IndexDoc(@JsonProperty("type") final String type,
                    @JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("version") final String version,
                    @JsonProperty("createTime") final Long createTime,
                    @JsonProperty("updateTime") final Long updateTime,
                    @JsonProperty("createUser") final String createUser,
                    @JsonProperty("updateUser") final String updateUser,
                    @JsonProperty("description") final String description,
                    @JsonProperty("maxDocsPerShard") final int maxDocsPerShard,
                    @JsonProperty("partitionBy") final PartitionBy partitionBy,
                    @JsonProperty("partitionSize") final int partitionSize,
                    @JsonProperty("shardsPerPartition") final int shardsPerPartition,
                    @JsonProperty("retentionDayAge") final Integer retentionDayAge,
                    @JsonProperty("fields") final List<IndexField> fields,
                    @JsonProperty("volumeGroupName") final String volumeGroupName) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        if (maxDocsPerShard > 0) {
            this.maxDocsPerShard = maxDocsPerShard;
        }
        if (partitionBy != null) {
            this.partitionBy = partitionBy;
        }
        if (partitionSize > 0) {
            this.partitionSize = partitionSize;
        }
        if (shardsPerPartition > 0) {
            this.shardsPerPartition = shardsPerPartition;
        }
        this.retentionDayAge = retentionDayAge;
        this.fields = fields;
        this.volumeGroupName = volumeGroupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public int getMaxDocsPerShard() {
        return maxDocsPerShard;
    }

    public void setMaxDocsPerShard(final int maxDocsPerShard) {
        this.maxDocsPerShard = maxDocsPerShard;
    }

    public PartitionBy getPartitionBy() {
        return partitionBy;
    }

    public void setPartitionBy(final PartitionBy partitionBy) {
        this.partitionBy = partitionBy;
    }

    public int getPartitionSize() {
        return partitionSize;
    }

    public void setPartitionSize(final int partitionSize) {
        this.partitionSize = partitionSize;
    }

    public int getShardsPerPartition() {
        return shardsPerPartition;
    }

    public void setShardsPerPartition(final int shardsPerPartition) {
        this.shardsPerPartition = shardsPerPartition;
    }

    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public void setRetentionDayAge(final Integer retentionDayAge) {
        this.retentionDayAge = retentionDayAge;
    }

    public List<IndexField> getFields() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        return fields;
    }

    public void setFields(final List<IndexField> fields) {
        this.fields = fields;
    }

    @Deprecated
    public void setIndexFields(final List<IndexField> fields) {
        this.fields = fields;
    }

    @Deprecated
    public List<IndexField> getIndexFields() {
        return null;
    }

    public String getVolumeGroupName() {
        return volumeGroupName;
    }

    public void setVolumeGroupName(String volumeGroupName) {
        this.volumeGroupName = volumeGroupName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final IndexDoc indexDoc = (IndexDoc) o;
        return maxDocsPerShard == indexDoc.maxDocsPerShard &&
                partitionSize == indexDoc.partitionSize &&
                shardsPerPartition == indexDoc.shardsPerPartition &&
                Objects.equals(description, indexDoc.description) &&
                partitionBy == indexDoc.partitionBy &&
                Objects.equals(retentionDayAge, indexDoc.retentionDayAge) &&
                Objects.equals(fields, indexDoc.fields) &&
                Objects.equals(volumeGroupName, indexDoc.volumeGroupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, maxDocsPerShard, partitionBy, partitionSize, shardsPerPartition, retentionDayAge, fields, volumeGroupName);
    }

    public enum PartitionBy implements HasDisplayValue {
        DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year");

        private final String displayValue;

        PartitionBy(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}