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

import stroom.docref.HasDisplayValue;
import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.util.shared.ModelStringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A place where a indexUuid has been created.
 */
public class IndexShard extends AuditedEntity {
    public static final Set<IndexShardStatus> NON_DELETED_INDEX_SHARD_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(IndexShardStatus.OPEN, IndexShardStatus.CLOSED, IndexShardStatus.CORRUPT)));
    public static final Set<IndexShardStatus> READABLE_INDEX_SHARD_STATUS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(IndexShardStatus.OPEN, IndexShardStatus.CLOSED)));

    private static final long serialVersionUID = 3699846921846088685L;

    public static final String ENTITY_TYPE = "IndexShard";

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

    private IndexVolume volume;

    private String nodeName;

    private String indexUuid;

    public IndexShard() {
    }

    public void setVolume(IndexVolume volume) {
        this.volume = volume;
    }

    public IndexVolume getVolume() {
        return volume;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getIndexUuid() {
        return indexUuid;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setIndexUuid(String indexUuid) {
        this.indexUuid = indexUuid;
    }

    public String getPartition() {
        return partition;
    }

    public void setPartition(final String partition) {
        this.partition = partition;
    }

    public Long getPartitionFromTime() {
        return partitionFromTime;
    }

    public void setPartitionFromTime(final Long partitionFromTime) {
        this.partitionFromTime = partitionFromTime;
    }

    public Long getPartitionToTime() {
        return partitionToTime;
    }

    public void setPartitionToTime(final Long partitionToTime) {
        this.partitionToTime = partitionToTime;
    }

    public byte getPstatus() {
        return pstatus;
    }

    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    public IndexShardStatus getStatus() {
        return IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(getPstatus());
    }

    public void setStatus(final IndexShardStatus status) {
        this.pstatus = status.getPrimitiveValue();
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(final Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getCommitMs() {
        return commitMs;
    }

    public void setCommitMs(final Long commitTimeMs) {
        this.commitMs = commitTimeMs;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(final int documentCount) {
        this.documentCount = documentCount;
    }

    public Long getCommitDurationMs() {
        return commitDurationMs;
    }

    public void setCommitDurationMs(final Long commitDurationMs) {
        this.commitDurationMs = commitDurationMs;
    }

    public Integer getCommitDocumentCount() {
        return commitDocumentCount;
    }

    public void setCommitDocumentCount(final Integer commitDocuments) {
        this.commitDocumentCount = commitDocuments;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(final String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public Long getCommitDocumentCountPs() {
        if (commitDocumentCount != null && commitDurationMs != null && commitDurationMs > 0) {
            final double seconds = commitDurationMs.doubleValue() / 1000;
            final double ps = commitDocumentCount.doubleValue() / seconds;

            return Long.valueOf((long) ps);
        }
        return null;
    }

    public String getFileSizeString() {
        return ModelStringUtil.formatIECByteSizeString(getFileSize());
    }

    public Integer getBytesPerDocument() {
        final Long fileSize = getFileSize();
        if (fileSize != null && documentCount > 0) {
            return (int) (fileSize / documentCount);
        }
        return null;
    }

    @Override
    public String getType() {
        return ENTITY_TYPE;
    }

    /**
     * The status of this indexUuid shard
     */
    public enum IndexShardStatus implements HasDisplayValue, HasPrimitiveValue {
        // Closed - Nobody is writing to it
        CLOSED("Closed", 0),
        // Open - We are writing to it (maybe indexUuid or merge)
        OPEN("Open", 1),
        //        // Final - used to mark that a shard is full or will no longer be used.
//        FINAL("Final", 3),
// Closing - We are in the process of closing the indexUuid shard.
        CLOSING("Closing", 10),
        // Opening - We are in the process of opening an indexUuid shard.
        OPENING("Opening", 20),
        // Deleted - Used to mark shard for deletion
        DELETED("Deleted", 99),
        // Corrupt - Used to mark shard has been corrupted
        CORRUPT("Corrupt", 100);

        public static final PrimitiveValueConverter<IndexShardStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                values());

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
