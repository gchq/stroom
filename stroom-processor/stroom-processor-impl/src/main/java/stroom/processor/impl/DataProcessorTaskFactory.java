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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import stroom.task.api.SimpleThreadPool;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@DistributedTaskFactoryDescription(jobName = JobNames.DATA_PROCESSOR, description = "Job to process data matching processor filters with their associated pipelines")
public class DataProcessorTaskFactory implements DistributedTaskFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataProcessorTaskFactory.class);
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool("Data Processor#", 1);

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ProcessorTaskResource processorTaskResource;
    private final Provider<DataProcessorTaskHandler> dataProcessorTaskHandlerProvider;
    private final NodeInfo nodeInfo;

    @Inject
    DataProcessorTaskFactory(final TargetNodeSetFactory targetNodeSetFactory,
                             final ProcessorTaskResource processorTaskResource,
                             final Provider<DataProcessorTaskHandler> dataProcessorTaskHandlerProvider,
                             final NodeInfo nodeInfo) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.processorTaskResource = processorTaskResource;
        this.dataProcessorTaskHandlerProvider = dataProcessorTaskHandlerProvider;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public List<DistributedTask> fetch(final String nodeName, final int count) {
        try {
            if (targetNodeSetFactory.isClusterStateInitialised()) {
                final String masterNode = targetNodeSetFactory.getMasterNode();
                final ProcessorTaskList processorTaskList = processorTaskResource
                        .assignTasks(masterNode, new AssignTasksRequest(nodeName, count));

                return processorTaskList
                        .getList()
                        .stream()
                        .map(processorTask -> {
                            final Runnable runnable = () -> {
                                final DataProcessorTaskHandler dataProcessorTaskHandler = dataProcessorTaskHandlerProvider.get();
                                dataProcessorTaskHandler.exec(processorTask);
                            };
                            return new DistributedDataProcessorTask(JobNames.DATA_PROCESSOR, runnable, THREAD_POOL, processorTask);
                        })
                        .collect(Collectors.toList());
            }
        } catch (final RuntimeException | NullClusterStateException | NodeNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    @Override
    public Boolean abandon(final String nodeName, final List<DistributedTask> tasks) {
        try {
            if (targetNodeSetFactory.isClusterStateInitialised()) {
                final String masterNode = targetNodeSetFactory.getMasterNode();

                final List<ProcessorTask> processorTasks = tasks
                        .stream()
                        .map(distributedTask -> (DistributedDataProcessorTask) distributedTask)
                        .map(DistributedDataProcessorTask::getProcessorTask)
                        .collect(Collectors.toList());

                final ProcessorTaskList processorTaskList = new ProcessorTaskList(nodeInfo.getThisNodeName(), processorTasks);

                return processorTaskResource
                        .abandonTasks(masterNode, processorTaskList);
            }
        } catch (final RuntimeException | NullClusterStateException | NodeNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

    private static class DistributedDataProcessorTask extends DistributedTask {
        private final ProcessorTask processorTask;

        public DistributedDataProcessorTask(final String jobName,
                                            final Runnable runnable,
                                            final ThreadPool threadPool,
                                            final ProcessorTask processorTask) {
            super(jobName, runnable, threadPool, String.valueOf(processorTask.getId()));
            this.processorTask = processorTask;
        }

        public ProcessorTask getProcessorTask() {
            return processorTask;
        }
    }
}
