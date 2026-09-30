/*
 * Copyright 2018 Crown Copyright
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

import stroom.config.common.HasDbConfig;
import stroom.processor.impl.db.ProcessorDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@SuppressWarnings("unused")
@JsonPropertyOrder(alphabetic = true)
public class ProcessorConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final boolean DEFAULT_ASSIGN_TASKS = true;
    private static final boolean DEFAULT_FILL_TASK_QUEUE = true;
    private static final int DEFAULT_QUEUE_SIZE = 1000;
    private static final boolean DEFAULT_CLAIM_TASKS_ON_WORKER = false;
    private static final int DEFAULT_TASKS_TO_CREATE = 1000;
    private static final boolean DEFAULT_CREATE_TASKS_BEYOND_PROCESS_LIMIT = true;
    private static final int DEFAULT_TASK_CREATION_THREAD_COUNT = 5;
    private static final int DEFAULT_DATABASE_MULTI_INSERT_MAX_BATCH_SIZE = 500;
    private static final boolean DEFAULT_USE_MAX_META_ID_FROM_PREVIOUS_POLL = true;
    private static final StroomDuration DEFAULT_TASK_AVAILABILITY_INTERVAL = StroomDuration.ofSeconds(5);
    private static final StroomDuration DEFAULT_TASK_LEASE_TIMEOUT = StroomDuration.ofMinutes(10);

    private final ProcessorDbConfig dbConfig;
    private boolean claimTasksOnWorker;
    private final boolean assignTasks;
    private final StroomDuration deleteAge;
    private final boolean fillTaskQueue;
    private final int queueSize;
    private final StroomDuration waitToQueueTasksDuration;


    private final int tasksToCreate;
    private final boolean createTasksBeyondProcessLimit;
    private final int taskCreationThreadCount;
    private final int databaseMultiInsertMaxBatchSize;

    private final CacheConfig processorCache;
    private final CacheConfig processorFilterCache;
    private final CacheConfig processorNodeCache;
    private final CacheConfig processorFeedCache;
    private final CacheConfig processorProfileCache;

    private final StroomDuration taskAvailabilityInterval;
    private final StroomDuration taskLeaseTimeout;

    private StroomDuration skipNonProducingFiltersDuration;
    private StroomDuration skipNonProducingFiltersMaxDuration;
    private StroomDuration skipEmptyFilterFetchDuration;
    private boolean useMaxMetaIdFromPreviousPoll;

    public ProcessorConfig() {
        dbConfig = new ProcessorDbConfig();
        claimTasksOnWorker = DEFAULT_CLAIM_TASKS_ON_WORKER;
        assignTasks = DEFAULT_ASSIGN_TASKS;
        deleteAge = StroomDuration.ofDays(1);
        fillTaskQueue = DEFAULT_FILL_TASK_QUEUE;
        queueSize = DEFAULT_QUEUE_SIZE;
        waitToQueueTasksDuration = StroomDuration.ofSeconds(10);
        tasksToCreate = DEFAULT_TASKS_TO_CREATE;
        createTasksBeyondProcessLimit = DEFAULT_CREATE_TASKS_BEYOND_PROCESS_LIMIT;
        taskCreationThreadCount = DEFAULT_TASK_CREATION_THREAD_COUNT;
        databaseMultiInsertMaxBatchSize = DEFAULT_DATABASE_MULTI_INSERT_MAX_BATCH_SIZE;

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
        processorProfileCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofHours(1))
                .refreshAfterWrite(StroomDuration.ofSeconds(10))
                .build();
        taskAvailabilityInterval = DEFAULT_TASK_AVAILABILITY_INTERVAL;
        taskLeaseTimeout = DEFAULT_TASK_LEASE_TIMEOUT;
        skipNonProducingFiltersDuration = StroomDuration.ofSeconds(10);
        skipNonProducingFiltersMaxDuration = StroomDuration.ofMinutes(1);
        skipEmptyFilterFetchDuration = StroomDuration.ofSeconds(10);
        useMaxMetaIdFromPreviousPoll = DEFAULT_USE_MAX_META_ID_FROM_PREVIOUS_POLL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProcessorConfig(@JsonProperty("db") final ProcessorDbConfig dbConfig,
                           @JsonProperty("claimTasksOnWorker") final Boolean claimTasksOnWorker,
                           @JsonProperty("assignTasks") final Boolean assignTasks,
                           @JsonProperty("deleteAge") final StroomDuration deleteAge,
                           @JsonProperty("fillTaskQueue") final Boolean fillTaskQueue,
                           @JsonProperty("queueSize") final Integer queueSize,
                           @JsonProperty("waitToQueueTasksDuration") final StroomDuration waitToQueueTasksDuration,
                           @JsonProperty("tasksToCreate") final Integer tasksToCreate,
                           @JsonProperty("createTasksBeyondProcessLimit") final Boolean createTasksBeyondProcessLimit,
                           @JsonProperty("taskCreationThreadCount") final Integer taskCreationThreadCount,
                           @JsonProperty("databaseMultiInsertMaxBatchSize")
                               final Integer databaseMultiInsertMaxBatchSize,
                           @JsonProperty("processorCache") final CacheConfig processorCache,
                           @JsonProperty("processorFilterCache") final CacheConfig processorFilterCache,
                           @JsonProperty("processorNodeCache") final CacheConfig processorNodeCache,
                           @JsonProperty("processorFeedCache") final CacheConfig processorFeedCache,
                           @JsonProperty("processorProfileCache") final CacheConfig processorProfileCache,
                           @JsonProperty("taskAvailabilityInterval") final StroomDuration taskAvailabilityInterval,
                           @JsonProperty("taskLeaseTimeout") final StroomDuration taskLeaseTimeout,
                           @JsonProperty("skipNonProducingFiltersDuration") final StroomDuration
                                   skipNonProducingFiltersDuration,
                           @JsonProperty("skipNonProducingFiltersMaxDuration") final StroomDuration
                                   skipNonProducingFiltersMaxDuration,
                           @JsonProperty("skipEmptyFilterFetchDuration") final StroomDuration
                                   skipEmptyFilterFetchDuration,
                           @JsonProperty("useMaxMetaIdFromPreviousPoll")
                               final Boolean useMaxMetaIdFromPreviousPoll) {
        this.dbConfig = dbConfig;
        this.claimTasksOnWorker =
                Objects.requireNonNullElse(claimTasksOnWorker, DEFAULT_CLAIM_TASKS_ON_WORKER);
        this.assignTasks =
                Objects.requireNonNullElse(assignTasks, DEFAULT_ASSIGN_TASKS);
        this.deleteAge = deleteAge;
        this.fillTaskQueue =
                Objects.requireNonNullElse(fillTaskQueue, DEFAULT_FILL_TASK_QUEUE);
        this.queueSize =
                Objects.requireNonNullElse(queueSize, DEFAULT_QUEUE_SIZE);
        this.waitToQueueTasksDuration = waitToQueueTasksDuration;
        this.tasksToCreate =
                Objects.requireNonNullElse(tasksToCreate, DEFAULT_TASKS_TO_CREATE);
        this.createTasksBeyondProcessLimit =
                Objects.requireNonNullElse(createTasksBeyondProcessLimit, DEFAULT_CREATE_TASKS_BEYOND_PROCESS_LIMIT);
        this.taskCreationThreadCount =
                Objects.requireNonNullElse(taskCreationThreadCount, DEFAULT_TASK_CREATION_THREAD_COUNT);
        this.databaseMultiInsertMaxBatchSize =
                Objects.requireNonNullElse(databaseMultiInsertMaxBatchSize,
                        DEFAULT_DATABASE_MULTI_INSERT_MAX_BATCH_SIZE);
        this.processorCache = processorCache;
        this.processorFilterCache = processorFilterCache;
        this.processorNodeCache = processorNodeCache;
        this.processorFeedCache = processorFeedCache;
        this.processorProfileCache = processorProfileCache;
        this.taskAvailabilityInterval = Objects.requireNonNullElse(
                taskAvailabilityInterval, DEFAULT_TASK_AVAILABILITY_INTERVAL);
        this.taskLeaseTimeout = Objects.requireNonNullElse(taskLeaseTimeout, DEFAULT_TASK_LEASE_TIMEOUT);
        this.skipNonProducingFiltersDuration = skipNonProducingFiltersDuration;
        this.skipNonProducingFiltersMaxDuration = skipNonProducingFiltersMaxDuration;
        this.skipEmptyFilterFetchDuration = skipEmptyFilterFetchDuration;
        this.useMaxMetaIdFromPreviousPoll = Objects.requireNonNullElse(
                useMaxMetaIdFromPreviousPoll, DEFAULT_USE_MAX_META_ID_FROM_PREVIOUS_POLL);
    }

    @Override
    @JsonProperty("db")
    public ProcessorDbConfig getDbConfig() {
        return dbConfig;
    }


    @JsonPropertyDescription("EXPERIMENTAL (gh-5699), off by default and not yet proven in production - leave " +
                             "this false unless you have been asked to trial it. Should each worker node find " +
                             "and claim its own processor tasks directly from the database (true), or be fed by " +
                             "the task queue held in memory on the master node (false, the default and the " +
                             "long-standing behaviour)? A worker knows which filters its own processing " +
                             "profiles allow it to run, which the master has to guess at on its behalf, so " +
                             "claiming both removes the master from the processing path and lets a node ask " +
                             "only about work it can actually do. " +
                             "THIS MUST BE THE SAME ON EVERY NODE. The two modes use different task states and " +
                             "neither can see the other's in flight work, so changing it means a hard cutover: " +
                             "stop the whole cluster, change the value everywhere, start it again. Running a " +
                             "mixed cluster is not supported and will leave tasks unprocessed. Turning it back " +
                             "off is the supported way to recover if worker claiming does not keep the cluster " +
                             "fed; tasks left behind by either mode are returned to the created state, so " +
                             "nothing is lost, though tasks in flight when the cluster stopped wait for " +
                             "stroom.processor.taskLeaseTimeout before being picked up again.")
    public boolean isClaimTasksOnWorker() {
        return claimTasksOnWorker;
    }

    /**
     * Test only, in keeping with the other setters here. Production must never flip this at
     * runtime - see the property description for why it is a whole-cluster cutover.
     */
    public void setClaimTasksOnWorker(final boolean claimTasksOnWorker) {
        this.claimTasksOnWorker = claimTasksOnWorker;
    }

    @JsonPropertyDescription("Should the master node assign tasks to workers when tasks are requested? " +
                             "Only used when claimTasksOnWorker is false.")
    public boolean isAssignTasks() {
        return assignTasks;
    }

    @JsonPropertyDescription("How long to keep tasks and filters on the database for before deleting them " +
                             "(if they are complete). After a duration of 'deleteAge', they will be logically " +
                             "deleted and unavailable in the user interface. After a subsequent duration of " +
                             "'deleteAge' they will be physically deleted. " +
                             "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getDeleteAge() {
        return deleteAge;
    }


    @Min(1)

    @Min(1)
    @JsonPropertyDescription("How many tasks should we try to create in the DB ready to be queued. " +
                             "This is applied separately to each processing profile, and to the filters that have " +
                             "no profile, so that a busy profile can't use up a whole task creation run and leave " +
                             "the nodes of another profile with nothing they are allowed to process. The total " +
                             "number of tasks created in a run therefore grows with the number of profiles in use. " +
                             "Note that the number of tasks created may be greater than this number as each task " +
                             "creation thread will " +
                             "try and create the same number of tasks.")
    public int getTasksToCreate() {
        return tasksToCreate;
    }

    @JsonPropertyDescription("Do we want to eagerly create tasks beyond the concurrent processing limit specified by " +
                             "filters? If we don't then those filters will likely run out of queued tasks to process " +
                             "before new tasks " +
                             "are created.")
    public boolean isCreateTasksBeyondProcessLimit() {
        return createTasksBeyondProcessLimit;
    }

    @Min(1)
    @JsonPropertyDescription("The number of concurrent threads to use for task creation.")
    public int getTaskCreationThreadCount() {
        return taskCreationThreadCount;
    }

    @Min(1)
    @JsonPropertyDescription("The maximum number of rows to insert in a single multi insert statement, " +
                             "e.g. INSERT INTO X VALUES (...), (...), (...)")
    public int getDatabaseMultiInsertMaxBatchSize() {
        return databaseMultiInsertMaxBatchSize;
    }

    @JsonPropertyDescription("Should the master node fill the task queue ready for workers to fetch tasks? " +
                             "Only used when claimTasksOnWorker is false.")
    public boolean isFillTaskQueue() {
        return fillTaskQueue;
    }

    @Min(1)
    @JsonPropertyDescription("The number of tasks to attempt to queue from filters considered in priority order. " +
                             "Note that this number will be exceeded if we have currently queued tasks from lower " +
                             "priority filters. This is a cluster wide total shared by all nodes, so it should " +
                             "comfortably exceed the number of tasks all nodes will ask for at once, i.e. the " +
                             "number of nodes multiplied by their Data Processing job task limit. Note also that " +
                             "no further filters of a processing profile are considered once half this number is " +
                             "already queued for that profile. It is applied separately to each processing " +
                             "profile, and to the filters that have no profile, so that a busy profile can't fill " +
                             "the queue and leave the nodes of another profile asking for work that is never " +
                             "queued. The total number of queued tasks therefore grows with the number of " +
                             "profiles in use. Only used when claimTasksOnWorker is false.")
    public int getQueueSize() {
        return queueSize;
    }

    @JsonPropertyDescription("How long should we wait to queue new tasks if we previously managed to queue 0 new " +
                             "tasks. Only used when claimTasksOnWorker is false.")
    public StroomDuration getWaitToQueueTasksDuration() {
        return waitToQueueTasksDuration;
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

    public CacheConfig getProcessorProfileCache() {
        return processorProfileCache;
    }

    @JsonPropertyDescription("gh-5699. How long a node may reuse its summary of which of the processor filters " +
                             "it is allowed to process have tasks waiting, before taking a fresh one. Each node " +
                             "takes this summary for itself in one query, instead of asking about each filter " +
                             "in turn, so this is the main cost of looking for work on an idle cluster. Raising " +
                             "it lowers that cost but delays a node noticing that an idle filter has gained " +
                             "work by up to this long. Set to zero to take a fresh summary every time work is " +
                             "looked for.")
    public StroomDuration getTaskAvailabilityInterval() {
        return taskAvailabilityInterval;
    }

    @JsonPropertyDescription("gh-5699. How long a processing task's heartbeat (its status time) may go " +
                             "un-renewed before the task is considered dead: the reaper returns such tasks to " +
                             "CREATED for another node to pick up, and a node that cannot renew for this long " +
                             "terminates its own in-flight tasks to prevent duplicate output. Must be " +
                             "comfortably longer than the 'Processor Task Heartbeat' job frequency; a 10:1 " +
                             "ratio gives ample slack for a GC pause or database blip.")
    public StroomDuration getTaskLeaseTimeout() {
        return taskLeaseTimeout;
    }


    @JsonPropertyDescription("How long should we wait before retrying task creation for previously non producing " +
                             "filters. This is also the amount by which the wait increases after each successive " +
                             "poll that creates no tasks, up to skipNonProducingFiltersMaxDuration.")
    public StroomDuration getSkipNonProducingFiltersDuration() {
        return skipNonProducingFiltersDuration;
    }

    public void setSkipNonProducingFiltersDuration(final StroomDuration skipNonProducingFiltersDuration) {
        this.skipNonProducingFiltersDuration = skipNonProducingFiltersDuration;
    }

    @JsonPropertyDescription("The longest we will wait before retrying task creation for a filter that keeps " +
                             "creating no tasks. The wait starts at skipNonProducingFiltersDuration and grows by " +
                             "that amount after each successive non producing poll until it reaches this value. " +
                             "This bounds how long a filter that suddenly receives data will wait for its first " +
                             "task, so raising it reduces the cost of polling idle filters at the expense of " +
                             "latency. Individual filters can override this with their own maximum task creation " +
                             "delay. Set to zero to poll non producing filters on every run.")
    public StroomDuration getSkipNonProducingFiltersMaxDuration() {
        return skipNonProducingFiltersMaxDuration;
    }

    public void setSkipNonProducingFiltersMaxDuration(final StroomDuration skipNonProducingFiltersMaxDuration) {
        this.skipNonProducingFiltersMaxDuration = skipNonProducingFiltersMaxDuration;
    }

    @JsonPropertyDescription("How long to leave a filter alone after looking for created tasks to queue for it " +
                             "and finding none. The queue is filled after every task assignment, and a fill only " +
                             "stops early once every processing profile has enough tasks queued, which a profile " +
                             "with nothing to do never does, so without this every fill would query for every " +
                             "filter that has no work. This bounds how long tasks created for an idle filter wait " +
                             "before being queued. Set to zero to look for tasks for every filter on every fill.")
    public StroomDuration getSkipEmptyFilterFetchDuration() {
        return skipEmptyFilterFetchDuration;
    }

    public void setSkipEmptyFilterFetchDuration(final StroomDuration skipEmptyFilterFetchDuration) {
        this.skipEmptyFilterFetchDuration = skipEmptyFilterFetchDuration;
    }

    @JsonPropertyDescription("Should task creation be bounded by the max meta id seen on the previous poll " +
                             "rather than the current max meta id? The database allocates meta ids at insert " +
                             "time but only makes the rows visible at commit time, so a max id read now may " +
                             "sit above a meta that is still in flight. Using the previous poll's value gives " +
                             "such a meta a full poll interval to become visible before task creation moves " +
                             "past it, at the cost of up to one poll interval of extra latency before a new " +
                             "stream gets a task. Setting this to false bounds task creation with the current " +
                             "max meta id instead, which risks a stream silently never being processed.")
    public boolean isUseMaxMetaIdFromPreviousPoll() {
        return useMaxMetaIdFromPreviousPoll;
    }

    public void setUseMaxMetaIdFromPreviousPoll(final boolean useMaxMetaIdFromPreviousPoll) {
        this.useMaxMetaIdFromPreviousPoll = useMaxMetaIdFromPreviousPoll;
    }

    @Override
    public String toString() {
        return "ProcessorConfig{" +
               "dbConfig=" + dbConfig +
               ", deleteAge=" + deleteAge +
               ", tasksToCreate=" + tasksToCreate +
               ", taskCreationThreadCount=" + taskCreationThreadCount +
               ", databaseMultiInsertMaxBatchSize=" + databaseMultiInsertMaxBatchSize +
               ", processorCache=" + processorCache +
               ", processorFilterCache=" + processorFilterCache +
               ", processorNodeCache=" + processorNodeCache +
               ", processorFeedCache=" + processorFeedCache +
               ", processorProfileCache=" + processorProfileCache +
               ", claimTasksOnWorker=" + claimTasksOnWorker +
               ", assignTasks=" + assignTasks +
               ", fillTaskQueue=" + fillTaskQueue +
               ", queueSize=" + queueSize +
               ", waitToQueueTasksDuration=" + waitToQueueTasksDuration +
               ", taskAvailabilityInterval=" + taskAvailabilityInterval +
               ", taskLeaseTimeout=" + taskLeaseTimeout +
               ", skipNonProducingFiltersDuration=" + skipNonProducingFiltersDuration +
               ", useMaxMetaIdFromPreviousPoll=" + useMaxMetaIdFromPreviousPoll +
               '}';
    }
}
