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

package stroom.pipeline.refdata;

import stroom.lmdb.LmdbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class ReferenceDataStagingLmdbConfig extends AbstractConfig implements LmdbConfig, IsStroomConfig {

    static final String DEFAULT_LOCAL_DIR = "reference_staging_data";
    static final int DEFAULT_MAX_READERS = 5;
    static final ByteSize DEFAULT_MAX_STORE_SIZE = ByteSize.ofGibibytes(10);
    static final boolean DEFAULT_IS_READ_AHEAD_ENABLED = true;
    static final boolean DEFAULT_IS_READER_BLOCKED_BY_WRITER = false;

    private final String localDir;
    private final int maxReaders;
    private final ByteSize maxStoreSize;
    private final boolean isReadAheadEnabled;
    private final boolean isReaderBlockedByWriter;

    public ReferenceDataStagingLmdbConfig() {
        localDir = DEFAULT_LOCAL_DIR;
        maxReaders = DEFAULT_MAX_READERS;
        maxStoreSize = DEFAULT_MAX_STORE_SIZE;
        isReadAheadEnabled = DEFAULT_IS_READ_AHEAD_ENABLED;
        isReaderBlockedByWriter = DEFAULT_IS_READER_BLOCKED_BY_WRITER;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReferenceDataStagingLmdbConfig(
            @JsonProperty("localDir") final String localDir,
            @JsonProperty("maxReaders") final int maxReaders,
            @JsonProperty("maxStoreSize") final ByteSize maxStoreSize,
            @JsonProperty("readAheadEnabled") final boolean isReadAheadEnabled,
            @JsonProperty("readerBlockedByWriter") final boolean isReaderBlockedByWriter) {

        this.localDir = localDir;
        this.maxReaders = maxReaders;
        this.maxStoreSize = maxStoreSize;
        this.isReadAheadEnabled = isReadAheadEnabled;
        this.isReaderBlockedByWriter = isReaderBlockedByWriter;
    }

    @Override
    @NotNull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path to store temporary reference staging data stores in. " +
            "It will contain one sub directory per staging store. " +
            "It MUST be on local disk, NOT network storage, due to use of memory mapped files. " +
            "Use the fastest disk you can, but using a tmpfs file system is not advised. " +
            "A staging store only exists for the life of a reference stream load. " +
            "The directory will be created if it doesn't exist." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getLocalDir() {
        return localDir;
    }

    @Override
    @Min(1)
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The maximum number of concurrent readers/threads that can use the off-heap store.")
    public int getMaxReaders() {
        return maxReaders;
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

    public ReferenceDataStagingLmdbConfig withLocalDir(final String localDir) {
        return new ReferenceDataStagingLmdbConfig(
                localDir, maxReaders, maxStoreSize, isReadAheadEnabled, isReaderBlockedByWriter);
    }

    public ReferenceDataStagingLmdbConfig withMaxStoreSize(final ByteSize maxStoreSize) {
        return new ReferenceDataStagingLmdbConfig(
                localDir, maxReaders, maxStoreSize, isReadAheadEnabled, isReaderBlockedByWriter);
    }

    public ReferenceDataStagingLmdbConfig withReadAheadEnabled(final boolean isReadAheadEnabled) {
        return new ReferenceDataStagingLmdbConfig(
                localDir, maxReaders, maxStoreSize, isReadAheadEnabled, isReaderBlockedByWriter);
    }

    public ReferenceDataStagingLmdbConfig withReaderBlockedByWriter(final boolean isReaderBlockedByWriter) {
        return new ReferenceDataStagingLmdbConfig(
                localDir, maxReaders, maxStoreSize, isReadAheadEnabled, isReaderBlockedByWriter);
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
