/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.api.MetaService;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.task.api.TaskContext;
import stroom.util.shared.ResultList;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class MockProcessorTaskManager implements ProcessorTaskManager, Clearable {
    private final MetaService metaService;
    private final ProcessorFilterService processorFilterService;

    @Inject
    MockProcessorTaskManager(final MetaService metaService,
                          final ProcessorFilterService processorFilterService) {
        this.metaService = metaService;
        this.processorFilterService = processorFilterService;
    }

    @Override
    public void clear() {
        // NA
    }

    @Override
    public List<ProcessorTask> assignTasks(final String nodeName, final int count) {
        List<ProcessorTask> taskList = Collections.emptyList();
        final ExpressionCriteria criteria = new ExpressionCriteria();
        final ResultList<ProcessorFilter> processorFilters = processorFilterService
                .find(criteria);
        if (processorFilters != null && processorFilters.size() > 0) {
            // Sort by priority.
            processorFilters.sort((o1, o2) -> o2.getPriority() - o1.getPriority());

            // Get tasks for each filter.
            taskList = new ArrayList<>();
            for (final ProcessorFilter filter : processorFilters) {
                final QueryData queryData = filter.getQueryData();

                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
                findMetaCriteria.setExpression(queryData.getExpression());
                final ResultList<Meta> streams = metaService.find(findMetaCriteria);

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
                            streamTask.setStatus(TaskStatus.ASSIGNED);

                            taskList.add(streamTask);
                        }
                    }
                }
            }
        }

        return taskList;
    }

    @Override
    public void createTasks(TaskContext taskContext) {
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void writeQueueStatistics() {

    }

    @Override
    public AtomicLong getNextDeleteMs() {
        return null;
    }

    @Override
    public int getTaskQueueSize() {
        return 0;
    }

    @Override
    public void abandonTasks(final String nodeName, final List<ProcessorTask> tasks) {
        // NA
    }
}
