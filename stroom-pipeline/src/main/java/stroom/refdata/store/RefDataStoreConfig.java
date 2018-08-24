package stroom.refdata.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

@Singleton
public class RefDataStoreConfig {
    private static final int MAX_READERS_DEFAULT = 100;
    private static final int MAX_PUTS_BEFORE_COMMIT_DEFAULT = 1000;
    private static final int VALUE_BUFFER_CAPACITY_DEFAULT_VALUE = 1000;

    private String localDir = "${stroom.temp}/refDataOffHeapStore";
    private int maxPutsBeforeCommit = MAX_PUTS_BEFORE_COMMIT_DEFAULT;
    private int maxReaders = MAX_READERS_DEFAULT;
    private String maxStoreSize = "50G";
    private String purgeAge = "30d";
    private int valueBufferCapacity = VALUE_BUFFER_CAPACITY_DEFAULT_VALUE;

    @JsonPropertyDescription("The full directory path  to use for storing the reference data store. It MUST be on local disk, NOT network storage, due to use of memory mapped files. The directory will be created if it doesn't exist.")
    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(final String localDir) {
        this.localDir = localDir;
    }

    @JsonPropertyDescription("The maximum number of puts into the store before the transaction is committed. There is only one write transaction available long running transactions are not desirable.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    public void setMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
    }

    @JsonPropertyDescription("The maximum number of concurrent readers/threads that can use the offheapstore.")
    public int getMaxReaders() {
        return maxReaders;
    }

    public void setMaxReaders(final int maxReaders) {
        this.maxReaders = maxReaders;
    }

    @JsonPropertyDescription("The maximum size in bytes for the ref loader off heap store. There must be available space on the disk to accommodate this size. It can be larger than the amount of available RAM.")
    public String getMaxStoreSize() {
        return maxStoreSize;
    }

    public void setMaxStoreSize(final String maxStoreSize) {
        this.maxStoreSize = maxStoreSize;
    }

    @JsonPropertyDescription("The time to retain reference data for in the off heap store. The time is taken from the time that the reference stream was last accessed, e.g. a lookup was made against it.")
    public String getPurgeAge() {
        return purgeAge;
    }

    public void setPurgeAge(final String purgeAge) {
        this.purgeAge = purgeAge;
    }

    @JsonPropertyDescription("The size in bytes allocated to the value buffers used in the offheapstore. This should be large enough to accommodate reference data values.")
    public int getValueBufferCapacity() {
        return valueBufferCapacity;
    }

    public void setValueBufferCapacity(final int valueBufferCapacity) {
        this.valueBufferCapacity = valueBufferCapacity;
    }

    @JsonIgnore
    long getMaxStoreSizeBytes() {
        return ModelStringUtil.parseIECByteSizeString(maxStoreSize);
    }
}
