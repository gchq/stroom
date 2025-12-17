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

package stroom.query.common.v2;

import stroom.lmdb.LmdbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ResultStoreLmdbConfig extends AbstractConfig implements LmdbConfig, IsStroomConfig {

    static final String DEFAULT_LOCAL_DIR = "search_results";
    static final int DEFAULT_MAX_READERS = 10;
    static final ByteSize DEFAULT_MAX_STORE_SIZE = ByteSize.ofGibibytes(10);
    static final boolean DEFAULT_IS_READ_AHEAD_ENABLED = true;
    // Not something we want to expose to admins. Search breaks if this is set to true.
    static final boolean DEFAULT_IS_READER_BLOCKED_BY_WRITER = false;

    private final String localDir;
    private final int maxReaders;
    private final ByteSize maxStoreSize;
    private final boolean isReadAheadEnabled;


    public ResultStoreLmdbConfig() {
        localDir = DEFAULT_LOCAL_DIR;
        maxReaders = DEFAULT_MAX_READERS;
        maxStoreSize = DEFAULT_MAX_STORE_SIZE;
        isReadAheadEnabled = DEFAULT_IS_READ_AHEAD_ENABLED;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ResultStoreLmdbConfig(@JsonProperty("localDir") final String localDir,
                                 @JsonProperty("maxReaders") final int maxReaders,
                                 @JsonProperty("maxStoreSize") final ByteSize maxStoreSize,
                                 @JsonProperty("readAheadEnabled") final boolean isReadAheadEnabled) {
        this.localDir = localDir;
        this.maxReaders = maxReaders;
        this.maxStoreSize = maxStoreSize;
        this.isReadAheadEnabled = isReadAheadEnabled;
    }

    @Override
    @NotNull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path relative to the home directory to use for storing the data stores. " +
            "It MUST be on local disk, NOT network storage, due to use of memory mapped files. " +
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
    @JsonProperty("readAheadEnabled")
    @JsonPropertyDescription("Read ahead means the OS will pre-fetch additional data from the disk in the " +
            "expectation that it will be used at some point. This generally improves performance as more data is " +
            "available in the page cache. Read ahead is enabled by default. It may be worth disabling it if " +
            "the actively used data is larger than the available RAM, as this will stop it evicting hot " +
            "entries to make space for pre-fetched data.")
    public boolean isReadAheadEnabled() {
        return isReadAheadEnabled;
    }

    @Override
    @JsonIgnore
    public boolean isReaderBlockedByWriter() {
        // Not something we want to expose to admins. Search breaks if this is set to true.
        return DEFAULT_IS_READER_BLOCKED_BY_WRITER;
    }

    @Override
    public String toString() {
        return "ResultStoreLmdbConfig{" +
                "localDir='" + localDir + '\'' +
                ", maxReaders=" + maxReaders +
                ", maxStoreSize=" + maxStoreSize +
                ", isReadAheadEnabled=" + isReadAheadEnabled +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String localDir = DEFAULT_LOCAL_DIR;
        private int maxReaders = DEFAULT_MAX_READERS;
        private ByteSize maxStoreSize = DEFAULT_MAX_STORE_SIZE;
        private boolean isReadAheadEnabled = DEFAULT_IS_READ_AHEAD_ENABLED;

        private Builder() {
        }

        private Builder(final ResultStoreLmdbConfig lmdbConfig) {
            this.localDir = lmdbConfig.localDir;
            this.maxReaders = lmdbConfig.maxReaders;
            this.maxStoreSize = lmdbConfig.maxStoreSize;
            this.isReadAheadEnabled = lmdbConfig.isReadAheadEnabled;
        }

        public Builder localDir(final String localDir) {
            this.localDir = localDir;
            return this;
        }

        public Builder maxReaders(final int maxReaders) {
            this.maxReaders = maxReaders;
            return this;
        }

        public Builder maxStoreSize(final ByteSize maxStoreSize) {
            this.maxStoreSize = maxStoreSize;
            return this;
        }

        public Builder readAheadEnabled(final boolean readAheadEnabled) {
            isReadAheadEnabled = readAheadEnabled;
            return this;
        }

        public ResultStoreLmdbConfig build() {
            return new ResultStoreLmdbConfig(localDir, maxReaders, maxStoreSize, isReadAheadEnabled);
        }
    }
}
