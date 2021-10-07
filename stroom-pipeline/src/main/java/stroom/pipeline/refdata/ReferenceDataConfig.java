package stroom.pipeline.refdata;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidFilePath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ReferenceDataConfig extends AbstractConfig {

    public static final String LOCAL_DIR_PROP_NAME = "localDir";

    private String localDir = "reference_data";
    private String lmdbSystemLibraryPath = null;
    private int maxPutsBeforeCommit = 0;
    private int maxReaders = 100;
    private ByteSize maxStoreSize = ByteSize.ofGibibytes(50);
    private StroomDuration purgeAge = StroomDuration.ofDays(30);
    private boolean isReadAheadEnabled = true;
    private int loadingLockStripes = 2048;
    private boolean isReaderBlockedByWriter = true;

    private CacheConfig effectiveStreamCache = CacheConfig.builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @Nonnull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonProperty(LOCAL_DIR_PROP_NAME)
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
            "bundled with Stroom will be extracted to 'localDir'. This property can be used if you already have LMDB " +
            "installed or want to make use of a package manager provided instance. If you set this property care " +
            "needs  to be taken over version compatibility between the version of LMDBJava (that Stroom uses to " +
            "interact with LMDB) and the version of the LMDB binary.")
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

    @Min(2)
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The number of lock stripes used for preventing multiple pipeline processes " +
            "from loading the same reference stream at the same time. Values should be a power of 2. " +
            "Lower values will mean it is more likely for two different streams from blocking one another.")
    public int getLoadingLockStripes() {
        return loadingLockStripes;
    }

    public void setLoadingLockStripes(final int loadingLockStripes) {
        this.loadingLockStripes = loadingLockStripes;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("If true, then a process writing to the reference data store will block all " +
            "other processes from reading from the store. As only one writer is allowed the active writer will " +
            "also block all other writers. If false, then multiple processes can read from the store regardless " +
            "of whether a process is writing to it. Also when false, if there are active readers during a write " +
            "then empty space in " +
            "the store cannot be reclaimed, instead the store will grow. This setting is a trade off between " +
            "performance and store size.")
    public boolean isReaderBlockedByWriter() {
        return isReaderBlockedByWriter;
    }

    public void setReaderBlockedByWriter(final boolean readerBlockedByWriter) {
        isReaderBlockedByWriter = readerBlockedByWriter;
    }

    public CacheConfig getEffectiveStreamCache() {
        return effectiveStreamCache;
    }

    public void setEffectiveStreamCache(final CacheConfig effectiveStreamCache) {
        this.effectiveStreamCache = effectiveStreamCache;
    }

    @Override
    public String toString() {
        return "ReferenceDataConfig{" +
                "localDir='" + localDir + '\'' +
                ", lmdbSystemLibraryPath='" + lmdbSystemLibraryPath + '\'' +
                ", maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", maxReaders=" + maxReaders +
                ", maxStoreSize=" + maxStoreSize +
                ", purgeAge=" + purgeAge +
                ", isReadAheadEnabled=" + isReadAheadEnabled +
                ", loadingLockStripes=" + loadingLockStripes +
                ", isReaderBlockedByWriter=" + isReaderBlockedByWriter +
                ", effectiveStreamCache=" + effectiveStreamCache +
                '}';
    }
}
