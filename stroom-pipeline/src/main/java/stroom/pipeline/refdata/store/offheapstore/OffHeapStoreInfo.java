package stroom.pipeline.refdata.store.offheapstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.file.Path;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class OffHeapStoreInfo {

    @JsonProperty
    private final String storeName;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final Path localDir;
    @JsonProperty
    private final long sizeOnDisk;
    @JsonProperty
    private final long sizeInUse;
    @JsonProperty
    private final long osBytesFree;
    @JsonProperty
    private final long osBytesTotal;
    @JsonProperty
    private final long keyValueEntries;
    @JsonProperty
    private final long rangeValueEntries;
    @JsonProperty
    private final long streamCount;
    @JsonProperty
    private final long distinctValuesCount;
    @JsonProperty
    private final long infoSnapshotEpochMs;

    @JsonCreator
    public OffHeapStoreInfo(@JsonProperty("storeName") final String storeName,
                            @JsonProperty("nodeName") final String nodeName,
                            @JsonProperty("feedName") final String feedName,
                            @JsonProperty("localDir") final Path localDir,
                            @JsonProperty("sizeOnDisk") final long sizeOnDisk,
                            @JsonProperty("sizeInUse") final long sizeInUse,
                            @JsonProperty("osBytesFree") final long osBytesFree,
                            @JsonProperty("osBytesTotal") final long osBytesTotal,
                            @JsonProperty("keyValueEntries") final long keyValueEntries,
                            @JsonProperty("rangeValueEntries") final long rangeValueEntries,
                            @JsonProperty("streamCount") final long streamCount,
                            @JsonProperty("distinctValuesCount") final long distinctValuesCount,
                            @JsonProperty("infoSnapshotEpochMs") final long infoSnapshotEpochMs) {
        this.storeName = storeName;
        this.nodeName = nodeName;
        this.feedName = feedName;
        this.localDir = localDir;
        this.sizeOnDisk = sizeOnDisk;
        this.sizeInUse = sizeInUse;
        this.osBytesFree = osBytesFree;
        this.osBytesTotal = osBytesTotal;
        this.keyValueEntries = keyValueEntries;
        this.rangeValueEntries = rangeValueEntries;
        this.streamCount = streamCount;
        this.distinctValuesCount = distinctValuesCount;
        this.infoSnapshotEpochMs = infoSnapshotEpochMs;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getFeedName() {
        return feedName;
    }

    public Path getLocalDir() {
        return localDir;
    }

    public long getSizeOnDisk() {
        return sizeOnDisk;
    }

    public long getSizeInUse() {
        return sizeInUse;
    }

    @JsonIgnore
    public double getStoreInUsePct() {
        return ((double) sizeInUse) / sizeOnDisk;
    }

    public long getOsBytesFree() {
        return osBytesFree;
    }

    public long getOsBytesTotal() {
        return osBytesTotal;
    }

    @JsonIgnore
    public double getOsUsePct() {
        return ((double) sizeOnDisk) / osBytesTotal;
    }

    public long getKeyValueEntries() {
        return keyValueEntries;
    }

    public long getRangeValueEntries() {
        return rangeValueEntries;
    }

    public long getStreamCount() {
        return streamCount;
    }

    public long getDistinctValuesCount() {
        return distinctValuesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OffHeapStoreInfo that = (OffHeapStoreInfo) o;
        return sizeOnDisk == that.sizeOnDisk
               && sizeInUse == that.sizeInUse
               && osBytesFree == that.osBytesFree
               && osBytesTotal == that.osBytesTotal
               && keyValueEntries == that.keyValueEntries
               && rangeValueEntries == that.rangeValueEntries
               && streamCount == that.streamCount
               && distinctValuesCount == that.distinctValuesCount
               && infoSnapshotEpochMs == that.infoSnapshotEpochMs
               && Objects.equals(storeName, that.storeName)
               && Objects.equals(nodeName, that.nodeName)
               && Objects.equals(feedName, that.feedName)
               && Objects.equals(localDir, that.localDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeName,
                nodeName,
                feedName,
                localDir,
                sizeOnDisk,
                sizeInUse,
                osBytesFree,
                osBytesTotal,
                keyValueEntries,
                rangeValueEntries,
                streamCount,
                distinctValuesCount,
                infoSnapshotEpochMs);
    }

    @Override
    public String toString() {
        return "OffHeapStoreInfo{" +
               "storeName='" + storeName + '\'' +
               ", nodeName='" + nodeName + '\'' +
               ", feedName='" + feedName + '\'' +
               ", localDir=" + localDir +
               ", sizeOnDisk=" + sizeOnDisk +
               ", sizeInUse=" + sizeInUse +
               ", osBytesFree=" + osBytesFree +
               ", osBytesTotal=" + osBytesTotal +
               ", keyValueEntries=" + keyValueEntries +
               ", rangeValueEntries=" + rangeValueEntries +
               ", streamCount=" + streamCount +
               ", distinctValuesCount=" + distinctValuesCount +
               ", infoSnapshotEpochMs=" + infoSnapshotEpochMs +
               '}';
    }
}
