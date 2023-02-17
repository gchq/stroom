package stroom.processor.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@SuppressWarnings("unused")
@JsonPropertyOrder(alphabetic = true)
public class ProcessorConfig extends AbstractConfig implements HasDbConfig {

    private final ProcessorDbConfig dbConfig;
    private final boolean assignTasks;
    private final boolean createTasks;
    private final StroomDuration deleteAge;
    // TODO 29/11/2021 AT: Make final
    private boolean fillTaskQueue;
    // TODO 29/11/2021 AT: Make final
    private int queueSize;


    private final int tasksToCreatePerFilter;
    private final int taskCreationThreadCount;
    private final int databaseMultiInsertMaxBatchSize;

    private final CacheConfig processorCache;
    private final CacheConfig processorFilterCache;
    private final CacheConfig processorNodeCache;
    private final CacheConfig processorFeedCache;

    private final StroomDuration disownDeadTasksAfter;

    private final StroomDuration fillTaskQueueFrequency;
    private final StroomDuration createTasksFrequency;

    public ProcessorConfig() {
        dbConfig = new ProcessorDbConfig();
        assignTasks = true;
        createTasks = true;
        deleteAge = StroomDuration.ofDays(1);
        fillTaskQueue = true;
        queueSize = 1000;
        tasksToCreatePerFilter = 1000;
        taskCreationThreadCount = 5;
        databaseMultiInsertMaxBatchSize = 500;

        processorCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofSeconds(10))
                .build();
        processorFilterCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofSeconds(10))
                .build();
        processorNodeCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        processorFeedCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        disownDeadTasksAfter = StroomDuration.ofMinutes(10);
        fillTaskQueueFrequency = StroomDuration.ofSeconds(10);
        createTasksFrequency = StroomDuration.ofSeconds(10);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProcessorConfig(@JsonProperty("db") final ProcessorDbConfig dbConfig,
                           @JsonProperty("assignTasks") final boolean assignTasks,
                           @JsonProperty("createTasks") final boolean createTasks,
                           @JsonProperty("deleteAge") final StroomDuration deleteAge,
                           @JsonProperty("fillTaskQueue") final boolean fillTaskQueue,
                           @JsonProperty("queueSize") final int queueSize,
                           @JsonProperty("tasksToCreatePerFilter") final int tasksToCreatePerFilter,
                           @JsonProperty("taskCreationThreadCount") final int taskCreationThreadCount,
                           @JsonProperty("databaseMultiInsertMaxBatchSize") final int databaseMultiInsertMaxBatchSize,
                           @JsonProperty("processorCache") final CacheConfig processorCache,
                           @JsonProperty("processorFilterCache") final CacheConfig processorFilterCache,
                           @JsonProperty("processorNodeCache") final CacheConfig processorNodeCache,
                           @JsonProperty("processorFeedCache") final CacheConfig processorFeedCache,
                           @JsonProperty("disownDeadTasksAfter") final StroomDuration disownDeadTasksAfter,
                           @JsonProperty("fillTaskQueueFrequency") final StroomDuration fillTaskQueueFrequency,
                           @JsonProperty("createTasksFrequency") final StroomDuration createTasksFrequency) {
        this.dbConfig = dbConfig;
        this.assignTasks = assignTasks;
        this.createTasks = createTasks;
        this.deleteAge = deleteAge;
        this.fillTaskQueue = fillTaskQueue;
        this.queueSize = queueSize;
        this.tasksToCreatePerFilter = tasksToCreatePerFilter;
        this.taskCreationThreadCount = taskCreationThreadCount;
        this.databaseMultiInsertMaxBatchSize = databaseMultiInsertMaxBatchSize;
        this.processorCache = processorCache;
        this.processorFilterCache = processorFilterCache;
        this.processorNodeCache = processorNodeCache;
        this.processorFeedCache = processorFeedCache;
        this.disownDeadTasksAfter = disownDeadTasksAfter;
        this.fillTaskQueueFrequency = fillTaskQueueFrequency;
        this.createTasksFrequency = createTasksFrequency;
    }

    @Override
    @JsonProperty("db")
    public ProcessorDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("Should the master node assign tasks to workers when tasks are requested?")
    public boolean isAssignTasks() {
        return assignTasks;
    }

    @JsonPropertyDescription("Should the master node create new tasks for stream processor filters?")
    public boolean isCreateTasks() {
        return createTasks;
    }

    @JsonPropertyDescription("How long to keep tasks and filters on the database for before deleting them " +
            "(if they are complete). After a duration of 'deleteAge', they will be logically deleted and unavailable " +
            "in the user interface. After a subsequent duration of 'deleteAge' they will be physically deleted. " +
            "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    @JsonPropertyDescription("Should the master node fill the task queue ready for workers to fetch tasks?")
    public boolean isFillTaskQueue() {
        return fillTaskQueue;
    }

    @Deprecated(forRemoval = true) // Awaiting refactor to handle immutable config
    public void setFillTaskQueue(final boolean fillTaskQueue) {
        this.fillTaskQueue = fillTaskQueue;
    }

    @JsonPropertyDescription("Maximum number of tasks to cache ready for processing, in total and per filter.")
    public int getQueueSize() {
        return queueSize;
    }

    @Deprecated(forRemoval = true) // Awaiting refactor to handle immutable config
    public void setQueueSize(final int queueSize) {
        this.queueSize = queueSize;
    }

    @JsonPropertyDescription("How many tasks should we try to create in the DB per filter. If the queue size is " +
            "larger then that figure will be used instead.")
    public int getTasksToCreatePerFilter() {
        return tasksToCreatePerFilter;
    }

    @JsonPropertyDescription("The number of concurrent threads to use for task creation.")
    public int getTaskCreationThreadCount() {
        return taskCreationThreadCount;
    }

    @JsonPropertyDescription("The maximum number of rows to insert in a single multi insert statement, " +
            "e.g. INSERT INTO X VALUES (...), (...), (...)")
    public int getDatabaseMultiInsertMaxBatchSize() {
        return databaseMultiInsertMaxBatchSize;
    }

    public CacheConfig getProcessorCache() {
        return processorCache;
    }

    public CacheConfig getProcessorFilterCache() {
        return processorFilterCache;
    }

    public CacheConfig getProcessorNodeCache() {
        return processorNodeCache;
    }

    public CacheConfig getProcessorFeedCache() {
        return processorFeedCache;
    }

    @JsonPropertyDescription("How long to wait before we remove ownership of tasks from nodes that appear to have died")
    public StroomDuration getDisownDeadTasksAfter() {
        return disownDeadTasksAfter;
    }

    @JsonPropertyDescription("How frequently do we want to try and fill the in memory task queue from the DB.")
    public StroomDuration getFillTaskQueueFrequency() {
        return fillTaskQueueFrequency;
    }

    @JsonPropertyDescription("How frequently do we want to insert new tasks into the DB.")
    public StroomDuration getCreateTasksFrequency() {
        return createTasksFrequency;
    }

    @Override
    public String toString() {
        return "ProcessorConfig{" +
                "dbConfig=" + dbConfig +
                ", assignTasks=" + assignTasks +
                ", createTasks=" + createTasks +
                ", deleteAge=" + deleteAge +
                ", fillTaskQueue=" + fillTaskQueue +
                ", queueSize=" + queueSize +
                ", taskCreationThreadCount=" + taskCreationThreadCount +
                ", databaseMultiInsertMaxBatchSize=" + databaseMultiInsertMaxBatchSize +
                ", processorCache=" + processorCache +
                ", processorFilterCache=" + processorFilterCache +
                ", processorNodeCache=" + processorNodeCache +
                ", processorFeedCache=" + processorFeedCache +
                ", disownDeadTasksAfter=" + disownDeadTasksAfter +
                ", fillTaskQueueFrequency=" + fillTaskQueueFrequency +
                ", createTasksFrequency=" + createTasksFrequency +
                '}';
    }

    @BootStrapConfig
    public static class ProcessorDbConfig extends AbstractDbConfig {

        public ProcessorDbConfig() {
            super();
        }

        @JsonCreator
        public ProcessorDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
