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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PrimitiveValueConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A place where a indexUuid has been created.
 */
@JsonPropertyOrder({
        "id",
        "partition",
        "partitionFromTime",
        "partitionToTime",
        "documentCount",
        "commitMs",
        "commitDurationMs",
        "commitDocumentCount",
        "status",
        "fileSize",
        "indexVersion",
        "volume",
        "nodeName",
        "indexUuid"
})
@JsonInclude(Include.NON_NULL)
public class IndexShard {

    public static final Set<IndexShardStatus> NON_DELETED_INDEX_SHARD_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    IndexShardStatus.OPEN,
                    IndexShardStatus.OPENING,
                    IndexShardStatus.CLOSED,
                    IndexShardStatus.CLOSING,
                    IndexShardStatus.CORRUPT)));

    @JsonProperty
    private Long id;

    /**
     * The time that the partition that this shard belongs to starts
     */
    @JsonProperty
    private String partition;
    /**
     * The time that the partition that this shard belongs to starts
     */
    @JsonProperty
    private Long partitionFromTime;
    /**
     * The time that the partition that this shard belongs to ends
     */
    @JsonProperty
    private Long partitionToTime;
    /**
     * Number of documents indexed
     */
    @JsonProperty
    private int documentCount;
    /**
     * When the item was last commited / updated
     */
    @JsonProperty
    private Long commitMs;
    /**
     * How long did the commit take
     */
    @JsonProperty
    private Long commitDurationMs;
    /**
     * The number of extra documents commited
     */
    @JsonProperty
    private Integer commitDocumentCount;
    /**
     * Status
     */
    @JsonProperty
    private volatile IndexShardStatus status = IndexShardStatus.NEW;

    @JsonProperty
    private Long fileSize;
    @JsonProperty
    private String indexVersion;
    @JsonProperty
    private IndexVolume volume;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    private String indexUuid;

    public IndexShard() {
    }

    @JsonCreator
    public IndexShard(@JsonProperty("id") final Long id,
                      @JsonProperty("partition") final String partition,
                      @JsonProperty("partitionFromTime") final Long partitionFromTime,
                      @JsonProperty("partitionToTime") final Long partitionToTime,
                      @JsonProperty("documentCount") final int documentCount,
                      @JsonProperty("commitMs") final Long commitMs,
                      @JsonProperty("commitDurationMs") final Long commitDurationMs,
                      @JsonProperty("commitDocumentCount") final Integer commitDocumentCount,
                      @JsonProperty("status") final IndexShardStatus status,
                      @JsonProperty("fileSize") final Long fileSize,
                      @JsonProperty("indexVersion") final String indexVersion,
                      @JsonProperty("volume") final IndexVolume volume,
                      @JsonProperty("nodeName") final String nodeName,
                      @JsonProperty("indexUuid") final String indexUuid) {
        this.id = id;
        this.partition = partition;
        this.partitionFromTime = partitionFromTime;
        this.partitionToTime = partitionToTime;
        this.documentCount = documentCount;
        this.commitMs = commitMs;
        this.commitDurationMs = commitDurationMs;
        this.commitDocumentCount = commitDocumentCount;
        this.status = status;
        this.fileSize = fileSize;
        this.indexVersion = indexVersion;
        this.volume = volume;
        this.nodeName = nodeName;
        this.indexUuid = indexUuid;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setVolume(final IndexVolume volume) {
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

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public void setIndexUuid(final String indexUuid) {
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

    public IndexShardStatus getStatus() {
        return status;
    }

    public void setStatus(final IndexShardStatus status) {
        this.status = status;
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

    @JsonIgnore
    public Long getCommitDocumentCountPs() {
        if (commitDocumentCount != null && commitDurationMs != null && commitDurationMs > 0) {
            final double seconds = commitDurationMs.doubleValue() / 1000;
            final double ps = commitDocumentCount.doubleValue() / seconds;

            return Long.valueOf((long) ps);
        }
        return null;
    }

    @JsonIgnore
    public String getFileSizeString() {
        return ModelStringUtil.formatIECByteSizeString(getFileSize());
    }

    @JsonIgnore
    public Integer getBytesPerDocument() {
        final Long fileSize = getFileSize();
        if (fileSize != null && documentCount > 0) {
            return (int) (fileSize / documentCount);
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IndexShard{");
        sb.append("id=").append(id);
        sb.append(", partition='").append(partition).append('\'');
        sb.append(", partitionFromTime=").append(partitionFromTime);
        sb.append(", partitionToTime=").append(partitionToTime);
        sb.append(", documentCount=").append(documentCount);
        sb.append(", commitMs=").append(commitMs);
        sb.append(", commitDurationMs=").append(commitDurationMs);
        sb.append(", commitDocumentCount=").append(commitDocumentCount);
        sb.append(", status=").append(status);
        sb.append(", fileSize=").append(fileSize);
        sb.append(", indexVersion='").append(indexVersion).append('\'');
        sb.append(", volume=").append(volume);
        sb.append(", nodeName='").append(nodeName).append('\'');
        sb.append(", indexUuid='").append(indexUuid).append('\'');
        sb.append('}');
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


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
        // New - We are a brand new shard that hasn't been opened yet.
        NEW("New", 30),
        // Deleted - Used to mark shard for deletion
        DELETED("Deleted", 99),
        // Corrupt - Used to mark shard has been corrupted
        CORRUPT("Corrupt", 100);

        public static final PrimitiveValueConverter<IndexShardStatus> PRIMITIVE_VALUE_CONVERTER =
                PrimitiveValueConverter.create(IndexShardStatus.class, IndexShardStatus.values());

        private final String displayValue;
        private final byte primitiveValue;
        private static final Map<String, IndexShardStatus> DISPLAY_NAME_TO_STATUS_MAP =
                Arrays.stream(IndexShardStatus.values())
                        .collect(Collectors.toMap(IndexShardStatus::getDisplayValue, Function.identity()));

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

        public static IndexShardStatus fromDisplayValue(final String displayValue) {
            final IndexShardStatus indexShardStatus = DISPLAY_NAME_TO_STATUS_MAP.get(displayValue);
            if (indexShardStatus == null) {
                throw new RuntimeException("Unknown status with displayValue " + displayValue);
            }
            return indexShardStatus;
        }
    }
}
