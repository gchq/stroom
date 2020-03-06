/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.impl.db.migration._V07_00_00.doc.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.index.impl.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_EMPTY)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "maxDocsPerShard", "partitionBy", "partitionSize", "shardsPerPartition", "retentionDayAge", "indexFields", "volumeGroupName"})
public class _V07_00_00_IndexDoc extends _V07_00_00_Doc {
    private static final long serialVersionUID = 2648729644398564919L;

    public static final int DEFAULT_MAX_DOCS_PER_SHARD = 1000000000;
    private static final int DEFAULT_SHARDS_PER_PARTITION = 1;
    private static final PartitionBy DEFAULT_PARTITION_BY = PartitionBy.MONTH;
    private static final int DEFAULT_PARTITION_SIZE = 1;

    public static final String DOCUMENT_TYPE = "Index";

    private String description;
    private int maxDocsPerShard = DEFAULT_MAX_DOCS_PER_SHARD;
    private PartitionBy partitionBy = DEFAULT_PARTITION_BY;
    private int partitionSize = DEFAULT_PARTITION_SIZE;
    private int shardsPerPartition = DEFAULT_SHARDS_PER_PARTITION;
    private Integer retentionDayAge;
    private List<_V07_00_00_IndexField> indexFields;
    private String volumeGroupName;

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

    public List<_V07_00_00_IndexField> getIndexFields() {
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }
        return indexFields;
    }

    public void setIndexFields(final List<_V07_00_00_IndexField> indexFields) {
        this.indexFields = indexFields;
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
        final _V07_00_00_IndexDoc indexDoc = (_V07_00_00_IndexDoc) o;
        return maxDocsPerShard == indexDoc.maxDocsPerShard &&
                partitionSize == indexDoc.partitionSize &&
                shardsPerPartition == indexDoc.shardsPerPartition &&
                Objects.equals(description, indexDoc.description) &&
                partitionBy == indexDoc.partitionBy &&
                Objects.equals(retentionDayAge, indexDoc.retentionDayAge) &&
                Objects.equals(indexFields, indexDoc.indexFields) &&
                Objects.equals(volumeGroupName, indexDoc.volumeGroupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, maxDocsPerShard, partitionBy, partitionSize, shardsPerPartition, retentionDayAge, indexFields, volumeGroupName);
    }

    public enum PartitionBy implements HasDisplayValue, HasPrimitiveValue {
        DAY("Day", 1), WEEK("Week", 2), MONTH("Month", 3), YEAR("Year", 4);

        public static final PrimitiveValueConverter<PartitionBy> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                PartitionBy.values());
        private final String displayValue;
        private final byte primitiveValue;

        PartitionBy(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
