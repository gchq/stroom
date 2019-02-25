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

package stroom.index;

import stroom.importexport.shared.ExternalFile;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;
import stroom.importexport.migration.DocumentEntity;
import stroom.index.shared.IndexFields;

import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldIndex extends DocumentEntity {
    private static final int DEFAULT_MAX_DOCS_PER_SHARD = 1000000000;
    private static final int DEFAULT_SHARDS_PER_PARTITION = 1;
    private static final PartitionBy DEFAULT_PARTITION_BY = PartitionBy.MONTH;
    private static final int DEFAULT_PARTITION_SIZE = 1;
    private static final String ENTITY_TYPE = "Index";

    //    private Set<Volume> volumes = new HashSet<>();
    private int maxDocsPerShard = DEFAULT_MAX_DOCS_PER_SHARD;
    private int shardsPerPartition = DEFAULT_SHARDS_PER_PARTITION;
    private Byte pPartitionBy = DEFAULT_PARTITION_BY.primitiveValue;
    private int partitionSize = DEFAULT_PARTITION_SIZE;
    private Integer retentionDayAge;
    private String description;
    private String indexFields;
    private IndexFields indexFieldsObject;

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

    public Byte getPPartitionBy() {
        return pPartitionBy;
    }

    public void setPPartitionBy(final Byte pPartitionBy) {
        this.pPartitionBy = pPartitionBy;
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

    public PartitionBy getPartitionBy() {
        if (pPartitionBy == null) {
            return null;
        }
        return PartitionBy.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pPartitionBy);
    }

    public void setPartitionBy(final PartitionBy partitionBy) {
        if (partitionBy == null) {
            this.pPartitionBy = null;
        } else {
            this.pPartitionBy = partitionBy.getPrimitiveValue();
        }
    }

    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public void setRetentionDayAge(final Integer retentionDayAge) {
        this.retentionDayAge = retentionDayAge;
    }

    @ExternalFile
    public String getIndexFields() {
        return indexFields;
    }

    public void setIndexFields(final String indexFields) {
        this.indexFields = indexFields;
    }

    @XmlTransient
    public IndexFields getIndexFieldsObject() {
        return indexFieldsObject;
    }

    public void setIndexFieldsObject(final IndexFields indexFieldsObject) {
        this.indexFieldsObject = indexFieldsObject;
    }

    public final String getType() {
        return ENTITY_TYPE;
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
