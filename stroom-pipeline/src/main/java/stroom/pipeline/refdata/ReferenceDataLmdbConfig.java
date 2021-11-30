package stroom.pipeline.refdata;

import stroom.lmdb.LmdbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class ReferenceDataLmdbConfig extends AbstractConfig implements LmdbConfig {

    static final String DEFAULT_LOCAL_DIR = "reference_data";
    static final int DEFAULT_MAX_READERS = 150;
    static final ByteSize DEFAULT_MAX_STORE_SIZE = ByteSize.ofGibibytes(50);
    static final boolean DEFAULT_IS_READ_AHEAD_ENABLED = true;
    static final boolean DEFAULT_IS_READER_BLOCKED_BY_WRITER = true;

    private String localDir = DEFAULT_LOCAL_DIR;
    private int maxReaders = DEFAULT_MAX_READERS;
    private ByteSize maxStoreSize = DEFAULT_MAX_STORE_SIZE;
    private boolean isReadAheadEnabled = DEFAULT_IS_READ_AHEAD_ENABLED;
    private boolean isReaderBlockedByWriter = DEFAULT_IS_READER_BLOCKED_BY_WRITER;

    @Override
    @Nonnull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path relative to the home directory to use for storing the reference data store. " +
            "It MUST be on local disk, NOT network storage, due to use of memory mapped files. " +
            "The directory will be created if it doesn't exist." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getLocalDir() {
        return localDir;
    }

    @SuppressWarnings("unused")
    public void setLocalDir(final String localDir) {
        this.localDir = localDir;
    }

    @Override
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

    @Override
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The maximum size for the off heap store. There must be " +
            "available space on the disk to accommodate this size. It can be larger than the amount of available RAM " +
            "and will only be allocated as it is needed. Can be expressed in IEC units (multiples of 1024), " +
            "e.g. 1024, 1024B, 1024bytes, 1KiB, 1KB, 1K, etc.")
    public ByteSize getMaxStoreSize() {
        return maxStoreSize;
    }

    @SuppressWarnings("unused")
    public void setMaxStoreSize(final ByteSize maxStoreSize) {
        this.maxStoreSize = maxStoreSize;
    }

    @Override
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Read ahead means the OS will pre-fetch additional data from the disk in the " +
            "expectation that it will be used at some point. This generally improves performance as more data is " +
            "available in the page cache. Read ahead is enabled by default. It may be worth disabling it if " +
            "the actively used data is larger than the available RAM, as this will stop it evicting hot " +
            "entries to make space for pre-fetched data.")
    public boolean isReadAheadEnabled() {
        return isReadAheadEnabled;
    }

    @SuppressWarnings("unused")
    public void setReadAheadEnabled(final boolean isReadAheadEnabled) {
        this.isReadAheadEnabled = isReadAheadEnabled;
    }

    @Override
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("If true, then a process writing to the data store will block all " +
            "other processes from reading from the store. As only one writer is allowed the active writer will " +
            "also block all other writers. If false, then multiple processes can read from the store regardless " +
            "of whether a process is writing to it. Also when false, if there are active readers during a write " +
            "then empty space in " +
            "the store cannot be reclaimed, instead the store will grow. This setting is a trade off between " +
            "performance and store size.")
    public boolean isReaderBlockedByWriter() {
        return isReaderBlockedByWriter;
    }

    @SuppressWarnings("unused")
    public void setReaderBlockedByWriter(final boolean readerBlockedByWriter) {
        isReaderBlockedByWriter = readerBlockedByWriter;
    }

    @Override
    public String toString() {
        return "ReferenceDataLmdbConfig{" +
                "localDir='" + localDir + '\'' +
                ", maxReaders=" + maxReaders +
                ", maxStoreSize=" + maxStoreSize +
                ", isReadAheadEnabled=" + isReadAheadEnabled +
                ", isReaderBlockedByWriter=" + isReaderBlockedByWriter +
                '}';
    }
}
