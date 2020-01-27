package stroom.processor.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@SuppressWarnings("unused")
@Singleton
public class ProcessorConfig extends AbstractConfig implements HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private boolean assignTasks = true;
    private boolean createTasks = true;
    private StroomDuration deleteAge = StroomDuration.ofDays(1);
    private boolean fillTaskQueue = true;
    private int queueSize = 1000;
    private int databaseMultiInsertMaxBatchSize = 500;

    private CacheConfig processorCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofSeconds(10))
            .build();
    private CacheConfig processorFilterCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofSeconds(10))
            .build();
    private CacheConfig processorNodeCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();
    private CacheConfig processorFeedCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @JsonPropertyDescription("Should the master node assign tasks to workers when tasks are requested?")
    public boolean isAssignTasks() {
        return assignTasks;
    }

    public void setAssignTasks(final boolean assignTasks) {
        this.assignTasks = assignTasks;
    }

    @JsonPropertyDescription("Should the master node create new tasks for stream processor filters?")
    public boolean isCreateTasks() {
        return createTasks;
    }

    public void setCreateTasks(final boolean createTasks) {
        this.createTasks = createTasks;
    }

    @JsonPropertyDescription("How long to keep tasks on the database for before deleting them (if they are complete). " +
        "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    public void setDeleteAge(final StroomDuration deleteAge) {
        this.deleteAge = deleteAge;
    }

    @JsonPropertyDescription("Should the master node fill the task queue ready for workers to fetch tasks?")
    public boolean isFillTaskQueue() {
        return fillTaskQueue;
    }

    public void setFillTaskQueue(final boolean fillTaskQueue) {
        this.fillTaskQueue = fillTaskQueue;
    }

    @JsonPropertyDescription("Maximum number of tasks to cache ready for processing per processor filter")
    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(final int queueSize) {
        this.queueSize = queueSize;
    }

    @JsonPropertyDescription("The maximum number of rows to insert in a single multi insert statement, e.g. INSERT INTO X VALUES (...), (...), (...)")
    public int getDatabaseMultiInsertMaxBatchSize() {
        return databaseMultiInsertMaxBatchSize;
    }

    public void setDatabaseMultiInsertMaxBatchSize(final int databaseMultiInsertMaxBatchSize) {
        this.databaseMultiInsertMaxBatchSize = databaseMultiInsertMaxBatchSize;
    }

    public CacheConfig getProcessorCache() {
        return processorCache;
    }

    public void setProcessorCache(final CacheConfig processorCache) {
        this.processorCache = processorCache;
    }

    public CacheConfig getProcessorFilterCache() {
        return processorFilterCache;
    }

    public void setProcessorFilterCache(final CacheConfig processorFilterCache) {
        this.processorFilterCache = processorFilterCache;
    }

    public CacheConfig getProcessorNodeCache() {
        return processorNodeCache;
    }

    public void setProcessorNodeCache(final CacheConfig processorNodeCache) {
        this.processorNodeCache = processorNodeCache;
    }

    public CacheConfig getProcessorFeedCache() {
        return processorFeedCache;
    }

    public void setProcessorFeedCache(final CacheConfig processorFeedCache) {
        this.processorFeedCache = processorFeedCache;
    }

    @Override
    public String toString() {
        return "ProcessorConfig{" +
                "dbConfig=" + dbConfig +
                ", assignTasks=" + assignTasks +
                ", createTasks=" + createTasks +
                ", deleteAge='" + deleteAge + '\'' +
                ", fillTaskQueue=" + fillTaskQueue +
                ", queueSize=" + queueSize +
                ", databaseMultiInsertMaxBatchSize=" + databaseMultiInsertMaxBatchSize +
                ", processorCache=" + processorCache +
                ", processorFilterCache=" + processorFilterCache +
                ", processorNodeCache=" + processorNodeCache +
                ", processorFeedCache=" + processorFeedCache +
                '}';
    }
}
