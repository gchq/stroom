package stroom.data.store.impl.fs;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DataStoreServiceConfig extends AbstractConfig implements HasDbConfig {

    private final DataStoreServiceDbConfig dbConfig;
    private StroomDuration deletePurgeAge;
    private final int deleteBatchSize;
    private final int fileSystemCleanBatchSize;
    private final boolean fileSystemCleanDeleteOut;
    // TODO 29/11/2021 AT: Make final
    private StroomDuration fileSystemCleanOldAge;

    public DataStoreServiceConfig() {
        dbConfig = new DataStoreServiceDbConfig();
        deletePurgeAge = StroomDuration.ofDays(7);
        deleteBatchSize = 1000;
        fileSystemCleanBatchSize = 20;
        fileSystemCleanDeleteOut = false;
        fileSystemCleanOldAge = StroomDuration.ofDays(1);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DataStoreServiceConfig(@JsonProperty("db") final DataStoreServiceDbConfig dbConfig,
                                  @JsonProperty("deletePurgeAge") final StroomDuration deletePurgeAge,
                                  @JsonProperty("deleteBatchSize") final int deleteBatchSize,
                                  @JsonProperty("fileSystemCleanBatchSize") final int fileSystemCleanBatchSize,
                                  @JsonProperty("fileSystemCleanDeleteOut") final boolean fileSystemCleanDeleteOut,
                                  @JsonProperty("fileSystemCleanOldAge") final StroomDuration fileSystemCleanOldAge) {
        this.dbConfig = dbConfig;
        this.deletePurgeAge = deletePurgeAge;
        this.deleteBatchSize = deleteBatchSize;
        this.fileSystemCleanBatchSize = fileSystemCleanBatchSize;
        this.fileSystemCleanDeleteOut = fileSystemCleanDeleteOut;
        this.fileSystemCleanOldAge = fileSystemCleanOldAge;
    }

    @Override
    @JsonProperty("db")
    public DataStoreServiceDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("How long data records are left logically deleted before it is deleted from the database")
    public StroomDuration getDeletePurgeAge() {
        return deletePurgeAge;
    }

    @Deprecated(forRemoval = true) // Awaiting refactor to handle immutable config
    public void setDeletePurgeAge(final StroomDuration deletePurgeAge) {
        this.deletePurgeAge = deletePurgeAge;
    }

    @JsonPropertyDescription("How many data records we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
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

    @Deprecated(forRemoval = true) // Awaiting refactor to handle immutable config
    public void setFileSystemCleanOldAge(final StroomDuration fileSystemCleanOldAge) {
        this.fileSystemCleanOldAge = fileSystemCleanOldAge;
    }

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
