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

package stroom.index.shared;

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.ModelStringUtil;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A place where a index has been created.
 */
@Entity
@Table(name = "IDX_SHRD")
public class IndexShard extends AuditedEntity {
    public static final String TABLE_NAME = SQLNameConstants.INDEX + SEP + SQLNameConstants.SHARD;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String FILE_SIZE = SQLNameConstants.FILE + SEP + SQLNameConstants.SIZE;
    public static final String COMMIT_MS = SQLNameConstants.COMMIT + SQLNameConstants.MS_SUFFIX;
    public static final String COMMIT_DURATION_MS = SQLNameConstants.COMMIT + SEP + SQLNameConstants.DURATION
            + SQLNameConstants.MS_SUFFIX;
    public static final String COMMIT_DOCUMENT_COUNT = SQLNameConstants.COMMIT + SEP + SQLNameConstants.DOCUMENT
            + SQLNameConstants.COUNT_SUFFIX;
    public static final String DOCUMENT_COUNT = SQLNameConstants.DOCUMENT + SQLNameConstants.COUNT_SUFFIX;
    public static final String PARTITION = SQLNameConstants.PARTITION;
    public static final String PARTITION_FROM_TIME = SQLNameConstants.PARTITION + SEP + SQLNameConstants.FROM
            + SQLNameConstants.MS_SUFFIX;
    public static final String PARTITION_TO_TIME = SQLNameConstants.PARTITION + SEP + SQLNameConstants.TO
            + SQLNameConstants.MS_SUFFIX;
    public static final String INDEX_VERSION = SQLNameConstants.INDEX + SEP + SQLNameConstants.VERSION;
    public static final String ENTITY_TYPE = "IndexShard";
    public static final String MANAGE_INDEX_SHARDS_PERMISSION = "Manage Index Shards";
    public static final Set<IndexShardStatus> NON_DELETED_INDEX_SHARD_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(IndexShardStatus.OPEN, IndexShardStatus.CLOSED, IndexShardStatus.CORRUPT)));
    public static final Set<IndexShardStatus> READABLE_INDEX_SHARD_STATUS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(IndexShardStatus.OPEN, IndexShardStatus.CLOSED)));
    private static final long serialVersionUID = 3699846921846088685L;
    /**
     * The index that this index shard belongs to.
     */
    private Index index;
    /**
     * Volume the index is on
     */
    private Volume volume;
    /**
     * The owner of the index (the writer)
     */
    private Node node;
    /**
     * The time that the partition that this shard belongs to starts
     */
    private String partition;
    /**
     * The time that the partition that this shard belongs to starts
     */
    private Long partitionFromTime;
    /**
     * The time that the partition that this shard belongs to ends
     */
    private Long partitionToTime;
    /**
     * Number of documents indexed
     */
    private int documentCount;
    /**
     * When the item was last commited / updated
     */
    private Long commitMs;
    /**
     * How long did the commit take
     */
    private Long commitDurationMs;
    /**
     * The number of extra documents commited
     */
    private Integer commitDocumentCount;
    /**
     * Status
     */
    private volatile byte pstatus = IndexShardStatus.CLOSED.getPrimitiveValue();
    private Long fileSize;
    private String indexVersion;

    public IndexShard() {
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = Index.FOREIGN_KEY)
    public Index getIndex() {
        return index;
    }

    public void setIndex(final Index index) {
        this.index = index;
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = Volume.FOREIGN_KEY)
    @Fetch(FetchMode.SELECT)
    public Volume getVolume() {
        return volume;
    }

    public void setVolume(final Volume volume) {
        this.volume = volume;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = Node.FOREIGN_KEY)
    public Node getNode() {
        return node;
    }

    public void setNode(final Node ownerNode) {
        this.node = ownerNode;
    }

    @Column(name = PARTITION, nullable = false)
    public String getPartition() {
        return partition;
    }

    public void setPartition(final String partition) {
        this.partition = partition;
    }

    @Column(name = PARTITION_FROM_TIME, columnDefinition = BIGINT_UNSIGNED)
    public Long getPartitionFromTime() {
        return partitionFromTime;
    }

    public void setPartitionFromTime(final Long partitionFromTime) {
        this.partitionFromTime = partitionFromTime;
    }

    @Column(name = PARTITION_TO_TIME, columnDefinition = BIGINT_UNSIGNED)
    public Long getPartitionToTime() {
        return partitionToTime;
    }

    public void setPartitionToTime(final Long partitionToTime) {
        this.partitionToTime = partitionToTime;
    }

    @Column(name = SQLNameConstants.STATUS, nullable = false)
    public byte getPstatus() {
        return pstatus;
    }

    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    @Transient
    public IndexShardStatus getStatus() {
        return IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(getPstatus());
    }

    public void setStatus(final IndexShardStatus status) {
        this.pstatus = status.getPrimitiveValue();
    }

    @Column(name = FILE_SIZE, columnDefinition = BIGINT_UNSIGNED)
    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(final Long fileSize) {
        this.fileSize = fileSize;
    }

    @Column(name = COMMIT_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getCommitMs() {
        return commitMs;
    }

    public void setCommitMs(final Long commitTimeMs) {
        this.commitMs = commitTimeMs;
    }

    @Column(name = DOCUMENT_COUNT, columnDefinition = INT_UNSIGNED, nullable = false)
    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(final int documentCount) {
        this.documentCount = documentCount;
    }

    @Column(name = COMMIT_DURATION_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getCommitDurationMs() {
        return commitDurationMs;
    }

    public void setCommitDurationMs(final Long commitDurationMs) {
        this.commitDurationMs = commitDurationMs;
    }

    @Column(name = COMMIT_DOCUMENT_COUNT, columnDefinition = INT_UNSIGNED)
    public Integer getCommitDocumentCount() {
        return commitDocumentCount;
    }

    public void setCommitDocumentCount(final Integer commitDocuments) {
        this.commitDocumentCount = commitDocuments;
    }

    @Column(name = INDEX_VERSION)
    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(final String indexVersion) {
        this.indexVersion = indexVersion;
    }

    @Transient
    public Long getCommitDocumentCountPs() {
        if (commitDocumentCount != null && commitDurationMs != null && commitDurationMs > 0) {
            final double seconds = commitDurationMs.doubleValue() / 1000;
            final double ps = commitDocumentCount.doubleValue() / seconds;

            return Long.valueOf((long) ps);
        }
        return null;
    }

    @Transient
    public String getFileSizeString() {
        return ModelStringUtil.formatIECByteSizeString(getFileSize());
    }

    @Transient
    public Integer getBytesPerDocument() {
        final Long fileSize = getFileSize();
        if (fileSize != null && documentCount > 0) {
            return (int) (fileSize / documentCount);
        }
        return null;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", status=");
        sb.append(getStatus());
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    /**
     * The status of this index shard
     */
    public enum IndexShardStatus implements HasDisplayValue, HasPrimitiveValue {
        // Closed - Nobody is writing to it
        CLOSED("Closed", 0),
        // Open - We are writing to it (maybe index or merge)
        OPEN("Open", 1),
//        // Final - used to mark that a shard is full or will no longer be used.
//        FINAL("Final", 3),
//        // Closing - We are in the process of closing the index shard.
//        CLOSING("Closing", 10),
//        // Opening - We are in the process of opening an index shard.
//        OPENING("Opening", 20),
        // Deleted - Used to mark shard for deletion
        DELETED("Deleted", 99),
        // Corrupt - Used to mark shard has been corrupted
        CORRUPT("Corrupt", 666);

        public static final PrimitiveValueConverter<IndexShardStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<IndexShardStatus>(
                IndexShardStatus.values());

        private final String displayValue;
        private final byte primitiveValue;

        IndexShardStatus(final String displayValue, final int primitiveValue) {
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
