package stroom.data.store.impl.fs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class DataStoreServiceConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private StroomDuration deletePurgeAge = StroomDuration.ofDays(7);
    private int deleteBatchSize = 1000;
    private int fileSystemCleanBatchSize = 20;
    private boolean fileSystemCleanDeleteOut;
    private StroomDuration fileSystemCleanOldAge = StroomDuration.ofDays(1);

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    @SuppressWarnings("unused")
    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @JsonPropertyDescription("How long data records are left logically deleted before it is deleted from the database")
    public StroomDuration getDeletePurgeAge() {
        return deletePurgeAge;
    }

    public void setDeletePurgeAge(final StroomDuration deletePurgeAge) {
        this.deletePurgeAge = deletePurgeAge;
    }

    @JsonPropertyDescription("How many data records we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @SuppressWarnings("unused")
    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @JsonPropertyDescription("Set child jobs to be created by the file system clean sub task")
    public int getFileSystemCleanBatchSize() {
        return fileSystemCleanBatchSize;
    }

    @SuppressWarnings("unused")
    public void setFileSystemCleanBatchSize(final int fileSystemCleanBatchSize) {
        this.fileSystemCleanBatchSize = fileSystemCleanBatchSize;
    }

    @JsonPropertyDescription("Write a delete out in the root of the volume rather than physically deleting the files")
    public boolean isFileSystemCleanDeleteOut() {
        return fileSystemCleanDeleteOut;
    }

    @SuppressWarnings("unused")
    public void setFileSystemCleanDeleteOut(final boolean fileSystemCleanDeleteOut) {
        this.fileSystemCleanDeleteOut = fileSystemCleanDeleteOut;
    }

    @JsonPropertyDescription("Duration until a file is deemed old")
    public StroomDuration getFileSystemCleanOldAge() {
        return fileSystemCleanOldAge;
    }

    public void setFileSystemCleanOldAge(final StroomDuration fileSystemCleanOldAge) {
        this.fileSystemCleanOldAge = fileSystemCleanOldAge;
    }
}
