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

package stroom.processor.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@SuppressWarnings("unused")
@JsonPropertyOrder(alphabetic = true)
public class ProcessorConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final ProcessorDbConfig dbConfig;
    private final boolean assignTasks;
    private final StroomDuration deleteAge;
    private final boolean fillTaskQueue;
    private final int queueSize;


    private final int tasksToCreate;
    private final boolean createTasksBeyondProcessLimit;
    private final int taskCreationThreadCount;
    private final int databaseMultiInsertMaxBatchSize;

    private final CacheConfig processorCache;
    private final CacheConfig processorFilterCache;
    private final CacheConfig processorNodeCache;
    private final CacheConfig processorFeedCache;

    private final StroomDuration disownDeadTasksAfter;

    private final StroomDuration waitToQueueTasksDuration;
    private StroomDuration skipNonProducingFiltersDuration;

    public ProcessorConfig() {
        dbConfig = new ProcessorDbConfig();
        assignTasks = true;
        deleteAge = StroomDuration.ofDays(1);
        fillTaskQueue = true;
        queueSize = 1000;
        tasksToCreate = 1000;
        createTasksBeyondProcessLimit = true;
        taskCreationThreadCount = 5;
        databaseMultiInsertMaxBatchSize = 500;

        processorCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofSeconds(10))
                .build();
        processorFilterCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofHours(1))
                .refreshAfterWrite(StroomDuration.ofSeconds(10))
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
        waitToQueueTasksDuration = StroomDuration.ofSeconds(10);
        skipNonProducingFiltersDuration = StroomDuration.ofSeconds(10);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProcessorConfig(@JsonProperty("db") final ProcessorDbConfig dbConfig,
                           @JsonProperty("assignTasks") final boolean assignTasks,
                           @JsonProperty("deleteAge") final StroomDuration deleteAge,
                           @JsonProperty("fillTaskQueue") final boolean fillTaskQueue,
                           @JsonProperty("queueSize") final int queueSize,
                           @JsonProperty("tasksToCreate") final int tasksToCreate,
                           @JsonProperty("createTasksBeyondProcessLimit") final boolean createTasksBeyondProcessLimit,
                           @JsonProperty("taskCreationThreadCount") final int taskCreationThreadCount,
                           @JsonProperty("databaseMultiInsertMaxBatchSize") final int databaseMultiInsertMaxBatchSize,
                           @JsonProperty("processorCache") final CacheConfig processorCache,
                           @JsonProperty("processorFilterCache") final CacheConfig processorFilterCache,
                           @JsonProperty("processorNodeCache") final CacheConfig processorNodeCache,
                           @JsonProperty("processorFeedCache") final CacheConfig processorFeedCache,
                           @JsonProperty("disownDeadTasksAfter") final StroomDuration disownDeadTasksAfter,
                           @JsonProperty("waitToQueueTasksDuration") final StroomDuration waitToQueueTasksDuration,
                           @JsonProperty("skipNonProducingFiltersDuration") final StroomDuration
                                   skipNonProducingFiltersDuration) {
        this.dbConfig = dbConfig;
        this.assignTasks = assignTasks;
        this.deleteAge = deleteAge;
        this.fillTaskQueue = fillTaskQueue;
        this.queueSize = queueSize;
        this.tasksToCreate = tasksToCreate;
        this.createTasksBeyondProcessLimit = createTasksBeyondProcessLimit;
        this.taskCreationThreadCount = taskCreationThreadCount;
        this.databaseMultiInsertMaxBatchSize = databaseMultiInsertMaxBatchSize;
        this.processorCache = processorCache;
        this.processorFilterCache = processorFilterCache;
        this.processorNodeCache = processorNodeCache;
        this.processorFeedCache = processorFeedCache;
        this.disownDeadTasksAfter = disownDeadTasksAfter;
        this.waitToQueueTasksDuration = waitToQueueTasksDuration;
        this.skipNonProducingFiltersDuration = skipNonProducingFiltersDuration;
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

    @JsonPropertyDescription("The number of tasks to attempt to queue from filters considered in priority order. " +
            "Note that this number will be exceeded if we have currently queued tasks from lower priority filters.")
    public int getQueueSize() {
        return queueSize;
    }

    @JsonPropertyDescription("How many tasks should we try to create in the DB ready to be queued." +
            "Note that the number of tasks created may be greater than this number as each task creation thread will " +
            "try and create the same number of tasks.")
    public int getTasksToCreate() {
        return tasksToCreate;
    }

    @JsonPropertyDescription("Do we want to eagerly create tasks beyond the concurrent processing limit specified by " +
            "filters? If we don't then those filters will likely run out of queued tasks to process before new tasks " +
            "are created.")
    public boolean isCreateTasksBeyondProcessLimit() {
        return createTasksBeyondProcessLimit;
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

    @JsonPropertyDescription("How long should we wait to queue new tasks if we previously managed to queue 0 new " +
            "tasks.")
    public StroomDuration getWaitToQueueTasksDuration() {
        return waitToQueueTasksDuration;
    }

    @JsonPropertyDescription("How long should we wait before retrying task creation for previously non producing " +
            "filters.")
    public StroomDuration getSkipNonProducingFiltersDuration() {
        return skipNonProducingFiltersDuration;
    }

    public void setSkipNonProducingFiltersDuration(final StroomDuration skipNonProducingFiltersDuration) {
        this.skipNonProducingFiltersDuration = skipNonProducingFiltersDuration;
    }

    @Override
    public String toString() {
        return "ProcessorConfig{" +
                "dbConfig=" + dbConfig +
                ", assignTasks=" + assignTasks +
                ", deleteAge=" + deleteAge +
                ", fillTaskQueue=" + fillTaskQueue +
                ", queueSize=" + queueSize +
                ", tasksToCreate=" + tasksToCreate +
                ", taskCreationThreadCount=" + taskCreationThreadCount +
                ", databaseMultiInsertMaxBatchSize=" + databaseMultiInsertMaxBatchSize +
                ", processorCache=" + processorCache +
                ", processorFilterCache=" + processorFilterCache +
                ", processorNodeCache=" + processorNodeCache +
                ", processorFeedCache=" + processorFeedCache +
                ", disownDeadTasksAfter=" + disownDeadTasksAfter +
                ", waitToQueueTasksDuration=" + waitToQueueTasksDuration +
                ", skipNonProducingFiltersDuration=" + skipNonProducingFiltersDuration +
                '}';
    }

    @BootStrapConfig
    public static class ProcessorDbConfig extends AbstractDbConfig implements IsStroomConfig {

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
