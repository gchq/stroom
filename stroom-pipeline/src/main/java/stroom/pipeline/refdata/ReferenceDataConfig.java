package stroom.pipeline.refdata;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidFilePath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.Min;
import java.util.Map;
import java.util.TreeMap;

@Singleton
public class ReferenceDataConfig extends AbstractConfig {

    private String localDir = "${stroom.temp}/refDataOffHeapStore";
    private String lmdbSystemLibraryPath = null;
    private int maxPutsBeforeCommit = 0;
    private int maxReaders = 100;
    private ByteSize maxStoreSize = ByteSize.ofGibibytes(50);
    private StroomDuration purgeAge = StroomDuration.ofDays(30);
    private boolean isReadAheadEnabled = true;
    // Use a treemap so we get a consistent order in the yaml so TestYamlUtil doesn't fail
    private Map<Integer, Integer> pooledByteBufferCounts = new TreeMap<>(Map.of(
            1, 50,
            10, 50,
            100, 50,
            1_000, 50,
            10_000, 50,
            100_000, 10,
            1_000_000, 3));


    private CacheConfig effectiveStreamCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The absolute directory path to use for storing the reference data store. It MUST be on " +
            "local disk, NOT network storage, due to use of memory mapped files. The directory will be created " +
            "if it doesn't exist.")
    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(final String localDir) {
        this.localDir = localDir;
    }

    @ValidFilePath
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The absolute path to a provided LMDB system library file. If unset the LMDB binary " +
            "bundled with Stroom will be extracted to 'localDir'. This property can be used if you already have LMDB " +
            "installed or want to make use of a package manager provided instance. If you set this property care needs " +
            " to be taken over version compatibility between the version of LMDBJava (that Stroom uses to interact with " +
            "LMDB) and the version of the LMDB binary.")
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

    @JsonPropertyDescription("The time to retain reference data for in the off heap store. The time is taken " +
        "from the time that the reference stream was last accessed, e.g. a lookup was made against it. " +
        "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getPurgeAge() {
        return purgeAge;
    }

    @SuppressWarnings("unused")
    public void setPurgeAge(final StroomDuration purgeAge) {
        this.purgeAge = purgeAge;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Read ahead means the OS will pre-fetch additional data from the disk in the " +
            "expectation that it will be used at some point. This generally improves performance as more data is " +
            "available in the page cache. Read ahead is enabled by default. It may be worth disabling it if " +
            "the actively used ref data is larger than the available RAM, as this will stop it evicting hot " +
            "ref entries to make space for pre-fetched data.")
    public boolean isReadAheadEnabled() {
        return isReadAheadEnabled;
    }

    public void setReadAheadEnabled(final boolean isReadAheadEnabled) {
        this.isReadAheadEnabled = isReadAheadEnabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Defines the maximum number of byte buffers that will be held in the pool, keyed by the " +
            "size of the buffer. Configured buffer sizes must be a power of ten (i.e. 1, 10, 100, etc.) or they will be ignored. " +
            "Values should be greater than or equal to zero. Set the count to zero to indicate that a buffer size " +
            "should not be pooled. An empty or null map means no buffers will be pooled. " +
            "Keys should be contiguous powers of ten from one upwards, else any gaps will be assigned a default value of 50.")
    public Map<Integer, Integer> getPooledByteBufferCounts() {
        return pooledByteBufferCounts;
    }

    public void setPooledByteBufferCounts(final Map<Integer, Integer> pooledByteBufferCounts) {
        this.pooledByteBufferCounts = pooledByteBufferCounts;
    }

    public CacheConfig getEffectiveStreamCache() {
        return effectiveStreamCache;
    }

    public void setEffectiveStreamCache(final CacheConfig effectiveStreamCache) {
        this.effectiveStreamCache = effectiveStreamCache;
    }

    @Override
    public String toString() {
        return "RefDataStoreConfig{" +
                "localDir='" + localDir + '\'' +
                ", maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", maxReaders=" + maxReaders +
                ", maxStoreSize='" + maxStoreSize + '\'' +
                ", purgeAge='" + purgeAge + '\'' +
                ", isReadAheadEnabled=" + isReadAheadEnabled +
                '}';
    }
}
