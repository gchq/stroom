/*
 * Copyright 2016 Crown Copyright
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

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.api.DistributedTaskFactoryDescription;
import stroom.node.api.NodeInfo;
import stroom.processor.api.JobNames;
import stroom.processor.shared.AssignTasksRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.TaskStatus;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@DistributedTaskFactoryDescription(
        jobName = JobNames.DATA_PROCESSOR,
        description = "Job to process data matching processor filters with their associated pipelines")
@Singleton
public class DataProcessorTaskFactory implements DistributedTaskFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataProcessorTaskFactory.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Data Processor#", 1);

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ProcessorTaskResource processorTaskResource;
    private final NodeInfo nodeInfo;
    private final TaskContextFactory taskContextFactory;
    private final ProcessorTaskClaimer processorTaskClaimer;
    private final ProcessorTaskDao processorTaskDao;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private RunnableFactory runnableFactory;

    @Inject
    DataProcessorTaskFactory(final TargetNodeSetFactory targetNodeSetFactory,
                             final ProcessorTaskResource processorTaskResource,
                             final Provider<DataProcessorTaskHandler> dataProcessorTaskHandlerProvider,
                             final NodeInfo nodeInfo,
                             final TaskContextFactory taskContextFactory,
                             final ProcessorTaskClaimer processorTaskClaimer,
                             final ProcessorTaskDao processorTaskDao,
                             final Provider<ProcessorConfig> processorConfigProvider) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.processorTaskResource = processorTaskResource;
        this.nodeInfo = nodeInfo;
        this.runnableFactory = new RunnableFactoryImpl(dataProcessorTaskHandlerProvider);
        this.taskContextFactory = taskContextFactory;
        this.processorTaskClaimer = processorTaskClaimer;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
    }

    @Override
    public List<DistributedTask> fetch(final String nodeName, final int count) {
        if (processorConfigProvider.get().isClaimTasksOnWorker()) {
            return claim(count);
        }
        return assign(nodeName, count);
    }

    /**
     * gh-5699. Find and claim our own tasks. Note what is <em>not</em> here: no master node, no
     * cluster state, and no REST call - so nothing to fail when there is no master, and nothing
     * for a node to queue behind.
     */
    private List<DistributedTask> claim(final int count) {
        try {
            final TaskContext taskContext = taskContextFactory.current();
            taskContext.info(() -> "Claiming processor tasks");
            final List<ProcessorTask> processorTasks = processorTaskClaimer.claimTasks(count);
            taskContext.info(() -> "Claimed " + processorTasks.size() + " new tasks");
            return toDistributedTasks(processorTasks, true);

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    /**
     * Ask the master node's queue for work, the behaviour worker claiming replaces. Kept so that a
     * cluster can be cut back to it if claiming does not keep the nodes fed.
     */
    private List<DistributedTask> assign(final String nodeName, final int count) {
        try {
            if (targetNodeSetFactory.isClusterStateInitialised()) {
                final String masterNode = targetNodeSetFactory.getMasterNode();
                LOGGER.debug("masterNode: {}", masterNode);
                final TaskContext taskContext = taskContextFactory.current();
                taskContext.info(() -> "Processor task resource assign tasks");
                final ProcessorTaskList processorTaskList = processorTaskResource
                        .assignTasks(masterNode, new AssignTasksRequest(taskContext.getTaskId(), nodeName, count));

                taskContext.info(() ->
                        "Received " +
                        processorTaskList.getList().size() +
                        " new tasks");
                return toDistributedTasks(processorTaskList.getList(), false);
            }
        } catch (final RuntimeException | NullClusterStateException | NodeNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private List<DistributedTask> toDistributedTasks(final List<ProcessorTask> processorTasks,
                                                     final boolean alreadyClaimed) {
        return processorTasks
                .stream()
                .map(processorTask -> {
                    final Runnable runnable = runnableFactory.create(processorTask, alreadyClaimed);
                    return (DistributedTask) new DistributedDataProcessorTask(JobNames.DATA_PROCESSOR,
                            runnable,
                            THREAD_POOL,
                            processorTask,
                            alreadyClaimed);
                })
                .collect(Collectors.toList());
    }

    /// Puts back tasks that this node fetched and will not run, by how each was fetched, as the setting may have
    /// changed since.
    ///
    /// @param nodeName This node.
    /// @param tasks    The tasks, as fetched.
    /// @return True if all of them could be put back, or were found to have changed since they were fetched.
    @Override
    public Boolean abandon(final String nodeName, final List<DistributedTask> tasks) {
        final Map<Boolean, List<ProcessorTask>> processorTasks = tasks
                .stream()
                .map(distributedTask -> (DistributedDataProcessorTask) distributedTask)
                .collect(Collectors.partitioningBy(
                        DistributedDataProcessorTask::isAlreadyClaimed,
                        Collectors.mapping(DistributedDataProcessorTask::getProcessorTask, Collectors.toList())));
        final boolean claimedPutBack = abandonClaimed(processorTasks.get(true));
        final boolean queuedPutBack = abandonQueued(nodeName, processorTasks.get(false));
        return claimedPutBack && queuedPutBack;
    }

    private boolean abandonClaimed(final List<ProcessorTask> processorTasks) {
        if (processorTasks.isEmpty()) {
            return true;
        }
        try {
            // We claimed these ourselves, so we put them back ourselves. Without this they
            // would sit in PROCESSING until their lease expired.
            processorTaskClaimer.abandonTasks(processorTasks);
            return true;
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }

    private boolean abandonQueued(final String nodeName, final List<ProcessorTask> processorTasks) {
        if (processorTasks.isEmpty()) {
            return true;
        }
        taskStatusTraceLog.putBackTasks(DataProcessorTaskFactory.class, processorTasks, nodeName);
        try {
            // They are put back here rather than by the master, which may be what has gone. They are still queued,
            // as only a run moves them on, and only those that have not changed since are put back.
            final int count = processorTaskDao.putBackTasks(processorTasks, Set.of(TaskStatus.QUEUED));
            LOGGER.info("Put back {} of {} tasks that were handed to this node and will not be run",
                    count, processorTasks.size());
            return true;
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    private static class DistributedDataProcessorTask extends DistributedTask {

        private final ProcessorTask processorTask;
        // Whether this node claimed the task itself, rather than being handed it by the master's queue.
        private final boolean alreadyClaimed;

        public DistributedDataProcessorTask(final String jobName,
                                            final Runnable runnable,
                                            final ThreadPool threadPool,
                                            final ProcessorTask processorTask,
                                            final boolean alreadyClaimed) {
            super(jobName, runnable, threadPool, String.valueOf(processorTask.getId()));
            this.processorTask = processorTask;
            this.alreadyClaimed = alreadyClaimed;
        }

        public ProcessorTask getProcessorTask() {
            return processorTask;
        }

        /// @return True if this node claimed the task itself, rather than being handed it by the master's queue.
        public boolean isAlreadyClaimed() {
            return alreadyClaimed;
        }
    }

    public void setRunnableFactory(final RunnableFactory runnableFactory) {
        this.runnableFactory = runnableFactory;
    }


    // --------------------------------------------------------------------------------


    public static class RunnableFactoryImpl implements RunnableFactory {

        private final Provider<DataProcessorTaskHandler> dataProcessorTaskHandlerProvider;

        public RunnableFactoryImpl(final Provider<DataProcessorTaskHandler> dataProcessorTaskHandlerProvider) {
            this.dataProcessorTaskHandlerProvider = dataProcessorTaskHandlerProvider;
        }

        @Override
        public Runnable create(final ProcessorTask processorTask, final boolean alreadyClaimed) {
            return () -> {
                final DataProcessorTaskHandler dataProcessorTaskHandler = dataProcessorTaskHandlerProvider.get();
                dataProcessorTaskHandler.exec(processorTask, alreadyClaimed);
            };
        }
    }


    // --------------------------------------------------------------------------------


    public interface RunnableFactory {

        /**
         * @param alreadyClaimed True if this node took the task to PROCESSING when it claimed it,
         *                       so the handler must not transition it again.
         */
        Runnable create(ProcessorTask processorTask, boolean alreadyClaimed);
    }
}
