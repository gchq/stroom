package stroom.data.store.impl.fs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.streamtask.BatchDeleteConfig;

import javax.inject.Singleton;

@Singleton
public class DataStoreServiceConfig implements BatchDeleteConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();

    private String deletePurgeAge = "7d";
    private int deleteBatchSize = 1000;
    private int fileSystemCleanBatchSize = 20;
    private boolean fileSystemCleanDeleteOut;
    private String fileSystemCleanOldAge = "1d";

    @JsonProperty("connection")
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonProperty("connectionPool")
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    public void setConnectionPoolConfig(final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionPoolConfig = connectionPoolConfig;
    }

    @JsonPropertyDescription("How long data records are left logically deleted before it is deleted from the database")
    public String getDeletePurgeAge() {
        return deletePurgeAge;
    }

    public void setDeletePurgeAge(final String deletePurgeAge) {
        this.deletePurgeAge = deletePurgeAge;
    }

    @JsonPropertyDescription("How many data records we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @JsonPropertyDescription("Set child jobs to be created by the file system clean sub task")
    public int getFileSystemCleanBatchSize() {
        return fileSystemCleanBatchSize;
    }

    public void setFileSystemCleanBatchSize(final int fileSystemCleanBatchSize) {
        this.fileSystemCleanBatchSize = fileSystemCleanBatchSize;
    }

    @JsonPropertyDescription("Write a delete out in the root of the volume rather than physically deleting the files")
    public boolean isFileSystemCleanDeleteOut() {
        return fileSystemCleanDeleteOut;
    }

    public void setFileSystemCleanDeleteOut(final boolean fileSystemCleanDeleteOut) {
        this.fileSystemCleanDeleteOut = fileSystemCleanDeleteOut;
    }

    @JsonPropertyDescription("Duration until a file is deemed old")
    public String getFileSystemCleanOldAge() {
        return fileSystemCleanOldAge;
    }

    public void setFileSystemCleanOldAge(final String fileSystemCleanOldAge) {
        this.fileSystemCleanOldAge = fileSystemCleanOldAge;
    }
}
