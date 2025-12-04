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

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.task.shared.TaskId;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Singleton
public class MockProcessorTaskQueueManager implements ProcessorTaskQueueManager {

    private final MetaService metaService;
    private final ProcessorFilterService processorFilterService;

    @Inject
    MockProcessorTaskQueueManager(final MetaService metaService,
                                  final ProcessorFilterService processorFilterService) {
        this.metaService = metaService;
        this.processorFilterService = processorFilterService;
    }

    @Override
    public ProcessorTaskList assignTasks(final TaskId sourceTaskId, final String nodeName, final int count) {
        List<ProcessorTask> taskList = Collections.emptyList();
        final ExpressionCriteria criteria = new ExpressionCriteria();
        final List<ProcessorFilter> processorFilters = processorFilterService
                .find(criteria).getValues();
        if (processorFilters != null && processorFilters.size() > 0) {
            // Sort by priority.
            processorFilters.sort((o1, o2) -> o2.getPriority() - o1.getPriority());

            // Get tasks for each filter.
            taskList = new ArrayList<>();
            for (final ProcessorFilter filter : processorFilters) {
                final QueryData queryData = filter.getQueryData();

                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
                findMetaCriteria.setExpression(queryData.getExpression());
                final List<Meta> streams = metaService.find(findMetaCriteria).getValues();

                streams.sort(Comparator.comparing(Meta::getId));

                if (streams.size() > 0) {
                    for (final Meta meta : streams) {
                        if (meta.getId() >= filter.getProcessorFilterTracker().getMinMetaId()) {
                            // Only process streams with an id of 1 or more
                            // greater than this stream in future.
                            filter.getProcessorFilterTracker().setMinMetaId(meta.getId() + 1);

                            final ProcessorTask streamTask = new ProcessorTask();
                            streamTask.setMetaId(meta.getId());
                            streamTask.setProcessorFilter(filter);
                            streamTask.setNodeName(nodeName);
                            streamTask.setStatus(TaskStatus.PROCESSING);

                            taskList.add(streamTask);
                        }
                    }
                }
            }
        }

        return new ProcessorTaskList(nodeName, taskList);
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void exec() {
    }

    @Override
    public void writeQueueStatistics() {

    }

    @Override
    public int getTaskQueueSize() {
        return 0;
    }

    @Override
    public Boolean abandonTasks(final ProcessorTaskList processorTaskList) {
        return true;
    }
}
