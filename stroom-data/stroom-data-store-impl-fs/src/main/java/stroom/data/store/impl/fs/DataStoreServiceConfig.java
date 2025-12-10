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

package stroom.data.store.impl.fs;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;


@JsonPropertyOrder(alphabetic = true)
public class DataStoreServiceConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String PROP_NAME_DELETE_PURGE_AGE = "deletePurgeAge";
    protected static final String PROP_NAME_DELETE_FAILURE_THRESHOLD = "deleteFailureThreshold";

    private final DataStoreServiceDbConfig dbConfig;
    private StroomDuration deletePurgeAge;
    private final int deleteBatchSize;
    private final int deleteFailureThreshold;
    private final int fileSystemCleanBatchSize;
    private final boolean fileSystemCleanDeleteOut;
    // TODO 29/11/2021 AT: Make final
    private StroomDuration fileSystemCleanOldAge;

    public DataStoreServiceConfig() {
        dbConfig = new DataStoreServiceDbConfig();
        deletePurgeAge = StroomDuration.ofDays(7);
        deleteBatchSize = 1000;
        deleteFailureThreshold = 100;
        fileSystemCleanBatchSize = 20;
        fileSystemCleanDeleteOut = false;
        fileSystemCleanOldAge = StroomDuration.ofDays(1);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DataStoreServiceConfig(@JsonProperty("db") final DataStoreServiceDbConfig dbConfig,
                                  @JsonProperty(PROP_NAME_DELETE_PURGE_AGE) final StroomDuration deletePurgeAge,
                                  @JsonProperty("deleteBatchSize") final int deleteBatchSize,
                                  @JsonProperty(PROP_NAME_DELETE_FAILURE_THRESHOLD) final int deleteFailureThreshold,
                                  @JsonProperty("fileSystemCleanBatchSize") final int fileSystemCleanBatchSize,
                                  @JsonProperty("fileSystemCleanDeleteOut") final boolean fileSystemCleanDeleteOut,
                                  @JsonProperty("fileSystemCleanOldAge") final StroomDuration fileSystemCleanOldAge) {
        this.dbConfig = dbConfig;
        this.deletePurgeAge = deletePurgeAge;
        this.deleteBatchSize = deleteBatchSize;
        this.deleteFailureThreshold = deleteFailureThreshold;
        this.fileSystemCleanBatchSize = fileSystemCleanBatchSize;
        this.fileSystemCleanDeleteOut = fileSystemCleanDeleteOut;
        this.fileSystemCleanOldAge = fileSystemCleanOldAge;
    }

    @Override
    @JsonProperty("db")
    public DataStoreServiceDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("How long data records are left logically deleted before they are deleted " +
            "from the database")
    public StroomDuration getDeletePurgeAge() {
        return deletePurgeAge;
    }

    @Min(1)
    @JsonPropertyDescription("How many logically deleted data records we want to try and physically delete in " +
            "a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @Min(0)
    @JsonPropertyDescription("The number of streams to accept file delete failures for before aborting the '"
            + PhysicalDeleteExecutor.TASK_NAME + "' job. This job deletes a stream's files before " +
            "removing its records from the database. Deletion of files may fail due to network connectivity. " +
            "A value of zero means it will abort on the first stream with errors. A value of 100 means it will " +
            "abort on the 101st stream that errors. Due to concurrent processing, the number of streams processed " +
            "may go some way beyond this value.")
    public int getDeleteFailureThreshold() {
        return deleteFailureThreshold;
    }

    @JsonPropertyDescription("Set child jobs to be created by the file system clean sub task")
    public int getFileSystemCleanBatchSize() {
        return fileSystemCleanBatchSize;
    }

    @JsonPropertyDescription("Write a delete out in the root of the volume rather than physically deleting the files")
    public boolean isFileSystemCleanDeleteOut() {
        return fileSystemCleanDeleteOut;
    }

    @JsonPropertyDescription("Duration until a file is deemed old")
    public StroomDuration getFileSystemCleanOldAge() {
        return fileSystemCleanOldAge;
    }

    public DataStoreServiceConfig withDeletePurgeAge(final StroomDuration deletePurgeAge) {
        return new DataStoreServiceConfig(
                dbConfig,
                deletePurgeAge,
                deleteBatchSize,
                deleteFailureThreshold,
                fileSystemCleanBatchSize,
                fileSystemCleanDeleteOut,
                fileSystemCleanOldAge);
    }

    public DataStoreServiceConfig withDeleteBatchSize(final int deleteBatchSize) {
        return new DataStoreServiceConfig(
                dbConfig,
                deletePurgeAge,
                deleteBatchSize,
                deleteFailureThreshold,
                fileSystemCleanBatchSize,
                fileSystemCleanDeleteOut,
                fileSystemCleanOldAge);
    }

    public DataStoreServiceConfig withFileSystemCleanOldAge(final StroomDuration fileSystemCleanOldAge) {
        return new DataStoreServiceConfig(
                dbConfig,
                deletePurgeAge,
                deleteBatchSize,
                deleteFailureThreshold,
                fileSystemCleanBatchSize,
                fileSystemCleanDeleteOut,
                fileSystemCleanOldAge);
    }


    // --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class DataStoreServiceDbConfig extends AbstractDbConfig {

        public DataStoreServiceDbConfig() {
            super();
        }

        @JsonCreator
        public DataStoreServiceDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
