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

package stroom.streamtask;

import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;
import stroom.node.api.NodeService;
import stroom.node.shared.Node;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.streamtask.shared.TaskStatus;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Singleton
public class MockStreamTaskCreator implements StreamTaskCreator, Clearable {
    private final MetaService metaService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final NodeService nodeService;

    @Inject
    MockStreamTaskCreator(final MetaService metaService,
                          final StreamProcessorFilterService streamProcessorFilterService,
                          final NodeService nodeService) {
        this.metaService = metaService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.nodeService = nodeService;
    }

    @Override
    public void clear() {
        // NA
    }

    @Override
    public List<ProcessorFilterTask> assignStreamTasks(final String nodeName, final int count) {
        final Node node = nodeService.getNode(nodeName);

        List<ProcessorFilterTask> taskList = Collections.emptyList();
        final FindStreamProcessorFilterCriteria criteria = new FindStreamProcessorFilterCriteria();
        final BaseResultList<ProcessorFilter> streamProcessorFilters = streamProcessorFilterService
                .find(criteria);
        if (streamProcessorFilters != null && streamProcessorFilters.size() > 0) {
            // Sort by priority.
            streamProcessorFilters.sort((o1, o2) -> o2.getPriority() - o1.getPriority());

            // Get tasks for each filter.
            taskList = new ArrayList<>();
            for (final ProcessorFilter filter : streamProcessorFilters) {
                final QueryData queryData = filter.getQueryData();

                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
                findMetaCriteria.setExpression(queryData.getExpression());
                final BaseResultList<Meta> streams = metaService.find(findMetaCriteria);

                streams.sort(Comparator.comparing(Meta::getId));

                if (streams.size() > 0) {
                    for (final Meta meta : streams) {
                        if (meta.getId() >= filter.getStreamProcessorFilterTracker().getMinStreamId()) {
                            // Only process streams with an id of 1 or more
                            // greater than this stream in future.
                            filter.getStreamProcessorFilterTracker().setMinStreamId(meta.getId() + 1);

                            final ProcessorFilterTask streamTask = new ProcessorFilterTask();
                            streamTask.setStreamId(meta.getId());
                            streamTask.setStreamProcessorFilter(filter);
                            streamTask.setNode(node);
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
    public int getStreamTaskQueueSize() {
        return 0;
    }

    @Override
    public void abandonStreamTasks(final String nodeName, final List<ProcessorFilterTask> tasks) {
        // NA
    }
}
