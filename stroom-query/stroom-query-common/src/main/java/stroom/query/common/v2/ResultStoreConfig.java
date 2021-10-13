package stroom.query.common.v2;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
public class ResultStoreConfig extends AbstractConfig {

    private String localDir = "search_results";
    private String lmdbSystemLibraryPath = null;
    private int maxPutsBeforeCommit = 0;
    private int maxReaders = 10;
    private ByteSize maxStoreSize = ByteSize.ofGibibytes(10);
    private int maxDbs = 10;
    private boolean isReadAheadEnabled = true;
    private boolean offHeapResults = true;
    private ByteSize payloadLimit = ByteSize.ofMebibytes(0);

    private ByteSize minValueSize = ByteSize.ofKibibytes(1);
    private ByteSize maxValueSize = ByteSize.ofMebibytes(1);
    private ByteSize minPayloadSize = ByteSize.ofMebibytes(1);
    private ByteSize maxPayloadSize = ByteSize.ofGibibytes(1);

    @Nonnull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path relative to the home directory to use for storing the reference data store. " +
            "It MUST be on local disk, NOT network storage, due to use of memory mapped files. " +
            "The directory will be created if it doesn't exist." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getLocalDir() {
        return localDir;
    }


    public void setLocalDir(final String localDir) {
        this.localDir = localDir;
    }

    @ValidFilePath
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path to a provided LMDB system library file. If unset the LMDB binary " +
            "bundled with Stroom will be extracted to 'localDir'. This property can be used if you already have " +
            "LMDB installed or want to make use of a package manager provided instance. If you set this property " +
            "care needs  to be taken over version compatibility between the version of LMDBJava (that Stroom " +
            "uses to interact with LMDB) and the version of the LMDB binary.")
    public String getLmdbSystemLibraryPath() {
        return lmdbSystemLibraryPath;
    }

    public void setLmdbSystemLibraryPath(final String lmdbSystemLibraryPath) {
        this.lmdbSystemLibraryPath = lmdbSystemLibraryPath;
    }

    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @SuppressWarnings("unused")
    public void setMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
    }

    @Min(1)
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The maximum number of concurrent readers/threads that can use the off-heap store.")
    public int getMaxReaders() {
        return maxReaders;
    }

    @SuppressWarnings("unused")
    public void setMaxReaders(final int maxReaders) {
        this.maxReaders = maxReaders;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The maximum size for the ref loader off heap store. There must be " +
            "available space on the disk to accommodate this size. It can be larger than the amount of available RAM " +
            "and will only be allocated as it is needed. Can be expressed in IEC units (multiples of 1024), " +
            "e.g. 1024, 1024B, 1024bytes, 1KiB, 1KB, 1K, etc.")
    public ByteSize getMaxStoreSize() {
        return maxStoreSize;
    }

    public void setMaxStoreSize(final ByteSize maxStoreSize) {
        this.maxStoreSize = maxStoreSize;
    }

    @JsonPropertyDescription("The maximum number of databases that can be created for this environment.")
    public int getMaxDbs() {
        return maxDbs;
    }

    public void setMaxDbs(final int maxDbs) {
        this.maxDbs = maxDbs;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Read ahead means the OS will pre-fetch additional data from the disk in the " +
            "expectation that it will be used at some point. This generally improves performance as more data is " +
            "available in the page cache. Read ahead is enabled by default. It may be worth disabling it if " +
            "the actively used search data is larger than the available RAM, as this will stop it evicting hot " +
            "search entries to make space for pre-fetched data.")
    public boolean isReadAheadEnabled() {
        return isReadAheadEnabled;
    }

    public void setReadAheadEnabled(final boolean isReadAheadEnabled) {
        this.isReadAheadEnabled = isReadAheadEnabled;
    }

    @JsonPropertyDescription("Should search results be stored off heap (experimental feature).")
    @JsonProperty("offHeapResults")
    public boolean isOffHeapResults() {
        return offHeapResults;
    }

    public void setOffHeapResults(final boolean offHeapResults) {
        this.offHeapResults = offHeapResults;
    }

    @JsonPropertyDescription("Do we want to limit the size of payloads (0 by default means no limit).")
    @JsonProperty("payloadLimit")
    public ByteSize getPayloadLimit() {
        return payloadLimit;
    }

    public void setPayloadLimit(final ByteSize payloadLimit) {
        this.payloadLimit = payloadLimit;
    }

    @JsonPropertyDescription("The minimum byte size of a value byte buffer.")
    public ByteSize getMinValueSize() {
        return minValueSize;
    }

    public void setMinValueSize(final ByteSize minValueSize) {
        this.minValueSize = minValueSize;
    }

    @JsonPropertyDescription("The maximum byte size of a value byte buffer.")
    public ByteSize getMaxValueSize() {
        return maxValueSize;
    }

    public void setMaxValueSize(final ByteSize maxValueSize) {
        this.maxValueSize = maxValueSize;
    }

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    public ByteSize getMinPayloadSize() {
        return minPayloadSize;
    }

    public void setMinPayloadSize(final ByteSize minPayloadSize) {
        this.minPayloadSize = minPayloadSize;
    }

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    public ByteSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(final ByteSize maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    @Override
    public String toString() {
        return "RefDataStoreConfig{" +
                "localDir='" + localDir + '\'' +
                ", maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", maxReaders=" + maxReaders +
                ", maxStoreSize='" + maxStoreSize + '\'' +
                ", isReadAheadEnabled=" + isReadAheadEnabled +
                '}';
    }
}
