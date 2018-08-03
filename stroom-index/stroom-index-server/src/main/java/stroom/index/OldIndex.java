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

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.index.shared.IndexFields;
import stroom.docref.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "IDX")
public class OldIndex extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.INDEX;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String MAX_DOCUMENT = SQLNameConstants.MAX + SEP + SQLNameConstants.DOCUMENT;
    public static final String MAX_SHARD = SQLNameConstants.MAX + SEP + SQLNameConstants.SHARD;
    public static final String PARTITION_SIZE = SQLNameConstants.PARTITION + SEP + SQLNameConstants.SIZE;
    public static final String PARTITION_BY = SQLNameConstants.PARTITION + SEP + SQLNameConstants.BY;
    public static final String RETENTION_DAY_AGE = SQLNameConstants.RETENTION + SEP + SQLNameConstants.DAY + SEP
            + SQLNameConstants.AGE;
    public static final int DEFAULT_MAX_DOCS_PER_SHARD = 1000000000;
    public static final int DEFAULT_SHARDS_PER_PARTITION = 1;
    public static final PartitionBy DEFAULT_PARTITION_BY = PartitionBy.MONTH;
    public static final int DEFAULT_PARTITION_SIZE = 1;
    public static final String STATUS = SQLNameConstants.STATUS;
    public static final String ENTITY_TYPE = "Index";

    private static final long serialVersionUID = 2648729644398564919L;

    //    private Set<Volume> volumes = new HashSet<>();
    private int maxDocsPerShard = DEFAULT_MAX_DOCS_PER_SHARD;
    private int shardsPerPartition = DEFAULT_SHARDS_PER_PARTITION;
    private Byte pPartitionBy = DEFAULT_PARTITION_BY.primitiveValue;
    private int partitionSize = DEFAULT_PARTITION_SIZE;
    private Integer retentionDayAge;
    private String description;
    private String indexFields;
    private IndexFields indexFieldsObject;

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

//    @ManyToMany(fetch = FetchType.EAGER)
//    @JoinTable(name = TABLE_NAME_INDEX_VOLUME, joinColumns = @JoinColumn(name = Index.FOREIGN_KEY), inverseJoinColumns = @JoinColumn(name = Volume.FOREIGN_KEY))
//    public Set<Volume> getVolumes() {
//        return volumes;
//    }
//
//    public void setVolumes(final Set<Volume> volumes) {
//        this.volumes = volumes;
//    }

    @Column(name = MAX_DOCUMENT, nullable = false)
    public int getMaxDocsPerShard() {
        return maxDocsPerShard;
    }

    public void setMaxDocsPerShard(final int maxDocsPerShard) {
        this.maxDocsPerShard = maxDocsPerShard;
    }

    @Column(name = PARTITION_BY, nullable = true)
    public Byte getPPartitionBy() {
        return pPartitionBy;
    }

    public void setPPartitionBy(final Byte pPartitionBy) {
        this.pPartitionBy = pPartitionBy;
    }

    @Column(name = PARTITION_SIZE, nullable = false)
    public int getPartitionSize() {
        return partitionSize;
    }

    public void setPartitionSize(final int partitionSize) {
        this.partitionSize = partitionSize;
    }

    @Column(name = MAX_SHARD, nullable = false)
    public int getShardsPerPartition() {
        return shardsPerPartition;
    }

    public void setShardsPerPartition(final int shardsPerPartition) {
        this.shardsPerPartition = shardsPerPartition;
    }

    @Transient
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

    @Column(name = RETENTION_DAY_AGE, columnDefinition = INT_UNSIGNED)
    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public void setRetentionDayAge(final Integer retentionDayAge) {
        this.retentionDayAge = retentionDayAge;
    }

    @Lob
    @Column(name = SQLNameConstants.FIELDS, length = Integer.MAX_VALUE)
    @ExternalFile
    public String getIndexFields() {
        return indexFields;
    }

    public void setIndexFields(final String indexFields) {
        this.indexFields = indexFields;
    }

    @Transient
    @XmlTransient
    public IndexFields getIndexFieldsObject() {
        return indexFieldsObject;
    }

    public void setIndexFieldsObject(final IndexFields indexFieldsObject) {
        this.indexFieldsObject = indexFieldsObject;
    }

    @Transient
    @Override
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
