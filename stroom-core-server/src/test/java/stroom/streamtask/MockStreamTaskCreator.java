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

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.node.shared.Node;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTask;
import stroom.streamtask.shared.TaskStatus;
import stroom.task.TaskContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Singleton
public class MockStreamTaskCreator implements StreamTaskCreator, Clearable {
    private final StreamMetaService streamMetaService;
    private final StreamProcessorFilterService streamProcessorFilterService;

    @Inject
    MockStreamTaskCreator(final StreamMetaService streamMetaService,
                          final StreamProcessorFilterService streamProcessorFilterService) {
        this.streamMetaService = streamMetaService;
        this.streamProcessorFilterService = streamProcessorFilterService;
    }

    @Override
    public void clear() {
        // NA
    }

    @Override
    public List<ProcessorFilterTask> assignStreamTasks(final Node node, final int count) {
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

                final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
                findStreamCriteria.setExpression(queryData.getExpression());
                final BaseResultList<Stream> streams = streamMetaService.find(findStreamCriteria);

                streams.sort(Comparator.comparing(Stream::getId));

                if (streams.size() > 0) {
                    for (final Stream stream : streams) {
                        if (stream.getId() >= filter.getStreamProcessorFilterTracker().getMinStreamId()) {
                            // Only process streams with an id of 1 or more
                            // greater than this stream in future.
                            filter.getStreamProcessorFilterTracker().setMinStreamId(stream.getId() + 1);

                            final ProcessorFilterTask streamTask = new ProcessorFilterTask();
                            streamTask.setStream(stream.getId());
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
    public int getStreamTaskQueueSize() {
        return 0;
    }

    @Override
    public StreamTaskCreatorRecentStreamDetails getStreamTaskCreatorRecentStreamDetails() {
        return null;
    }

    @Override
    public void abandonStreamTasks(final Node node, final List<ProcessorFilterTask> tasks) {
        // NA
    }
}
